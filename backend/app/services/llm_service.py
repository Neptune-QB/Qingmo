from typing import AsyncGenerator, Optional, List, Dict, Any
import asyncio

from volcenginesdkarkruntime import AsyncArk
from app.config import settings


SYSTEM_PROMPT_XIAOMO_DEFAULT = """你是「小墨」，青墨短剧平台的 AI 观剧助手。
你的性格活泼可爱，偶尔卖萌，像一个追剧上头的好朋友。
你陪伴用户一起看短剧，在高光时刻和用户互动，陪用户讨论剧情，帮用户找想看的短剧。
你的说话语气要轻松、有趣，多用表情符号，不要太正式刻板。
如果用户提到具体的短剧、角色名、剧情细节，要给出针对性的回应，不要泛泛而谈。"""

MAX_RETRIES = 3
RETRY_DELAY_BASE = 1.0  # 秒，指数退避基数


class LLMService:
    def __init__(self):
        if settings.DOUBAO_API_KEY:
            self.client = AsyncArk(
                api_key=settings.DOUBAO_API_KEY,
                base_url=settings.DOUBAO_BASE_URL
            )
        else:
            self.client = None

    @property
    def is_available(self) -> bool:
        return self.client is not None

    async def chat(
        self,
        user_message: str,
        history: Optional[List[Dict[str, str]]] = None,
        drama_context: Optional[Dict[str, Any]] = None,
        system_prompt: Optional[str] = None
    ) -> AsyncGenerator[str, None]:
        """
        普通对话，流式返回内容（带重试机制）
        """
        if not self.is_available:
            yield "小墨当前离线，请检查 API Key 配置~"
            return

        messages = []
        prompt = system_prompt or SYSTEM_PROMPT_XIAOMO_DEFAULT

        if drama_context:
            prompt += f"\n\n当前观剧上下文：{drama_context}"

        messages.append({"role": "system", "content": prompt})

        if history:
            for msg in history:
                messages.append(msg)

        # 用户输入用三引号包裹防注入
        messages.append({"role": "user", "content": f'"""{user_message}"""'})

        last_error = None
        for attempt in range(MAX_RETRIES):
            try:
                stream = await asyncio.wait_for(
                    self._create_stream(messages),
                    timeout=30.0
                )
                async for chunk in stream:
                    if chunk.choices and chunk.choices[0].delta.content:
                        yield chunk.choices[0].delta.content
                return
            except asyncio.TimeoutError:
                last_error = "timeout"
                if attempt < MAX_RETRIES - 1:
                    delay = RETRY_DELAY_BASE * (2 ** attempt)
                    await asyncio.sleep(delay)
            except Exception as e:
                last_error = str(e)
                if attempt < MAX_RETRIES - 1:
                    delay = RETRY_DELAY_BASE * (2 ** attempt)
                    await asyncio.sleep(delay)

        yield f"小墨思考太久啦，换个方式试试？（{last_error}）"

    async def _create_stream(self, messages):
        return await self.client.chat.completions.create(
            model=settings.DOUBAO_EP_ID,
            messages=messages,
            stream=True,
            temperature=0.7,
            max_tokens=800
        )

    async def story_extension(
        self,
        drama_title: str,
        drama_desc: str,
        latest_episodes: List[str],
        user_preferences: Optional[List[str]] = None
    ) -> str:
        """
        AI 剧情续写：基于当前短剧生成 200-500 字后续剧情
        """
        if not self.is_available:
            return "小墨离线，无法生成续写内容~"

        context = f"""
短剧名：{drama_title}
剧情简介：{drama_desc}
最近更新集数：{', '.join(latest_episodes)}
"""
        if user_preferences:
            context += f"\n用户偏好：{', '.join(user_preferences)}"

        messages = [
            {
                "role": "system",
                "content": "你是青墨短剧的剧情续写助手。根据已有剧情续写后续发展，200-500字，保持原作风格，有画面感，引人入胜。"
            },
            {
                "role": "user",
                "content": f"请为以下短剧续写后续剧情：\n{context}"
            }
        ]

        resp = await self.client.chat.completions.create(
            model=settings.DOUBAO_EP_ID,
            messages=messages,
            stream=False,
            temperature=0.8,
            max_tokens=600
        )
        return resp.choices[0].message.content or "续写失败，请重试。"

    async def generate_highlights(
        self,
        drama_title: str,
        episode_transcript: str,
        episode_duration: float
    ) -> List[Dict[str, Any]]:
        """
        智能分析剧集内容，自动标注高光点
        返回标准的高光点 JSON 数组
        """
        if not self.is_available:
            return []

        prompt = f"""你是短剧高光点标注专家。
请基于以下剧集内容，自动识别剧情高光点：
- 类型：conflict（冲突）、twist（反转）、sweet（甜蜜）、famous（名场面）、funny（搞笑）
- 每集标注 3-8 个高光点
- 时间点单位是秒，总时长约 {episode_duration} 秒
- 返回严格的 JSON 数组，不要其他说明文字

剧集名：{drama_title}
剧集台词/内容摘要：
{episode_transcript}

输出格式示例：
[
  {{"time": 45.5, "type": "conflict", "title": "男主霸气护妻", "widget_type": "emotion", "emotion_hints": ["爽！", "太解气了"]}}
]
"""

        messages = [
            {"role": "user", "content": prompt}
        ]

        resp = await self.client.chat.completions.create(
            model=settings.DOUBAO_EP_ID,
            messages=messages,
            stream=False,
            temperature=0.3,
            max_tokens=1000
        )

        import json
        try:
            text = resp.choices[0].message.content or "[]"
            start_idx = text.find("[")
            end_idx = text.rfind("]") + 1
            if start_idx != -1 and end_idx > start_idx:
                return json.loads(text[start_idx:end_idx])
            return []
        except Exception:
            return []


llm_service = LLMService()
