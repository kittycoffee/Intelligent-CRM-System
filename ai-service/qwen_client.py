from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

import httpx


DEFAULT_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
DEFAULT_MODEL = "qwen3.6-plus"
DEFAULT_TIMEOUT_MS = 30000
PROJECT_ROOT = Path(__file__).resolve().parent.parent


class QwenUnavailable(RuntimeError):
    """Raised when Qwen cannot provide a usable structured response."""


def load_project_env() -> None:
    env_path = PROJECT_ROOT / ".env"
    if not env_path.exists():
        return
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip("\"'"))


class QwenClient:
    def __init__(self, http_client: httpx.Client | None = None) -> None:
        load_project_env()
        self.api_key = os.getenv("DASHSCOPE_API_KEY", "").strip()
        self.api_url = os.getenv("QWEN_API_URL", DEFAULT_API_URL).strip()
        self.model = os.getenv("QWEN_MODEL", DEFAULT_MODEL).strip()
        timeout_ms = int(os.getenv("QWEN_API_TIMEOUT_MS", str(DEFAULT_TIMEOUT_MS)))
        self.http_client = http_client or httpx.Client(timeout=timeout_ms / 1000)

    @property
    def available(self) -> bool:
        return bool(self.api_key)

    def generate_json(self, system_prompt: str, user_prompt: str) -> dict[str, Any]:
        if not self.available:
            raise QwenUnavailable("DASHSCOPE_API_KEY is not configured")
        body = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            "response_format": {"type": "json_object"},
            "enable_thinking": False,
            "temperature": 0.2,
        }
        last_error: Exception | None = None
        for _ in range(2):
            try:
                response = self.http_client.post(
                    self.api_url,
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    json=body,
                )
                response.raise_for_status()
                content = response.json()["choices"][0]["message"]["content"]
                result = json.loads(content)
                if not isinstance(result, dict):
                    raise ValueError("Qwen JSON response must be an object")
                return result
            except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError) as exc:
                last_error = exc
        raise QwenUnavailable("Qwen request failed after one retry") from last_error
