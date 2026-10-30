"""FastAPI 入口。HTTP 契约与 Android 端 core/network 的 DTO 严格对齐，
字段名一个都不能改，详见仓库 PLAN.md §6。
"""
import asyncio
import io
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from datetime import datetime

import numpy as np
from fastapi import FastAPI, File, Form, Header, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse, Response
from PIL import Image
from pydantic import BaseModel

from . import assistant, config, engine, pathology, store
from .preprocess import CollageImageError, UnsupportedImageError, canonical_source, prepare

_WORKERS = ThreadPoolExecutor(max_workers=2, thread_name_prefix="clearview-compute")
_WORK_SLOTS = asyncio.Semaphore(2)
_MAX_UPLOAD = 32 * 1024 * 1024


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.model_info = await asyncio.get_running_loop().run_in_executor(_WORKERS, engine.load_models)
    app.state.assistant = assistant.AssistantGateway.from_env()
    yield
    _WORKERS.shutdown(wait=False, cancel_futures=True)


app = FastAPI(title="澄影 ClearView 推理服务", lifespan=lifespan)


@app.exception_handler(store.StoreError)
async def store_error_handler(request: Request, exc: store.StoreError):
    return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})


def _check_cancelled(cancelled):
    if cancelled.is_set():
        raise store.StoreError(499, "CLIENT_DISCONNECTED")


async def _offload(request: Request, operation):
    # Bound queued image work as well as executing work; cancelled jobs retain their
    # slot until the worker actually stops, rather than accumulating behind image compute.
    try:
        await asyncio.wait_for(_WORK_SLOTS.acquire(), timeout=0.2)
    except asyncio.TimeoutError as exc:
        raise HTTPException(503, "COMPUTE_CAPACITY_REACHED") from exc
    cancelled = threading.Event()
    loop = asyncio.get_running_loop()
    try:
        future = loop.run_in_executor(_WORKERS, operation, cancelled)
    except BaseException:
        _WORK_SLOTS.release()
        raise

    def completed(task):
        _WORK_SLOTS.release()
        if not task.cancelled():
            task.exception()  # Consume abandoned worker errors without logging payloads.

    future.add_done_callback(completed)
    try:
        while not future.done():
            await asyncio.wait({future}, timeout=0.1)
            if await request.is_disconnected():
                cancelled.set()
                raise HTTPException(499, "CLIENT_DISCONNECTED")
        return future.result()
    except asyncio.CancelledError:
        cancelled.set()
        raise


class AssistantChatMessage(BaseModel):
    role: str
    content: str


class AssistantChatRequest(BaseModel):
    messages: list[AssistantChatMessage]


def _install(x_install_id: str | None) -> str:
    return (x_install_id or "anonymous").strip() or "anonymous"


def _parse_acquired_at(value):
    if value is None:
        return None
    if not isinstance(value, str) or len(value) > 64 or "T" not in value:
        raise store.StoreError(422, "INVALID_ACQUIRED_AT")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise store.StoreError(422, "INVALID_ACQUIRED_AT") from exc
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise store.StoreError(422, "ACQUIRED_AT_OFFSET_REQUIRED")
    return parsed


def _candidate(label: str, score: float) -> dict:
    return {
        "label": label,
        "label_zh": engine.zh_for(label),
        "disease_name": pathology.en_for(label),
        "disease_name_zh": engine.zh_for(label),
        "score": round(float(score), 6),
    }


def _build_report(
    record_id: str,
    result: engine.AnalysisResult,
    top_k: int,
    inference_ms: int,
    width: int,
    height: int,
    acquisition: dict,
) -> dict:
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    primary = result.primary_index()
    top_indices = result.ranked_indices()[: max(top_k, 1)]
    area = int(result.measurements["lesionAreaPixels"])
    image_ratio = float(result.measurements["lesionImageRatio"])
    mask_url = f"/api/v1/records/{record_id}/mask.png"
    candidates = [_candidate(result.labels[i], result.scores[i]) for i in top_indices]
    primary_label = result.labels[primary]
    primary_zh = engine.zh_for(primary_label)

    return {
        "recordId": record_id,
        "createdAt": now,
        "updatedAt": now,
        "topK": top_k,
        "status": "success",
        "recordUrl": f"/api/v1/records/{record_id}",
        "sourceImageUrl": f"/api/v1/records/{record_id}/source.jpg",
        "maskUrl": mask_url,
        "cropType": config.EXAM_DESCRIPTION,
        "primaryLabel": primary_label,
        "primaryLabelZh": primary_zh,
        "diseaseName": pathology.en_for(primary_label),
        "diseaseNameZh": primary_zh,
        "sourceImagePath": None,
        "maskImagePath": None,
        "lesionImageRatio": image_ratio,
        "severityLevel": None,
        "classifier": {
            "top1": candidates[0] if candidates else None,
            "predictions": candidates,
            "diseaseTop1": candidates[0] if candidates else None,
            "diseasePredictions": candidates,
        },
        "segmentor": {
            "requestId": record_id,
            "model": config.SEG_MODEL_NAME,
            "modelVersion": config.MODEL_VERSION,
            "inputSize": config.SEG_INPUT,
            "maskUrl": mask_url,
            "lesionPixels": area,
            "imageWidth": width,
            "imageHeight": height,
            "imagePixels": width * height,
            "lesionImageRatio": image_ratio,
            "inferenceMs": inference_ms,
            "disclaimer": config.DISCLAIMER,
        },
        "measurements": result.measurements,
        "schemaVersion": 3,
        "acquisition": acquisition,
        "geometry": result.geometry,
        "provenance": engine.provenance(),
        "fullScores": {label: float(result.scores[i]) for i, label in enumerate(result.labels)},
        "attentionTarget": None,
        "attentionState": None,
        "severityUsesCtr": False,
    }


def _mask_png(mask: np.ndarray, rgba=False) -> bytes:
    output = io.BytesIO()
    if rgba:
        pixels = np.zeros((*mask.shape, 4), dtype=np.uint8)
        pixels[mask > 0] = (255, 255, 255, 255)
        Image.fromarray(pixels, mode="RGBA").save(output, format="PNG")
    else:
        Image.fromarray((mask > 0).astype(np.uint8) * 255).save(output, format="PNG")
    return output.getvalue()


def _record_failure(claim, exc):
    # Never log image bytes, names, exception text, prompts or credentials.
    code = exc.detail if isinstance(exc, store.StoreError) else "COMPUTATION_FAILED"
    try:
        store.fail_request(claim, code)
    except Exception:
        # Preserve the original HTTP/cancellation/failure rather than fabricate success.
        # No new result is published by this error path.
        raise store.StoreError(503, "FAILURE_AUDIT_UNAVAILABLE") from exc


def _analyze_work(install, record_id, data, top_k, acquisition, cancelled):
    _check_cancelled(cancelled)
    try:
        source_bytes, source_image = canonical_source(data)
        prepared = prepare(source_image)
    except (CollageImageError, UnsupportedImageError) as exc:
        raise store.StoreError(400, str(exc)) from exc
    content_hash = store.sha256(store.canonical({
        "sourceHash": store.sha256(source_bytes), "topK": top_k,
        "acquisition": acquisition, "provenance": engine.provenance(),
        "cacheVersion": engine.CACHE_VERSION,
    }))
    claim = store.claim_request(install, "analysis", record_id, content_hash, record_id, [record_id])
    if claim.cached:
        return store.cached_analysis(claim)
    try:
        _check_cancelled(cancelled)
        started = time.perf_counter()
        result = engine.analyze(prepared, acquisition)
        _check_cancelled(cancelled)
        report = _build_report(record_id, result, top_k, int((time.perf_counter() - started) * 1000),
                               source_image.width, source_image.height, acquisition)
        files = {
            store.SOURCE_FILE: source_bytes,
            store.MASK_FILE: _mask_png(result.mask, rgba=True),
        }
        report["assets"] = {
            name: {"sha256": store.sha256(files[filename]),
                   "url": f"/api/v1/records/{record_id}/{filename}"}
            for name, filename in store.ASSET_FILES.items()
        }
        audit_id = uuid.uuid4().hex
        report["reportRevision"] = store.revision(report)
        _check_cancelled(cancelled)
        store.computed(claim, {
            "reportRevision": report["reportRevision"], "assets": report["assets"],
            "acquisition": acquisition, "provenance": report["provenance"],
            "resultStatus": report["status"],
        }, audit_id)
        _check_cancelled(cancelled)
        store.publish(claim, {**report, "_sortKey": time.time()}, files)
        return report
    except Exception as exc:
        _record_failure(claim, exc)
        raise


@app.get("/health")
def health():
    info = getattr(app.state, "model_info", None)
    assistant_gateway = getattr(app.state, "assistant", None)
    return {
        "status": "ok",
        "device": engine.device(),
        "modelsLoaded": info is not None,
        "models": info or {},
        "assistant": {
            "configured": bool(
                assistant_gateway and assistant_gateway.config.configured
            ),
            "model": assistant_gateway.config.model if assistant_gateway else None,
        },
    }


@app.post("/api/v1/assistant/chat")
async def assistant_chat(request: AssistantChatRequest):
    gateway = getattr(app.state, "assistant", None)
    if gateway is None:
        raise HTTPException(status_code=503, detail="助手服务尚未初始化，请稍后重试。")
    messages = [
        {"role": item.role, "content": item.content} for item in request.messages
    ]
    try:
        reply = await gateway.chat(messages)
    except assistant.AssistantRequestError as exc:
        raise HTTPException(status_code=exc.status_code, detail=exc.message) from exc
    return {"text": reply.text, "model": reply.model}


@app.post("/api/v1/analyze")
async def analyze(
    request: Request,
    image: UploadFile = File(...),
    top_k: int | None = Form(None, alias="top_k"),
    tta_mode: str | None = Form(None, alias="tta_mode"),
    segmentMode: str | None = Form(None),
    clientRecordId: str | None = Form(None),
    projection: str = Form("UNKNOWN"),
    acquiredAt: str | None = Form(None),
    completenessConfirmed: bool = Form(False),
    x_install_id: str | None = Header(None, alias="X-Install-Id"),
):
    install = store.strict_id(x_install_id if x_install_id is not None else "anonymous", "install")
    record_id = store.strict_id(clientRecordId if clientRecordId is not None else "srv-" + uuid.uuid4().hex, "record")
    top_k = 3 if top_k is None else top_k
    if not 1 <= top_k <= pathology.MAX_CANDIDATES:
        raise HTTPException(422, "TOP_K_OUT_OF_RANGE")
    if projection not in ("PA", "AP", "LATERAL", "UNKNOWN"):
        raise HTTPException(422, "INVALID_PROJECTION")
    acquired = _parse_acquired_at(acquiredAt)
    # 乳腺超声不使用旧版体位/完整度语义；保留字段仅为兼容现有客户端。
    acquisition = {"projection": "UNKNOWN",
                   "acquiredAt": acquired.isoformat() if acquired is not None else None,
                   "completenessConfirmed": False, "declaredBy": "USER"}
    data = await image.read(_MAX_UPLOAD + 1)
    if len(data) > _MAX_UPLOAD:
        raise HTTPException(413, "UPLOAD_TOO_LARGE")
    report = await _offload(request, lambda cancelled: _analyze_work(
        install, record_id, data, top_k, acquisition, cancelled))
    return JSONResponse(report)


@app.get("/api/v1/records")
def list_records(
    limit: int = 20,
    offset: int = 0,
    x_install_id: str | None = Header(None, alias="X-Install-Id"),
):
    install = _install(x_install_id)
    limit = max(min(limit, 100), 1)
    offset = max(offset, 0)
    records, total = store.list_records(install, limit, offset)
    has_more = offset + len(records) < total
    return {
        "records": records,
        "count": len(records),
        "offset": offset,
        "limit": limit,
        "total": total,
        "hasMore": has_more,
        "nextOffset": (offset + len(records)) if has_more else None,
    }


@app.get("/api/v1/records/{record_id}")
def get_record(
    record_id: str,
    x_install_id: str | None = Header(None, alias="X-Install-Id"),
):
    directory = store.record_dir(_install(x_install_id), record_id)
    report = store.read_report(directory)
    if report is None:
        raise HTTPException(status_code=404, detail="记录不存在")
    return JSONResponse(store.public_report(report))


@app.delete("/api/v1/records/{record_id}")
def delete_record(
    record_id: str,
    x_install_id: str | None = Header(None, alias="X-Install-Id"),
):
    if not store.delete_record(_install(x_install_id), record_id):
        raise HTTPException(status_code=404, detail="记录不存在")
    return {"deleted": record_id}


@app.get("/api/v1/records/{record_id}/source.jpg")
def get_source(
    record_id: str,
    x_install_id: str | None = Header(None, alias="X-Install-Id"),
):
    path = store.record_dir(_install(x_install_id), record_id) / store.SOURCE_FILE
    return Response(store.resource_bytes(path), media_type="image/jpeg")


@app.get("/api/v1/records/{record_id}/mask.png")
def get_mask(
    record_id: str,
    x_install_id: str | None = Header(None, alias="X-Install-Id"),
):
    path = store.record_dir(_install(x_install_id), record_id) / store.MASK_FILE
    return Response(store.resource_bytes(path), media_type="image/png")
