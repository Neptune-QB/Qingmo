from typing import AsyncGenerator, Optional, List, Dict, Any
import asyncio
import json

from openai import AsyncOpenAI

from app.config import settings
from app.database import get_connection


SYSTEM_PROMPT_XIAOMO_DEFAULT = """你是「小墨」，青墨短剧平台的 AI 观剧助手。
你的性格活泼可爱，偶尔卖萌，像一个追剧上头的好朋友。
你的说话语气要轻松、有趣，多用表情符号，不要太正式刻板。

## 你的身份：
你叫「小墨」，是青墨短剧平台的官方AI助手。当用户问「你叫什么」「你是谁」等问题时，直接回答「我是小墨呀~ 青墨短剧平台的观剧小助手！」之类的话，不要引用上下文中的弹幕或剧情信息来回答这类身份问题。

## 核心规则（严格遵守）：
1. **绝对不要主动推荐短剧**。只有当用户明确说「推荐」「找剧」「帮我挑」「剧荒」「有什么好看的」「介绍一下」等表达时，才帮用户找剧。
2. **不要编造剧情**。你只了解当前上下文中给出的真实剧情数据，没有上下文的剧情信息绝对不能瞎编。
3. **问答模式**：用户问什么你答什么，不要主动延伸话题到推荐短剧上。
4. **上下文理解**：如果用户说「这部剧」「这集」「刚才说的」等指代词，请结合之前的对话历史找到对应的剧名或集数。比如前一条消息你回答了「北派寻宝笔记」，用户接问「这部剧讲了什么」，你就应该理解为在问《北派寻宝笔记》。
5. **讨论剧情**：如果用户提到具体剧名想讨论剧情，你应该基于上下文中检索到的真实剧情信息来回答。如果上下文中有剧情资料，就结合资料讨论；如果没有资料但是提到了具体剧名，就诚实说你暂时没有这部剧的详细资料，邀请用户去播放页看剧时再讨论。如果用户没有提到任何剧名就问「讨论剧情」，你可以让用户先告诉你想聊哪部剧。
6. 短小精悍：回复控制在3-5句以内，不要长篇大论。"""

SYSTEM_PROMPT_XIAOMO_PLAYER = """你是「小墨」，青墨短剧平台的 AI 观剧助手。
你的性格活泼可爱，偶尔卖萌，像一个一起追剧的好朋友。
说话语气轻松有趣，多用表情符号。

## 你的身份：
你叫「小墨」。当用户问「你叫什么」「你是谁」等问题时，直接回答身份问题，不要引用弹幕或剧情来回答。

## 当前状态：用户正在观看短剧
上下文中有当前剧名、集数、播放进度和人物关系等信息，你需要根据这些真实信息与用户讨论剧情。

## 核心规则：
1. **只能讨论当前正在看的这部短剧**。上下文给出了剧名、集数和剧情资料，你必须严格基于这些真实内容回答，绝对不允许编造。
2. **使用真实角色名**：上下文中的【人物关系】给出了角色的真实名字，你必须用这些名字来称呼角色，不要用「主角」「女主」「男主」「说话人」等泛称代替。如果没有给出名字，可以暂时用角色定位代替（如「鉴宝少年」），但优先使用真实姓名。
3. **有资料就大胆讨论**：只要上下文中有任何关于当前集数的信息（即使只有一句话），就基于它展开讨论。不要因为资料简短就说「还没有更新」或「不知道」——用你有的信息回答，没有详细情节就说「根据资料这集主要讲xxx，具体的细节我也在等更新呢~」。
4. **绝对不要推荐其他短剧**。用户正在看剧，不要打断他的观影体验。
5. 短小精悍：回复控制在3-5句以内。"""

# 推荐专用的提示词（仅在用户明确要求推荐时使用）
SYSTEM_PROMPT_XIAOMO_SEARCH = """你是「小墨」，青墨短剧平台的 AI 观剧助手。
性格活泼可爱，偶尔卖萌。

当前状态：用户正在找剧，你需要根据下方的真实短剧列表帮助用户挑选最合适的。

## 规则：
1. 严格从下方列表中推荐，不要编造不存在的剧名和标签
2. 每部推荐格式：「剧名」- 一句话推荐理由
3. 推荐完自动追加可点击跳转按钮：
👉 「点我立刻看《短剧名》」<qingmo://play?drama_id=X>
4. 最多推荐3部
5. 语气活泼，用表情符号"""

MAX_RETRIES = 3
RETRY_DELAY_BASE = 1.0  # 秒，指数退避基数


class LLMService:
    def __init__(self):
        provider = settings.LLM_PROVIDER
        if provider == "deepseek" and settings.DEEPSEEK_API_KEY:
            self.client = AsyncOpenAI(
                api_key=settings.DEEPSEEK_API_KEY,
                base_url=settings.DEEPSEEK_BASE_URL,
            )
            self.model = settings.DEEPSEEK_MODEL
        elif provider == "doubao" and settings.DOUBAO_API_KEY:
            self.client = AsyncOpenAI(
                api_key=settings.DOUBAO_API_KEY,
                base_url=settings.DOUBAO_BASE_URL,
            )
            self.model = settings.DOUBAO_EP_ID
        else:
            self.client = None
            self.model = None

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
        # 根据上下文选择系统提示词：播放中 → player 模式，否则 → 默认问答案模式
        if drama_context and drama_context.get("drama_title"):
            prompt = system_prompt or SYSTEM_PROMPT_XIAOMO_PLAYER
        else:
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
                stream = await self._create_stream(messages)
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
            model=self.model,
            messages=messages,
            stream=True,
            temperature=0.7,
            max_tokens=4096,
            timeout=60.0,
        )

    async def story_extension(
        self,
        drama_title: str,
        drama_desc: str,
        latest_episodes: Optional[List[str]] = None,
        user_preferences: Optional[List[str]] = None
    ):
        """
        AI 剧情续写：流式输出后续剧情。
        drama_desc 应包含摘要、人物、时间线等完整剧情上下文。
        """
        if not self.is_available:
            yield "小墨离线，无法生成续写内容~"
            return

        messages = [
            {
                "role": "system",
                "content": "你是青墨短剧的剧情续写高手。根据已有剧情信息续写后续发展，保持原作风格和人物性格一致，有画面感，引人入胜。写完整的段落，不要用markdown格式和#号标题。"
            },
            {
                "role": "user",
                "content": f"请为以下短剧续写后续剧情：\n{drama_desc}"
            }
        ]

        try:
            stream = await self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                stream=True,
                temperature=0.8,
                max_tokens=4096,
            )
            async for chunk in stream:
                if chunk.choices and chunk.choices[0].delta.content:
                    yield chunk.choices[0].delta.content
        except Exception:
            yield "续写失败，请重试。"

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
                model=self.model,
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
    "story_extension": [
        "续写", "续写剧情", "写下去",
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
    - 有 drama_id（播放页）：1.人物 2.时间线 3.弹幕 4.高光点 5.摘要
    - 无 drama_id（外层Agent）：只搜剧集摘要（剧名匹配），不搜弹幕/角色等剧集级数据
    """
    conn = get_connection()
    cursor = conn.cursor()
    parts = []
    # 提取关键词：额外拆分「的」「剧情」「讲了什么」等常见后缀
    msg_stripped = user_message
    for ch in "？?，。的剧情讲了什么怎聊讨论说说谁是":
        msg_stripped = msg_stripped.replace(ch, " ")
    keywords = [w.strip() for w in msg_stripped.split() if len(w.strip()) >= 2]

    # 确定目标 drama_id
    drama_id = None
    if drama_context:
        drama_id = drama_context.get("drama_id") or drama_context.get("dramaId")

    # 以下剧集级检索仅在播放页（有 drama_id）时执行
    if drama_id:
        # 1. 人物检索
        char_conditions = []
        char_params = []
        for kw in keywords:
            char_conditions.append("name LIKE ? OR description LIKE ?")
            char_params.extend([f"%{kw}%", f"%{kw}%"])
        if char_conditions:
            sql = "SELECT name, role, description, relationships FROM drama_characters WHERE " + " OR ".join(char_conditions) + " AND drama_id = ? LIMIT 5"
            char_params.append(drama_id)
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
                WHERE dt.drama_id = ? AND (""" + " OR ".join(event_conditions) + """)
                ORDER BY e.episode_num, dt.time_sec LIMIT 8
            """
            cursor.execute(sql, [drama_id] + event_params)
            events = cursor.fetchall()
            if events:
                lines = ["【关键事件】"]
                for ev in events:
                    lines.append(f"  第{ev['episode_num']}集@{ev['time_sec']}s [{ev['event_type']}] {ev['event_desc']}（角色: {ev['characters']}）")
                parts.append("\n".join(lines))

        # 3. 弹幕检索
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
                WHERE e.drama_id = ? AND (""" + " OR ".join(danmaku_conditions) + """)
                ORDER BY d.time_sec LIMIT 10
            """
            cursor.execute(sql, [drama_id] + danmaku_params)
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
                WHERE e.drama_id = ? AND (""" + " OR ".join(hl_conditions) + """)
                ORDER BY e.episode_num, h.start_time_ms LIMIT 10
            """
            cursor.execute(sql, [drama_id] + hl_params)
            hl_rows = cursor.fetchall()
            if hl_rows:
                lines = ["【核心高光点】"]
                for hl in hl_rows:
                    lines.append(f"  第{hl['episode_num']}集 @{hl['start_time_ms'] // 1000}秒 → {hl['title']}")
                parts.append("\n".join(lines))

    # 5. 按剧名检索摘要（用户提到具体剧名时）
    title_conditions = []
    title_params = []
    for kw in keywords:
        title_conditions.append("d.title LIKE ?")
        title_params.append(f"%{kw}%")
    if title_conditions:
        sql = """
            SELECT d.id as drama_id, d.title, ds.episode_id, ds.summary as summary, e.episode_num
            FROM drama_summaries ds
            JOIN dramas d ON ds.drama_id = d.id
            JOIN episodes e ON ds.episode_id = e.episode_id
            WHERE """ + " OR ".join(title_conditions) + """
            UNION ALL
            SELECT d.id as drama_id, d.title, ecs.episode_id, COALESCE(ecs.long_summary, ecs.short_summary) as summary, e.episode_num
            FROM episode_content_summary ecs
            JOIN dramas d ON ecs.drama_id = d.id
            JOIN episodes e ON ecs.episode_id = e.episode_id
            WHERE """ + " OR ".join(title_conditions) + """
            ORDER BY episode_num LIMIT 25
        """
        cursor.execute(sql, title_params * 2)
        rows = cursor.fetchall()
        if rows:
            title = rows[0]["title"]
            lines = [f"【《{title}》剧情摘要】"]
            for r in rows:
                if r["summary"]:
                    lines.append(f"  第{r['episode_num']}集: {r['summary']}")
            parts.append("\n".join(lines))
            # 查到剧集后也注入角色信息
            did = rows[0]["drama_id"]
            cursor.execute("SELECT name, role, description FROM drama_characters WHERE drama_id = ? LIMIT 8", (did,))
            chars = cursor.fetchall()
            if chars:
                cl = ["【角色信息】"]
                for ch in chars:
                    cl.append(f"  {ch['name']}（{ch['role']}）: {ch['description']}")
                parts.append("\n".join(cl))

    # 6. 按角色名搜索（全库，不限 drama_id）
    char_name_conditions = []
    char_name_params = []
    for kw in keywords:
        char_name_conditions.append("dc.name LIKE ?")
        char_name_params.append(f"%{kw}%")
    if char_name_conditions:
        cursor.execute(
            "SELECT dc.name, dc.role, dc.description, d.title as drama_title FROM drama_characters dc"
            " JOIN dramas d ON dc.drama_id = d.id WHERE " +
            " OR ".join(char_name_conditions) + " LIMIT 5",
            char_name_params,
        )
        chars = cursor.fetchall()
        if chars:
            cl = ["【角色信息】"]
            for ch in chars:
                cl.append(f"  {ch['name']}（《{ch['drama_title']}》{ch['role']}）: {ch['description']}")
            parts.append("\n".join(cl))

    # 7. 摘要兜底：取当前剧集（或最近几集）的摘要
    if drama_id:
        # 先查 drama_summaries
        cursor.execute("""
            SELECT ds.episode_id, ds.summary, e.episode_num
            FROM drama_summaries ds
            JOIN episodes e ON ds.episode_id = e.episode_id
            WHERE ds.drama_id = ?
            ORDER BY e.episode_num LIMIT 30
        """, (drama_id,))
        summaries = cursor.fetchall()
        # 同时查 episode_content_summary
        cursor.execute("""
            SELECT ecs.episode_id, COALESCE(ecs.long_summary, ecs.short_summary) as summary, e.episode_num
            FROM episode_content_summary ecs
            JOIN episodes e ON ecs.episode_id = e.episode_id
            WHERE ecs.drama_id = ?
            ORDER BY e.episode_num LIMIT 30
        """, (drama_id,))
        summaries += cursor.fetchall()
        if summaries:
            lines = ["【剧情摘要】"]
            for s in summaries:
                lines.append(f"  第{s['episode_num']}集: {s['summary']}")
            parts.append("\n".join(lines))

    conn.close()
    return "\n\n".join(parts) if parts else ""
