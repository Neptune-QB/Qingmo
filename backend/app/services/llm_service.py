from typing import AsyncGenerator, Optional, List, Dict, Any
import asyncio
import json

from openai import AsyncOpenAI

from app.config import settings
from app.database import get_connection


SYSTEM_PROMPT_XIAOMO_DEFAULT = """你是「小墨」，青墨短剧平台的 AI 观剧助手。
你的性格活泼可爱，偶尔卖萌，像一个追剧上头的好朋友。
你陪伴用户一起看短剧，在高光时刻和用户互动，陪用户讨论剧情，帮用户找想看的短剧。
你的说话语气要轻松、有趣，多用表情符号，不要太正式刻板。
你推荐短剧的时候，结尾自动追加可点击跳转按钮，格式严格：
👉 「点我立刻看《短剧名》」<qingmo://play?drama_id=X>
如果是指定集数的跳转：
👉 「点我跳转到第N集高光时刻」<qingmo://play?drama_id=X&episode=N>
用户看到这段文字APP会自动渲染成可点击的高亮按钮，点击直接跳转到对应剧集播放页面，不需要用户手动翻找。"""

MAX_RETRIES = 3
RETRY_DELAY_BASE = 1.0  # 秒，指数退避基数


class LLMService:
    def __init__(self):
        if settings.DOUBAO_API_KEY:
            self.client = AsyncOpenAI(
                api_key=settings.DOUBAO_API_KEY,
                base_url=settings.DOUBAO_BASE_URL,
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

        # 自动检索本地真实剧情知识库，100%优先基于本地入库内容返回，零编造
        local_plot = retrieve_plot_context(user_message, drama_context)
        extra = ""
        if isinstance(drama_context, dict):
            extra = drama_context.pop("_rag_context", "")
        full_rag = "\n\n".join(x for x in [local_plot, extra] if x.strip())
        if full_rag:
            messages.append({"role": "system", "content": f"⚠️ 必须完全基于以下本地真实数据库已存储的剧情内容回答用户提问，绝对不允许编造任何不在下面内容里的虚构剧情！\n{full_rag}"})

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
                    timeout=60.0
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
        if self.client is None:
            raise RuntimeError("LLM not available")
        return await self.client.chat.completions.create(
            model=settings.DOUBAO_EP_ID,
            messages=messages,
            stream=True,
            temperature=0.7,
            max_tokens=4096,
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

        async with self.client as client:
            resp = await client.chat.completions.create(
                model=settings.DOUBAO_EP_ID,
                messages=messages,
                stream=False,
                temperature=0.8,
                max_tokens=600,
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
- highlight_type 取值：cliffhanger（悬念钩子）、choice_point（选择节点）、emotional_burst（情绪爆发）、
  power_moment（爽点时刻）、comedy（搞笑瞬间）、suspense（紧张悬念）、
  heartbreak（虐心时刻）、sweet_moment（甜蜜时刻）、reversal（惊天反转）、slapback（打脸爽点）
- 每集标注 3-8 个高光点
- 时间点单位是毫秒，总时长约 {int(episode_duration * 1000)} 毫秒
- 返回严格的 JSON 数组，不要其他说明文字

剧集名：{drama_title}
剧集台词/内容摘要：
{episode_transcript}

输出格式示例：
[
  {{"start_time_ms": 45000, "end_time_ms": 55000, "highlight_type": "emotional_burst", "title": "男主霸气护妻", "interaction_type": "reaction_panel", "interaction_config": {{"emotions": ["爽！", "太解气了"]}}, "xiaomo_gif_code": "emotional_burst"}}
]
"""

        messages = [
            {"role": "user", "content": prompt}
        ]

        async with self.client as client:
            resp = await client.chat.completions.create(
                model=settings.DOUBAO_EP_ID,
                messages=messages,
                stream=False,
                temperature=0.3,
                max_tokens=1000,
            )

        try:
            text = resp.choices[0].message.content or "[]"
            start_idx = text.find("[")
            end_idx = text.rfind("]") + 1
            if start_idx != -1 and end_idx > start_idx:
                return json.loads(text[start_idx:end_idx])
            return []
        except Exception:
            return []


# ===== Agent 意图路由（V1.0 关键词匹配） =====

# 意图分类关键词字典
INTENT_KEYWORDS = {
    "search_drama": [
        "推荐", "找剧", "搜索", "有什么好看的", "甜宠", "古装", "穿越", "悬疑",
        "仙侠", "校园", "年代", "家庭", "探险", "都市", "霸总", "逆袭",
        "帮我找", "有没有好看的", "想看", "介绍短剧", "剧荒",
    ],
    "user_profile": [
        "我看过什么", "看过什么剧", "我的收藏", "观看记录", "播放记录",
        "我最近看了", "看了多少集", "我看了几集", "我看了什么",
    ],
}

SEARCH_PROMPT = """你是一个短剧推荐助手。根据用户的问题和以下可用的短剧列表，用活泼可爱的语气推荐最匹配的短剧。
回复要求：
1. 最多推荐3部
2. 每部格式：「剧名」- 一句话推荐理由
3. 语气要活泼，像朋友推荐
4. 使用表情符号
"""

# 搜索词到数据库标签的映射（用户常用词 → 实际标签）
TAG_SYNONYMS = {
    "古装": "古代",
    "甜宠": "爱情",
    "悬疑": "盗墓",
    "仙侠": "志怪",
    "校园": "剧情",
    "年代": "年代爱情",
    "霸总": "总裁",
    "都市": "都市爱情",
    "冒险": "盗墓",
    "逆袭": "逆袭",
}


def classify_intent(user_message: str) -> str:
    """基于关键词匹配的意图分类"""
    msg_lower = user_message.lower()
    for intent, keywords in INTENT_KEYWORDS.items():
        for kw in keywords:
            if kw in msg_lower:
                return intent
    # 包含剧名/剧情讨论类关键词 → LLM 处理
    return "llm_chat"


def search_dramas(query: str, limit: int = 5) -> list[dict]:
    """
    短剧检索：按标题 + 标签模糊匹配，支持同义词映射
    """
    conn = get_connection()
    cursor = conn.cursor()

    # 提取有效搜索词：先去掉常见口语前缀/后缀，再拆分
    stop_words = ["推荐", "有没有", "有什么", "帮我找", "找", "我想看", "想看", "介绍", "搜索", "剧", "类的", "类型", "的好剧"]
    clean = query
    for sw in stop_words:
        clean = clean.replace(sw, " ")
    keywords_raw = [w.strip() for w in clean.replace("，", ",").replace("、", ",").replace(" ", ",").split(",") if w.strip()]

    # 应用同义词映射：扩展搜索词（支持子串匹配）
    keywords = []
    for kw in keywords_raw:
        keywords.append(kw)
        for syn_key, syn_val in TAG_SYNONYMS.items():
            if syn_key in kw:
                keywords.append(syn_val)

    if not keywords:
        cursor.execute("SELECT id, title, cover_url, total_episodes FROM dramas ORDER BY total_episodes DESC LIMIT ?", (limit,))
    else:
        conditions = []
        params = []
        for kw in keywords:
            conditions.append("(d.title LIKE ? OR d.tags LIKE ?)")
            params.extend([f"%{kw}%", f"%{kw}%"])
        where = " OR ".join(conditions)
        sql = f"""
            SELECT d.id, d.title, d.cover_url, d.total_episodes, d.tags
            FROM dramas d
            WHERE {where}
            ORDER BY d.total_episodes DESC
            LIMIT ?
        """
        params.append(limit)
        cursor.execute(sql, params)

    rows = cursor.fetchall()
    # LIKE 查询无结果时回退到兜底热门推荐，避免"给我推荐几部"这种口语化表达被清成无效关键词后返回空
    if not rows:
        cursor.execute("SELECT id, title, cover_url, total_episodes, tags FROM dramas ORDER BY total_episodes DESC LIMIT ?", (limit,))
        rows = cursor.fetchall()

    result = []
    for r in rows:
        tags_str = r["tags"]
        tags = [t.strip() for t in tags_str.split("/") if t.strip()] if tags_str else []
        result.append({
            "id": r["id"],
            "title": r["title"],
            "cover_url": r["cover_url"],
            "tags": tags,
            "total_episodes": r["total_episodes"],
        })
    conn.close()
    return result


def get_user_profile_summary(user_id: str) -> str:
    """
    为用户画像生成自然语言摘要
    """
    conn = get_connection()
    cursor = conn.cursor()

    # 观看统计
    cursor.execute("SELECT COUNT(*) as cnt FROM user_progress WHERE watched = 1")
    watched_count = cursor.fetchone()["cnt"] or 0

    cursor.execute("""
        SELECT e.drama_id, d.title, COUNT(*) as ep_count
        FROM user_progress up
        JOIN episodes e ON up.episode_id = e.episode_id
        JOIN dramas d ON e.drama_id = d.id
        WHERE up.watched = 1
        GROUP BY e.drama_id
        ORDER BY ep_count DESC
        LIMIT 3
    """)
    top_dramas = [dict(r) for r in cursor.fetchall()]

    conn.close()

    parts = []
    if watched_count > 0:
        parts.append(f"你一共看过 {watched_count} 集短剧")
        if top_dramas:
            names = "、".join(d["title"] for d in top_dramas[:2])
            parts.append(f"最爱看的是《{names}》")
    else:
        parts.append("你还没有看过短剧哦，快去看看吧~")

    return "！".join(parts) + "！" if parts else "小墨还没记住你呢，一起看剧吧~"


llm_service = LLMService()


# ===== RAG 剧情检索 =====
def retrieve_plot_context(user_message: str, drama_context: Optional[dict] = None) -> str:
    """
    从剧情知识库检索与用户问题相关的上下文，注入为 LLM system prompt 附件。
    检索策略：
    1. 从 drama_summaries 中按关键词匹配
    2. 从 drama_characters 中按人名匹配
    3. 从 drama_timeline 中按事件类型/关键词匹配
    4. 兜底返回当前剧集摘要
    """
    conn = get_connection()
    cursor = conn.cursor()
    parts = []
    keywords = [w.strip() for w in user_message.replace("？", " ").replace("?", " ").replace("，", " ").split() if len(w.strip()) >= 2]

    # 确定目标 drama_id
    drama_id = None
    if drama_context:
        drama_id = drama_context.get("drama_id") or drama_context.get("dramaId")

    # 1. 人物检索
    char_conditions = []
    char_params = []
    for kw in keywords:
        char_conditions.append("name LIKE ? OR description LIKE ?")
        char_params.extend([f"%{kw}%", f"%{kw}%"])
    if char_conditions:
        sql = "SELECT name, role, description, relationships FROM drama_characters WHERE " + " OR ".join(char_conditions)
        if drama_id:
            sql += " AND drama_id = ?"
            char_params.append(drama_id)
        sql += " LIMIT 5"
        cursor.execute(sql, char_params)
        chars = cursor.fetchall()
        if chars:
            lines = ["【人物关系】"]
            for ch in chars:
                rels = ch["relationships"]
                try:
                    rels = json.loads(rels) if isinstance(rels, str) else rels
                except Exception:
                    rels = []
                rel_str = "、".join(f"{r.get('to','?')}({r.get('relation','?')})" for r in rels) if rels else "暂无"
                lines.append(f"  {ch['name']}（{ch['role']}）: {ch['description']}；关系：{rel_str}")
            parts.append("\n".join(lines))

    # 2. 时间线/事件检索
    event_conditions = []
    event_params = []
    for kw in keywords:
        event_conditions.append("event_desc LIKE ? OR event_type LIKE ? OR characters LIKE ?")
        event_params.extend([f"%{kw}%", f"%{kw}%", f"%{kw}%"])
    if event_conditions:
        sql = """
            SELECT dt.episode_id, dt.time_sec, dt.event_type, dt.event_desc, dt.characters, e.episode_num
            FROM drama_timeline dt
            JOIN episodes e ON dt.episode_id = e.episode_id
            WHERE """ + " OR ".join(event_conditions)
        if drama_id:
            sql += " AND dt.drama_id = ?"
            event_params.append(drama_id)
        sql += " ORDER BY e.episode_num, dt.time_sec LIMIT 8"
        cursor.execute(sql, event_params)
        events = cursor.fetchall()
        if events:
            lines = ["【关键事件】"]
            for ev in events:
                lines.append(f"  第{ev['episode_num']}集@{ev['time_sec']}s [{ev['event_type']}] {ev['event_desc']}（角色: {ev['characters']}）")
            parts.append("\n".join(lines))

    # 3. 弹幕检索：匹配用户提到的弹幕内容、热议点、观众吐槽
    danmaku_conditions = []
    danmaku_params = []
    for kw in keywords:
        danmaku_conditions.append("text LIKE ?")
        danmaku_params.append(f"%{kw}%")
    if danmaku_conditions:
        sql = """
            SELECT d.text, d.time_sec, e.episode_num
            FROM danmaku d
            JOIN episodes e ON d.episode_id = e.episode_id
            WHERE """ + " OR ".join(danmaku_conditions)
        if drama_id:
            sql += " AND e.drama_id = ?"
            danmaku_params.append(drama_id)
        sql += " ORDER BY d.time_sec LIMIT 10"
        cursor.execute(sql, danmaku_params)
        danmakus = cursor.fetchall()
        if danmakus:
            lines = ["【观众弹幕热议】"]
            for dm in danmakus:
                lines.append(f"  第{dm['episode_num']}集@{dm['time_sec']:.1f}s: {dm['text']}")
            parts.append("\n".join(lines))

    # 4. 高光点检索
    hl_conditions = []
    hl_params = []
    for kw in keywords:
        hl_conditions.append("h.title LIKE ?")
        hl_params.append(f"%{kw}%")
    if hl_conditions:
        sql = """
            SELECT h.start_time_ms, h.title, e.episode_num
            FROM drama_highlight h
            JOIN episodes e ON h.episode_id = e.episode_id
            WHERE """ + " OR ".join(hl_conditions)
        if drama_id:
            sql += " AND e.drama_id = ?"
            hl_params.append(drama_id)
        sql += " ORDER BY e.episode_num, h.start_time_ms LIMIT 10"
        cursor.execute(sql, hl_params)
        hl_rows = cursor.fetchall()
        if hl_rows:
            lines = ["【核心高光点】"]
            for hl in hl_rows:
                lines.append(f"  第{hl['episode_num']}集 @{hl['start_time_ms'] // 1000}秒 → {hl['title']}")
            parts.append("\n".join(lines))

    # 5. 摘要兜底：取当前剧集（或最近几集）的摘要
    if drama_id:
        cursor.execute("""
            SELECT ds.episode_id, ds.summary, e.episode_num
            FROM drama_summaries ds
            JOIN episodes e ON ds.episode_id = e.episode_id
            WHERE ds.drama_id = ?
            ORDER BY e.episode_num
            LIMIT 3
        """, (drama_id,))
        summaries = cursor.fetchall()
        if summaries:
            lines = ["【剧情摘要】"]
            for s in summaries:
                lines.append(f"  第{s['episode_num']}集: {s['summary']}")
            parts.append("\n".join(lines))

    conn.close()
    return "\n\n".join(parts) if parts else ""
