"""LLM Provider 可插拔接口

使用 openai 兼容 SDK 接入豆包 Ark / 其他 LLM。
通过环境变量配置：
- LLM_API_KEY / DOUBAO_API_KEY
- LLM_BASE_URL / DOUBAO_BASE_URL
- LLM_MODEL / DOUBAO_EP_ID
"""

import json
import os
from typing import Optional, Dict, Any, Union
from openai import OpenAI


class LLMProvider:
    """LLM 调用封装"""

    def __init__(self):
        self.api_key = os.getenv("DOUBAO_API_KEY") or os.getenv("LLM_API_KEY") or ""
        self.base_url = os.getenv("DOUBAO_BASE_URL") or os.getenv("LLM_BASE_URL") or "https://ark.cn-beijing.volces.com/api/v3"
        self.model = os.getenv("DOUBAO_EP_ID") or os.getenv("LLM_MODEL") or ""
        self._client = None
        self.timeout = 60.0  # 单次 LLM 调用超时

    @property
    def is_available(self) -> bool:
        return bool(self.api_key and self.model)

    def _get_client(self) -> OpenAI:
        if self._client is None:
            self._client = OpenAI(api_key=self.api_key, base_url=self.base_url, timeout=self.timeout)
        return self._client

    def chat(self, system_prompt: str, user_prompt: str, temperature: float = 0.3, max_tokens: int = 2048) -> str:
        """非流式对话，返回完整响应"""
        if not self.is_available:
            raise RuntimeError("LLM 不可用，请配置 DOUBAO_API_KEY 和 DOUBAO_EP_ID 环境变量")

        client = self._get_client()
        resp = client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
            temperature=temperature,
            max_tokens=max_tokens,
        )
        return resp.choices[0].message.content or ""

    def chat_json(self, system_prompt: str, user_prompt: str, temperature: float = 0.3, max_tokens: int = 2048) -> Optional[dict]:
        """对话并解析 JSON，失败时尝试修复一次"""
        raw = self.chat(system_prompt, user_prompt, temperature, max_tokens)
        return _parse_json(raw)


def _parse_json(raw: str) -> Optional[dict]:
    """从 LLM 响应中提取 JSON，失败则尝试修复"""
    if not raw:
        return None
    raw = raw.strip()
    # 移除可能的 Markdown 代码块包裹
    if raw.startswith("```"):
        lines = raw.split("\n")
        if lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].startswith("```"):
            lines = lines[:-1]
        raw = "\n".join(lines)

    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        pass

    # 尝试从文本中提取 JSON 对象或数组
    for start, end in [("{", "}"), ("[", "]")]:
        si = raw.find(start)
        ei = raw.rfind(end)
        if si != -1 and ei > si:
            try:
                return json.loads(raw[si:ei + 1])
            except json.JSONDecodeError:
                continue

    return None
