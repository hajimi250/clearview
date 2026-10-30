"""BUSI 乳腺超声三类分类与 nnU-Net 病灶分割，输出均在源图像素坐标系。"""
import hashlib
import importlib.metadata
import json
import threading

import numpy as np
import torch
import torchvision.transforms as T
from PIL import Image
from transformers import AutoModelForImageClassification

from . import config
from .pathology import CLASSIFIER_LABELS, MALIGNANT_LABELS, PATHOLOGY_ZH
from .preprocess import PREPROCESS_VERSION, PreparedImage

CACHE_VERSION = "busi-siglip-nnunet-v1"
MEASUREMENT_VERSION = "busi-lesion-bbox-v1"
_device = None
_classifier = None
_transform = None
_segmentor = None
_provenance = None
_model_lock = threading.RLock()

LABEL_ALIASES = {
    "benign": "benign", "benign_lesion": "benign",
    "malignant": "malignant", "malignant_lesion": "malignant",
    "normal": "normal", "normal_tissue": "normal",
}


def device() -> str:
    global _device
    if _device is None:
        _device = "cuda" if torch.cuda.is_available() else "cpu"
    return _device


def _sha256(path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def _verify(path, expected, label: str) -> None:
    if not path.is_file():
        raise RuntimeError(f"{label}_MISSING: {path}")
    actual = _sha256(path)
    if expected.strip() and actual != expected.strip().lower():
        raise RuntimeError(f"{label}_HASH_MISMATCH: 实际 {actual}")


def _model_label_order(model) -> list[str]:
    declared = model.config.id2label
    try:
        order = [
            LABEL_ALIASES[str(declared.get(index, declared.get(str(index)))).strip().lower()]
            for index in range(len(declared))
        ]
    except (KeyError, TypeError) as exc:
        raise RuntimeError(f"CLASSIFIER_LABEL_UNKNOWN: {declared}") from exc
    if order != list(CLASSIFIER_LABELS):
        raise RuntimeError(f"CLASSIFIER_LABEL_ORDER_MISMATCH: {declared}")
    return order


def _load_classifier(dev: str):
    weights = config.CLASSIFIER_DIR / "model.safetensors"
    _verify(weights, config.CLASSIFIER_WEIGHTS_SHA256, "CLASSIFIER_CKPT")
    if not (config.CLASSIFIER_DIR / "config.json").is_file():
        raise RuntimeError(f"CLASSIFIER_CONFIG_MISSING: {config.CLASSIFIER_DIR}")
    model = AutoModelForImageClassification.from_pretrained(
        str(config.CLASSIFIER_DIR), local_files_only=True
    ).to(dev).eval()
    _model_label_order(model)
    transform = T.Compose(
        [
            T.Resize((config.CLASSIFIER_INPUT, config.CLASSIFIER_INPUT)),
            T.ToTensor(),
            T.Normalize(list(config.CLASSIFIER_MEAN), list(config.CLASSIFIER_STD)),
        ]
    )
    return transform, model


def _load_segmentor(dev: str):
    checkpoint = (
        config.NNUNET_MODEL_DIR
        / f"fold_{config.NNUNET_FOLD}"
        / config.NNUNET_CHECKPOINT
    )
    _verify(checkpoint, config.NNUNET_CHECKPOINT_SHA256, "SEG_CKPT")
    from nnunetv2.inference.predict_from_raw_data import nnUNetPredictor

    predictor = nnUNetPredictor(
        tile_step_size=0.5,
        use_gaussian=True,
        use_mirroring=True,
        perform_everything_on_device=(dev == "cuda"),
        device=torch.device(dev),
        verbose=False,
        verbose_preprocessing=False,
        allow_tqdm=False,
    )
    predictor.initialize_from_trained_model_folder(
        str(config.NNUNET_MODEL_DIR),
        use_folds=(config.NNUNET_FOLD,),
        checkpoint_name=config.NNUNET_CHECKPOINT,
    )
    return predictor


def load_models() -> dict:
    global _classifier, _transform, _segmentor, _provenance
    with _model_lock:
        dev = device()
        if _classifier is None:
            _transform, _classifier = _load_classifier(dev)
        if _segmentor is None:
            _segmentor = _load_segmentor(dev)
        if _provenance is None:
            libraries = {}
            for name in ("numpy", "Pillow", "torch", "torchvision",
                         "transformers", "safetensors", "nnunetv2"):
                try:
                    libraries[name] = importlib.metadata.version(name)
                except importlib.metadata.PackageNotFoundError:
                    libraries[name] = "unknown"
            checkpoint = (
                config.NNUNET_MODEL_DIR
                / f"fold_{config.NNUNET_FOLD}"
                / config.NNUNET_CHECKPOINT
            )
            _provenance = {
                "modelVersion": config.MODEL_VERSION,
                "classifierHash": _sha256(config.CLASSIFIER_DIR / "model.safetensors"),
                "classifierModel": config.CLASSIFIER_MODEL_NAME,
                "classifierSource": config.CLASSIFIER_MODEL_SOURCE,
                "classifierLicense": config.CLASSIFIER_MODEL_LICENSE,
                "classifierDataset": config.CLASSIFIER_DATASET,
                "classifierMetrics": config.CLASSIFIER_METRICS,
                "segmentorHash": _sha256(checkpoint),
                "segmentorModel": config.SEG_MODEL_NAME,
                "segmentorSource": config.SEG_MODEL_SOURCE,
                "segmentorLicense": config.SEG_MODEL_LICENSE,
                "segmentorDataset": config.SEG_DATASET,
                "segmentorMetrics": config.SEG_METRICS,
                "preprocessVersion": PREPROCESS_VERSION,
                "measurementVersion": MEASUREMENT_VERSION,
                "segmentationInput": config.SEG_INPUT,
                "segmentationPatchSize": list(config.SEG_PATCH_SIZE),
                "segmentationMinAreaRatio": config.SEG_MIN_AREA_RATIO,
                "segmentationMaxAreaRatio": config.SEG_MAX_AREA_RATIO,
                "labels": list(CLASSIFIER_LABELS),
                "malignantLabels": sorted(MALIGNANT_LABELS),
                "labelOrder": list(_model_label_order(_classifier)),
                "libraryVersion": json.dumps(libraries, sort_keys=True, separators=(",", ":")),
            }
        return {
            "device": dev,
            "classifier": config.CLASSIFIER_MODEL_NAME,
            "segmentor": config.SEG_MODEL_NAME,
            "pathologies": list(CLASSIFIER_LABELS),
        }


def provenance() -> dict:
    if _provenance is None:
        raise RuntimeError("MODELS_NOT_INITIALIZED")
    return dict(_provenance)


class AnalysisResult:
    def __init__(self, scores, labels, mask, measurements, geometry):
        self.scores = scores
        self.labels = labels
        self.mask = mask
        self.measurements = measurements
        self.geometry = geometry

    def ranked_indices(self):
        return sorted(range(len(self.scores)), key=lambda index: (-float(self.scores[index]), index))

    def primary_index(self):
        return self.ranked_indices()[0]


def _classify(image: Image.Image):
    tensor = _transform(image.convert("RGB")).unsqueeze(0).to(device())
    with torch.inference_mode():
        probabilities = torch.softmax(
            _classifier(pixel_values=tensor).logits, dim=-1
        )[0].cpu().numpy()
    order = _model_label_order(_classifier)
    scores = np.zeros(len(CLASSIFIER_LABELS), dtype=np.float32)
    for source_index, label in enumerate(order):
        scores[CLASSIFIER_LABELS.index(label)] = float(probabilities[source_index])
    return scores


def _segment(image: Image.Image) -> np.ndarray:
    gray = np.asarray(image.convert("L"), dtype=np.float32)
    segmentation = _segmentor.predict_single_npy_array(
        gray[None, None], {"spacing": (999, 1, 1)}
    )
    predicted = np.squeeze(segmentation) > 0
    if predicted.shape != gray.shape:
        predicted = np.asarray(
            Image.fromarray(predicted.astype(np.uint8) * 255).resize(
                image.size, Image.Resampling.NEAREST
            )
        ) > 0
    return predicted


def _measure(mask: np.ndarray) -> dict:
    height, width = mask.shape
    area = int(mask.sum())
    ratio = area / (width * height)
    points = np.argwhere(mask)
    bbox = (
        [int(points[:, 1].min()), int(points[:, 0].min()),
         int(points[:, 1].max() - points[:, 1].min() + 1),
         int(points[:, 0].max() - points[:, 0].min() + 1)]
        if area else None
    )
    touches_edge = bool(
        mask[:2].any() or mask[-2:].any() or mask[:, :2].any() or mask[:, -2:].any()
    )
    return {
        "lesionAreaPixels": area,
        "lesionImageRatio": round(ratio, 6),
        "lesionBoundingBox": bbox,
        "quality": {
            "maskValid": config.SEG_MIN_AREA_RATIO <= ratio <= config.SEG_MAX_AREA_RATIO,
            "maskTouchesEdge": touches_edge,
        },
        "imageWidth": width,
        "imageHeight": height,
        "imagePixels": width * height,
    }


def analyze(prepared: PreparedImage, acquisition: dict) -> AnalysisResult:
    del acquisition  # 乳腺超声不依赖旧版体位、检查完整度字段。
    with _model_lock:
        image = Image.fromarray(prepared.rgb, mode="RGB")
        scores = _classify(image)
        mask = _segment(image)
        measurements = _measure(mask)
        return AnalysisResult(
            scores, list(CLASSIFIER_LABELS), mask, measurements, prepared.geometry()
        )


def zh_for(label: str) -> str:
    return PATHOLOGY_ZH.get(label, label)
