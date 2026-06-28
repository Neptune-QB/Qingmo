"""LLM Provider 可插拔接口

使用 openai 兼容 SDK 接入豆包 Ark / DeepSeek / 其他 LLM。
通过环境变量配置：

豆包：
- DOUBAO_API_KEY / DOUBAO_BASE_URL / DOUBAO_EP_ID

DeepSeek：
- DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DEEPSEEK_MODEL

通用：
- LLM_PROVIDER=deepseek|doubao  选择后端
- LLM_API_KEY / LLM_BASE_URL / LLM_MODEL  通用覆盖
"""

import json
import os
from typing import Optional


class LLMProvider:
    """LLM 调用封装，支持多后端"""

    def __init__(self, provider: Optional[str] = None):
        self.provider = provider or os.getenv("LLM_PROVIDER", "doubao")

        if self.provider == "deepseek":
            self.api_key = os.getenv("DEEPSEEK_API_KEY") or os.getenv("LLM_API_KEY") or ""
            self.base_url = os.getenv("DEEPSEEK_BASE_URL") or os.getenv("LLM_BASE_URL") or "https://api.deepseek.com/v1"
            self.model = os.getenv("DEEPSEEK_MODEL") or os.getenv("LLM_MODEL") or "deepseek-chat"
        else:
            self.api_key = os.getenv("DOUBAO_API_KEY") or os.getenv("LLM_API_KEY") or ""
            self.base_url = os.getenv("DOUBAO_BASE_URL") or os.getenv("LLM_BASE_URL") or "https://ark.cn-beijing.volces.com/api/v3"
            self.model = os.getenv("DOUBAO_EP_ID") or os.getenv("LLM_MODEL") or ""

        self._client = None
        self.timeout = 120.0

    @property
    def is_available(self) -> bool:
        return bool(self.api_key and self.model)

    def _get_client(self):
        from openai import OpenAI
        if self._client is None:
            self._client = OpenAI(api_key=self.api_key, base_url=self.base_url, timeout=self.timeout)
        return self._client

    def chat(self, system_prompt: str, user_prompt: str, temperature: float = 0.3, max_tokens: int = 2048) -> str:
        if not self.is_available:
            raise RuntimeError(f"LLM ({self.provider}) 不可用，请配置对应的 API_KEY")

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
        raw = self.chat(system_prompt, user_prompt, temperature, max_tokens)
        return _parse_json(raw)


def _parse_json(raw: str) -> Optional[dict]:
    if not raw:
        return None
    raw = raw.strip()
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
    for start, end in [("{", "}"), ("[", "]")]:
        si = raw.find(start)
        ei = raw.rfind(end)
        if si != -1 and ei > si:
            try:
                return json.loads(raw[si:ei + 1])
            except json.JSONDecodeError:
                continue
    return None


# ===== ASR 后纠错 Prompt =====
ASR_CORRECTION_SYSTEM_PROMPT = """你是短剧台词校对助手。我会给你一段语音识别的台词列表，其中可能存在同音错字。

请根据上下文修正明显的识别错误，保持原意不变：
1. 同音错字：根据剧情背景和角色名字修正同音别字
2. 人名修正：根据提供的角色名单修正人名识别错误
3. 断句错误：合并或拆分不合理的短句
4. 罕见词幻觉：修正明显不通顺的短语

注意：
- 不要改变剧情内容，只修正语音识别错误
- 不确定的地方保持原样
- 输出严格 JSON 数组，每项包含 original 和 corrected 字段"""


def build_asr_correction_user_prompt(transcripts: list[dict], drama_context: str = "") -> str:
    """构建 ASR 纠错的用户提示，可选注入剧情上下文"""
    lines = "\n".join(
        f"[{t['start_time_ms']//1000}s] {t['text']}"
        for t in transcripts
    )
    ctx = f"\n\n【剧情背景】\n{drama_context}\n" if drama_context else ""
    return f"请修正以下短剧台词的语音识别错误：\n\n{lines}{ctx}\n\n请输出 JSON 数组，每项包含 original 和 corrected 字段。"
