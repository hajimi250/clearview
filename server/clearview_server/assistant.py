"""服务端助手代理。

模型 URL、API Key 与模型名只从服务端环境变量或 server/assistant.env 读取，
不会下发到 Android 客户端。
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import socket
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

from . import config

logger = logging.getLogger(__name__)

DEFAULT_ASSISTANT_URL = (
    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
)
DEFAULT_ASSISTANT_MODEL = "qwen-plus"
DEFAULT_TIMEOUT_SECONDS = 60.0
MAX_RESPONSE_BYTES = 2_000_000

_ALLOWED_ROLES = frozenset({"system", "user", "assistant"})
_MAX_MESSAGES = 40
_MAX_MESSAGE_CHARS = 16_000
_MAX_TOTAL_CHARS = 80_000

_FALLBACK_SYSTEM_PROMPT = (
    "你是乳腺超声影像辅助解读助手。只依据用户提供的文本回答，使用简体中文，分点、简短；"
    "不得编造未提供的分类、病灶范围、病史或治疗信息，不给药物剂量或治疗方案；"
    "直接、具体地回答问题本身，正文中不要添加免责声明或「仅供参考」「请复核」类套话。"
)


class AssistantRequestError(Exception):
    def __init__(self, status_code: int, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.message = message


@dataclass(frozen=True)
class AssistantConfig:
    url: str
    api_key: str = field(repr=False)
    model: str
    timeout_seconds: float

    @property
    def configured(self) -> bool:
        return bool(
            self.url
            and self.api_key
            and self.url.startswith(("http://", "https://"))
        )

    @classmethod
    def from_env(cls) -> "AssistantConfig":
        url = os.environ.get("CLEARVIEW_ASSISTANT_URL", DEFAULT_ASSISTANT_URL).strip()
        api_key = os.environ.get("CLEARVIEW_ASSISTANT_API_KEY", "").strip()
        model = (
            os.environ.get("CLEARVIEW_ASSISTANT_MODEL", DEFAULT_ASSISTANT_MODEL).strip()
            or DEFAULT_ASSISTANT_MODEL
        )
        raw_timeout = os.environ.get("CLEARVIEW_ASSISTANT_TIMEOUT_SECONDS", "").strip()
        try:
            timeout = float(raw_timeout) if raw_timeout else DEFAULT_TIMEOUT_SECONDS
        except ValueError:
            timeout = DEFAULT_TIMEOUT_SECONDS
        timeout = min(max(timeout, 10.0), 180.0)
        return cls(
            url=url,
            api_key=api_key,
            model=model,
            timeout_seconds=timeout,
        )


@dataclass(frozen=True)
class AssistantReply:
    text: str
    model: str


def _load_env_file(path: Path) -> None:
    if not path.exists():
        return
    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
    except OSError as exc:
        logger.warning("无法读取助手配置文件 %s: %s", path.name, exc.__class__.__name__)
        return
    for raw_line in lines:
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        name, value = line.split("=", 1)
        name = name.strip()
        value = value.strip().strip('"').strip("'")
        if name and name not in os.environ:
            os.environ[name] = value


def validate_messages(messages: list[dict[str, str]]) -> list[dict[str, str]]:
    if not messages:
        raise AssistantRequestError(400, "消息不能为空。")
    if len(messages) > _MAX_MESSAGES:
        raise AssistantRequestError(400, "消息数量过多。")
    cleaned: list[dict[str, str]] = []
    total_chars = 0
    for item in messages:
        role = item.get("role", "").strip()
        content = item.get("content", "").strip()
        if role not in _ALLOWED_ROLES:
            raise AssistantRequestError(400, "消息角色无效。")
        if not content:
            continue
        if len(content) > _MAX_MESSAGE_CHARS:
            raise AssistantRequestError(400, "单条消息过长。")
        total_chars += len(content)
        if total_chars > _MAX_TOTAL_CHARS:
            raise AssistantRequestError(400, "消息总长度过长。")
        cleaned.append({"role": role, "content": content})
    if not cleaned:
        raise AssistantRequestError(400, "消息不能为空。")
    if not any(item["role"] == "user" for item in cleaned):
        raise AssistantRequestError(400, "至少需要一条用户消息。")
    if not any(item["role"] == "system" for item in cleaned):
        cleaned.insert(0, {"role": "system", "content": _FALLBACK_SYSTEM_PROMPT})
    return cleaned


class AssistantGateway:
    def __init__(self, config: AssistantConfig):
        self.config = config

    @classmethod
    def from_env(cls) -> "AssistantGateway":
        _load_env_file(config.SERVER_ROOT / "assistant.env")
        return cls(AssistantConfig.from_env())

    async def chat(self, messages: list[dict[str, str]]) -> AssistantReply:
        if not self.config.configured:
            raise AssistantRequestError(503, "助手服务未配置，请联系管理员。")
        prepared = validate_messages(messages)
        return await asyncio.to_thread(self._chat_sync, prepared)

    def _chat_sync(self, messages: list[dict[str, str]]) -> AssistantReply:
        payload = {
            "model": self.config.model,
            "messages": messages,
            "temperature": 0.3,
            "max_tokens": 800,
            "stream": False,
        }
        request = urllib.request.Request(
            self.config.url,
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            headers={
                "Authorization": f"Bearer {self.config.api_key}",
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(
                request, timeout=self.config.timeout_seconds
            ) as response:
                raw = response.read(MAX_RESPONSE_BYTES)
        except urllib.error.HTTPError as exc:
            code = exc.code
            exc.close()
            logger.warning("助手模型服务返回 HTTP %s", code)
            if code in (401, 403):
                raise AssistantRequestError(
                    502, "助手服务鉴权失败，请联系管理员。"
                ) from exc
            if code == 429:
                raise AssistantRequestError(
                    503, "助手服务额度不足或请求过于频繁，请稍后重试。"
                ) from exc
            if 500 <= code <= 599:
                raise AssistantRequestError(
                    502, "助手模型服务暂时不可用，请稍后重试。"
                ) from exc
            raise AssistantRequestError(
                502, "助手模型服务拒绝了请求，请联系管理员。"
            ) from exc
        except (TimeoutError, socket.timeout) as exc:
            raise AssistantRequestError(
                504, "助手模型服务响应超时，请稍后重试。"
            ) from exc
        except urllib.error.URLError as exc:
            reason = exc.reason
            if isinstance(reason, (TimeoutError, socket.timeout)):
                raise AssistantRequestError(
                    504, "助手模型服务响应超时，请稍后重试。"
                ) from exc
            logger.warning("无法连接助手模型服务：%s", type(reason).__name__)
            raise AssistantRequestError(
                502, "无法连接助手模型服务，请检查服务端网络。"
            ) from exc
        except OSError as exc:
            logger.warning("助手模型服务连接失败：%s", exc.__class__.__name__)
            raise AssistantRequestError(
                502, "无法连接助手模型服务，请检查服务端网络。"
            ) from exc

        try:
            data = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            logger.warning("助手模型服务返回了非 JSON 内容")
            raise AssistantRequestError(
                502, "助手模型服务返回了无效内容。"
            ) from exc
        if not isinstance(data, dict):
            raise AssistantRequestError(502, "助手模型服务返回了无效内容。")
        if data.get("error"):
            logger.warning("助手模型服务返回错误对象")
            raise AssistantRequestError(
                502, "助手模型服务返回错误，请稍后重试。"
            )

        text = _extract_text(data)
        if not text:
            raise AssistantRequestError(502, "助手模型未返回内容，请稍后重试。")
        upstream_model = data.get("model")
        if not isinstance(upstream_model, str) or not upstream_model.strip():
            upstream_model = self.config.model
        return AssistantReply(text=text, model=upstream_model.strip())


def _extract_text(data: dict) -> str:
    choices = data.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""
    first = choices[0]
    if not isinstance(first, dict):
        return ""
    message = first.get("message")
    if not isinstance(message, dict):
        return ""
    content = message.get("content")
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts = [
            part.get("text")
            for part in content
            if isinstance(part, dict) and isinstance(part.get("text"), str)
        ]
        return "".join(parts).strip()
    return ""
