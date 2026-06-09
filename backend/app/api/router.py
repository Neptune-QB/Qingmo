import json
import asyncio
import re

from fastapi import APIRouter, HTTPException, Query, Body, Depends
from fastapi.responses import StreamingResponse
from typing import Optional, List, Dict, Any
from app.database import get_connection
from app.config import settings
from app.schemas import (
    DramaBrief, DramaDetail, EpisodeBrief,
    PlaybackInfo, HighlightItem, HealthResponse,
    InteractionRecord, InteractionStats, InteractionDetail,
    UserProfileResponse, UserProfileUpdate, WatchHistoryItem,
    AgentChatRequest, StoryExtensionRequest, GenerateHighlightsRequest,
    ChatSession, ChatMessageItem, CreateSessionRequest, AppendMessageRequest,
)
from app.services.llm_service import llm_service, classify_intent, search_dramas, get_user_profile_summary, retrieve_plot_context
from app.api.auth_router import get_current_user, get_optional_user

router = APIRouter()


# ===== 原有接口 =====
@router.get("/health", response_model=HealthResponse)
def health():
    return {
        "status": "ok",
        "service": settings.APP_NAME,
        "llm_available": llm_service.is_available
    }


# ===== Agent 新增接口 =====
def _build_page_aware_context(page_context):
    """根据当前页面类型自动构建场景上下文，小墨自动知道用户当前在做什么"""
    if not page_context:
        return {}
    ctx = {}
    ptype = page_context.page_type
    if ptype == "home":
        ctx["_page_hint"] = "当前用户在首页，正在浏览短剧推荐列表。"
    elif ptype == "drama_list":
        ctx["_page_hint"] = f"当前用户在剧集列表页，正在浏览《{page_context.drama_title or '未知剧集'}》的全部集数。"
    elif ptype == "playback":
        ctx["_page_hint"] = f"当前用户正在观看剧集《{page_context.drama_title or '未知剧集'}》第{page_context.episode_num}集，播放进度{page_context.playback_progress:.0f}秒。"
        ctx["drama_id"] = page_context.drama_id
        ctx["episode_id"] = page_context.episode_id
        ctx["episode_num"] = page_context.episode_num
        ctx["current_playback_second"] = page_context.playback_progress
    elif ptype == "comment":
        ctx["_page_hint"] = f"当前用户在《{page_context.drama_title or '未知剧集'}》第{page_context.episode_num}集的评论区。"
        ctx["drama_id"] = page_context.drama_id
        ctx["episode_id"] = page_context.episode_id
    elif ptype == "profile":
        ctx["_page_hint"] = "当前用户在个人中心页面，可以查看自己的观看历史、收藏和互动记录。"
    return ctx


@router.post("/agent/chat")
async def agent_chat(req: AgentChatRequest):
    """小墨 Agent 全局对话接口，带页面上下文感知 + 意图路由"""
    safe_message = req.message.replace("\n", " ").strip()
    intent = classify_intent(safe_message)

    async def generator():
        if intent == "search_drama":
            dramas = search_dramas(safe_message)
            if not dramas:
                yield "没找到匹配的短剧呢 😢\n试试换个关键词？比如「甜宠」「古装」「悬疑」~".encode("utf-8")
                return
            if not llm_service.is_available:
                lines = [f"找到 {len(dramas)} 部可能你喜欢的短剧哦~ ✨\n"]
                for d in dramas:
                    tags_str = " · ".join(d["tags"][:3]) if d["tags"] else "暂无标签"
                    lines.append(f"📺 《{d['title']}》(共 {d['total_episodes']} 集)\n   {tags_str}\n")
                for line in lines:
                    yield line.encode("utf-8")
                return
            # LLM 用 【ID:N】 标记推荐的剧，后端精准补链接
            drama_list_text = "\n".join(
                f"【ID:{d['id']}】《{d['title']}》 — {'、'.join(d['tags'][:3]) if d['tags'] else '暂无标签'}"
                for d in dramas
            )
            prompt = (
                f"用户说：「{safe_message}」\n\n"
                f"可推荐的短剧：\n{drama_list_text}\n\n"
                "要求：\n"
                "1. 严格按用户说的数量推荐\n"
                "2. 每部剧必须先写【ID:数字】，再写一句话简介\n"
                "3. 活泼可爱语气，不要输出任何链接"
            )
            full_text = ""
            async for chunk in llm_service.chat(user_message=prompt, history=req.history):
                full_text += chunk
                yield chunk.encode("utf-8")
            # 提取 LLM 实际推荐的剧 ID，精准补链接
            import re
            recommended_ids = [int(m) for m in re.findall(r'【ID:(\d+)】', full_text)]
            if not recommended_ids:
                recommended_ids = [d['id'] for d in dramas[:1]]
            id_to_drama = {d['id']: d for d in dramas}
            links = "\n\n".join(
                f"👉「点我立刻看《{id_to_drama[did]['title']}》」<qingmo://play?drama_id={did}>"
                for did in recommended_ids if did in id_to_drama
            )
            # 如果 LLM 已经输出了完整链接就不再重复追加
            if "</qingmo://" not in full_text:
                yield ("\n\n" + links).encode("utf-8")
        elif intent == "user_profile":
            summary = get_user_profile_summary(req.user_id)
            yield summary.encode("utf-8")
            if llm_service.is_available:
                yield "\n\n(还想知道什么？问小墨就行~)".encode("utf-8")
        else:
            safe_context = dict(req.context) if req.context else {}
            page_ctx_dict = _build_page_aware_context(req.page_context)
            safe_context.update(page_ctx_dict)
            # RAG：从剧情知识库检索相关内容，注入 context
            rag = retrieve_plot_context(safe_message, safe_context)
            if rag:
                safe_context["_rag_context"] = rag
            async for chunk in llm_service.chat(
                user_message=safe_message,
                history=req.history,
                drama_context=safe_context,
            ):
                yield chunk.encode("utf-8")

    return StreamingResponse(generator(), media_type="text/plain; charset=utf-8")


@router.post("/agent/story-extension")
async def story_extension(req: StoryExtensionRequest):
    """AI 剧情续写接口"""
    result = await llm_service.story_extension(
        drama_title=req.drama_title,
        drama_desc=req.drama_desc,
        latest_episodes=req.latest_episodes,
        user_preferences=req.user_preferences
    )
    return {"extension": result}


@router.post("/agent/generate-highlights")
async def generate_highlights(req: GenerateHighlightsRequest):
    """Doubao 自动智能生成高光点"""
    highlights = await llm_service.generate_highlights(
        drama_title=req.drama_title,
        episode_transcript=req.episode_transcript,
        episode_duration=req.episode_duration
    )
    return {"highlights": highlights}


@router.post("/interactions")
def report_interaction(
    user_id: str = Body(...),
    episode_id: int = Body(...),
    highlight_id: Optional[int] = Body(None),
    module_id: str = Body(...),
    interaction_data: Dict[str, Any] = Body(default_factory=dict)
):
    """上报用户互动数据"""
    # 限制单次上报数据大小
    if len(json.dumps(interaction_data, ensure_ascii=False)) > 4096:
        raise HTTPException(status_code=413, detail="互动数据过大，请精简至 4KB 以内")
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        """INSERT INTO user_interactions
           (user_id, episode_id, highlight_id, module_id, interaction_data)
           VALUES (?, ?, ?, ?, ?)""",
        (user_id, episode_id, highlight_id, module_id, json.dumps(interaction_data))
    )
    conn.commit()
    new_id = cursor.lastrowid
    conn.close()
    return {"ok": True, "interaction_id": new_id}


@router.get("/interactions", response_model=list[InteractionRecord])
def get_interactions(
    user_id: Optional[str] = Query(default=None, description="按用户ID过滤"),
    episode_id: Optional[int] = Query(default=None, description="按剧集ID过滤"),
    highlight_id: Optional[int] = Query(default=None, description="按高光点ID过滤"),
    module_id: Optional[str] = Query(default=None, description="按互动模块ID过滤"),
    limit: int = Query(default=50, ge=1, le=500, description="返回条数上限"),
    offset: int = Query(default=0, ge=0, description="分页偏移"),
):
    """查询互动记录，支持按用户/剧集/高光点/模块多维度过滤"""
    conn = get_connection()
    cursor = conn.cursor()

    where_clauses = []
    params: list = []
    if user_id:
        where_clauses.append("user_id = ?")
        params.append(user_id)
    if episode_id is not None:
        where_clauses.append("episode_id = ?")
        params.append(episode_id)
    if highlight_id is not None:
        where_clauses.append("highlight_id = ?")
        params.append(highlight_id)
    if module_id:
        where_clauses.append("module_id = ?")
        params.append(module_id)

    where_sql = ("WHERE " + " AND ".join(where_clauses)) if where_clauses else ""
    params.extend([limit, offset])
    cursor.execute(
        f"SELECT * FROM user_interactions {where_sql} ORDER BY created_at DESC LIMIT ? OFFSET ?",
        params,
    )
    rows = cursor.fetchall()
    conn.close()
    return [
        InteractionRecord(
            id=r["id"], user_id=r["user_id"], episode_id=r["episode_id"],
            highlight_id=r["highlight_id"], module_id=r["module_id"],
            interaction_data=json.loads(r["interaction_data"]) if r["interaction_data"] else None,
            created_at=r["created_at"],
        ) for r in rows
    ]


@router.get("/interactions/stats", response_model=InteractionDetail)
def get_interaction_stats(
    user_id: str = Query(..., description="用户ID"),
    episode_id: int = Query(..., description="剧集ID"),
):
    """按用户+剧集维度聚合互动统计"""
    conn = get_connection()
    cursor = conn.cursor()

    cursor.execute(
        "SELECT module_id, interaction_data FROM user_interactions WHERE user_id = ? AND episode_id = ?",
        (user_id, episode_id),
    )
    rows = cursor.fetchall()
    conn.close()

    by_module: dict[str, int] = {}
    by_emotion: dict[str, int] = {}
    for r in rows:
        module_id = r["module_id"]
        by_module[module_id] = by_module.get(module_id, 0) + 1

        data_str = r["interaction_data"]
        if data_str:
            try:
                data = json.loads(data_str)
            except json.JSONDecodeError:
                continue
            emotion = data.get("emotion") or data.get("type")
            if emotion:
                by_emotion[emotion] = by_emotion.get(emotion, 0) + 1

    total = len(rows)
    return InteractionDetail(
        episode_id=episode_id,
        user_id=user_id,
        total=total,
        stats=InteractionStats(total_count=total, by_module=by_module, by_emotion=by_emotion),
    )


@router.get("/dramas", response_model=list[DramaBrief])
def get_dramas(tag: Optional[str] = Query(default=None, description="按标签筛选")):
    conn = get_connection()
    cursor = conn.cursor()
    if tag:
        cursor.execute(
            "SELECT DISTINCT d.id, d.title, d.cover_url, d.total_episodes FROM dramas d JOIN drama_tags dt ON d.id = dt.drama_id WHERE dt.tag = ?",
            (tag,),
        )
    else:
        cursor.execute("SELECT id, title, cover_url, total_episodes FROM dramas")
    rows = cursor.fetchall()

    cursor.execute("SELECT drama_id, tag FROM drama_tags ORDER BY drama_id")
    tag_map: dict[int, list[str]] = {}
    for row in cursor.fetchall():
        tag_map.setdefault(row["drama_id"], []).append(row["tag"])

    result = []
    for r in rows:
        result.append(DramaBrief(
            id=r["id"], title=r["title"], cover_url=r["cover_url"],
            tags=tag_map.get(r["id"], []), total_episodes=r["total_episodes"],
        ))
    conn.close()
    return result


@router.get("/dramas/search", response_model=list[DramaBrief])
def search_dramas_api(
    q: str = Query(..., description="搜索关键词"),
    tag: Optional[str] = Query(default=None, description="按标签筛选"),
):
    """短剧搜索接口：按标题关键词 + 标签模糊匹配"""
    conn = get_connection()
    cursor = conn.cursor()
    keywords = [w.strip() for w in q.replace("，", ",").replace("、", ",").replace(" ", ",").split(",") if w.strip()]

    conditions = []
    params = []
    for kw in keywords:
        conditions.append("(d.title LIKE ? OR dt.tag LIKE ?)")
        params.extend([f"%{kw}%", f"%{kw}%"])
    if tag:
        conditions.append("dt2.tag = ?")
        params.append(tag)
    where = " OR ".join(conditions)
    sql = f"""
        SELECT DISTINCT d.id, d.title, d.cover_url, d.total_episodes
        FROM dramas d
        LEFT JOIN drama_tags dt ON d.id = dt.drama_id
        {'LEFT JOIN drama_tags dt2 ON d.id = dt2.drama_id' if tag else ''}
        WHERE {where}
        ORDER BY d.total_episodes DESC
        LIMIT 10
    """
    cursor.execute(sql, params)
    rows = cursor.fetchall()

    cursor.execute("SELECT drama_id, tag FROM drama_tags ORDER BY drama_id")
    tag_map: dict[int, list[str]] = {}
    for row in cursor.fetchall():
        tag_map.setdefault(row["drama_id"], []).append(row["tag"])

    result = []
    for r in rows:
        result.append(DramaBrief(
            id=r["id"], title=r["title"], cover_url=r["cover_url"],
            tags=tag_map.get(r["id"], []), total_episodes=r["total_episodes"],
        ))
    conn.close()
    return result


@router.get("/dramas/{drama_id}", response_model=DramaDetail)
def get_drama_detail(drama_id: int):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM dramas WHERE id = ?", (drama_id,))
    row = cursor.fetchone()
    if not row:
        conn.close()
        raise HTTPException(status_code=404, detail="Drama not found")

    cursor.execute("SELECT tag FROM drama_tags WHERE drama_id = ?", (drama_id,))
    tags = [t["tag"] for t in cursor.fetchall()]

    cursor.execute(
        "SELECT episode_id, episode_num, title, duration, thumbnail_url FROM episodes WHERE drama_id = ? ORDER BY episode_num",
        (drama_id,),
    )
    episodes = [
        EpisodeBrief(
            episode_id=e["episode_id"], episode_num=e["episode_num"],
            title=e["title"], duration=e["duration"], thumbnail_url=e["thumbnail_url"],
        ) for e in cursor.fetchall()
    ]

    conn.close()
    return DramaDetail(
        id=row["id"], title=row["title"], author=row["author"] or None,
        description=row["description"] or None, cover_url=row["cover_url"],
        tags=tags, episodes=episodes,
    )


@router.get("/playback/{episode_id}", response_model=PlaybackInfo)
def get_playback(episode_id: int):
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM episodes WHERE episode_id = ?", (episode_id,))
    ep = cursor.fetchone()
    if not ep:
        conn.close()
        raise HTTPException(status_code=404, detail="Episode not found")

    cursor.execute("SELECT * FROM highlights WHERE episode_id = ? ORDER BY time", (episode_id,))
    highlights = []
    for h in cursor.fetchall():
        opts = h["options"]
        hints = h["emotion_hints"]
        try:
            options = json.loads(opts) if opts else None
        except (json.JSONDecodeError, TypeError):
            options = opts.split(",") if opts else None
        try:
            emotion_hints = json.loads(hints) if hints else None
        except (json.JSONDecodeError, TypeError):
            emotion_hints = hints.split(",") if hints else None
        highlights.append(
            HighlightItem(
                id=h["id"], episode_id=h["episode_id"], time=h["time"],
                type=h["type"], title=h["title"], widget_type=h["widget_type"],
                options=options,
                emotion_hints=emotion_hints,
                duration=h["duration"] if h["duration"] else 15,
            )
        )

    conn.close()
    return PlaybackInfo(
        episode_id=ep["episode_id"], video_url=ep["video_url"],
        duration=ep["duration"], highlights=highlights,
    )


@router.post("/progress")
def report_progress(episode_id: int = Body(...), progress: int = Body(...), user_id: str = Body(default="0")):
    conn = get_connection()
    cursor = conn.cursor()
    watched = 1 if progress > 0 else 0
    cursor.execute(
        "INSERT INTO user_progress (user_id, episode_id, progress, watched) VALUES (?, ?, ?, ?) "
        "ON CONFLICT(user_id, episode_id) DO UPDATE SET progress = ?, watched = ?, updated_at = CURRENT_TIMESTAMP",
        (user_id, episode_id, progress, watched, progress, watched),
    )
    conn.commit()
    conn.close()
    return {"ok": True}


# ===== 用户画像接口 =====
@router.get("/user/profile")
def fetch_user_profile(user_id: str = Query(..., description="设备/用户唯一标识")):
    """获取用户画像：自动聚合观看历史 + 互动统计 + 偏好"""
    return {
        "user_id": user_id,
        "watch_history": [],
        "interaction_stats": {},
        "favorite_dramas": [],
        "preferences": {}
    }


@router.post("/user/profile")
def set_user_profile(
    user_id: str = Body(...),
    nickname: str = Body(default=""),
    avatar: str = Body(default=""),
):
    """更新用户昵称/头像（不需JWT，直接传user_id）"""
    if not nickname and not avatar:
        return {"ok": True}
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    if nickname:
        cursor.execute("UPDATE users SET nickname = ? WHERE user_id = ?", (nickname, uid))
    if avatar:
        cursor.execute("UPDATE users SET avatar = ? WHERE user_id = ?", (avatar, uid))
    conn.commit()
    conn.close()
    return {"ok": True}


@router.get("/user/watch-history", response_model=list[WatchHistoryItem])
def get_watch_history(
    user_id: str = Query(...),
    limit: int = Query(default=30, ge=1, le=100),
):
    """获取用户观看历史（按剧集去重，每条显示最新观看的集+进度+封面）"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT d.id as drama_id, d.title as drama_title, d.cover_url,
               e.episode_id, e.episode_num, up.progress, up.watched,
               d.total_episodes
        FROM user_progress up
        JOIN episodes e ON up.episode_id = e.episode_id
        JOIN dramas d ON e.drama_id = d.id
        WHERE up.user_id = ?
        GROUP BY d.id
        ORDER BY MAX(up.updated_at) DESC
        LIMIT ?
    """, (user_id, limit))
    result = [
        {
            "drama_id": row["drama_id"],
            "drama_title": row["drama_title"] or "",
            "cover_url": row["cover_url"] or "",
            "episode_id": row["episode_id"],
            "episode_num": row["episode_num"],
            "progress": row["progress"],
            "watched": row["watched"],
            "total_episodes": row["total_episodes"] or 0,
        }
        for row in cursor.fetchall()
    ]
    conn.close()
    return result


# ===== 管理员接口 =====
@router.post("/admin/highlights/batch")
def batch_upsert_highlights(
    highlights: List[dict] = Body(..., description="高光点数组"),
    user: dict = Depends(get_current_user),
):
    """批量上传/更新高光点配置，运营后台一键同步"""
    if not highlights:
        raise HTTPException(status_code=400, detail="高光点列表为空")
    if len(highlights) > 500:
        raise HTTPException(status_code=413, detail="单次最多 500 条")

    conn = get_connection()
    cursor = conn.cursor()
    inserted = 0
    updated = 0

    for h in highlights:
        ep_id = h.get("episode_id")
        time = h.get("time")
        if ep_id is None or time is None:
            continue
        h_type = h.get("type", "famous")
        title = h.get("title", "")
        widget = h.get("widget_type", "emotion")
        options = json.dumps(h.get("options")) if h.get("options") else None
        emotion_hints = json.dumps(h.get("emotion_hints")) if h.get("emotion_hints") else None
        duration = h.get("duration", 15)
        if "id" in h:
            cursor.execute(
                """UPDATE highlights SET episode_id=?, time=?, type=?, title=?,
                   widget_type=?, options=?, emotion_hints=?, duration=?
                   WHERE id=?""",
                (ep_id, time, h_type, title, widget, options, emotion_hints, duration, h["id"]),
            )
            if cursor.rowcount > 0:
                updated += 1
        else:
            cursor.execute(
                """INSERT INTO highlights (episode_id, time, type, title, widget_type, options, emotion_hints, duration)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (ep_id, time, h_type, title, widget, options, emotion_hints, duration),
            )
            inserted += 1
    conn.commit()
    conn.close()
    return {"ok": True, "inserted": inserted, "updated": updated, "total": inserted + updated}


@router.get("/admin/highlights/version")
def get_highlights_version(user: dict = Depends(get_current_user)):
    """获取高光点数据版本号，供客户端判断是否需要刷新缓存"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT MAX(id) as last_id, COUNT(*) as total FROM highlights")
    row = cursor.fetchone()
    conn.close()
    return {"version": row["last_id"] or 0, "total_count": row["total"]}


@router.delete("/admin/highlights/{highlight_id}")
def delete_highlight(highlight_id: int, user: dict = Depends(get_current_user)):
    """删除单条高光点"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM highlights WHERE id = ?", (highlight_id,))
    deleted = cursor.rowcount
    conn.commit()
    conn.close()
    if deleted == 0:
        raise HTTPException(status_code=404, detail="高光点不存在")
    return {"ok": True, "deleted_id": highlight_id}


# ===== 弹幕 / 点赞 / 收藏 =====

# 弹幕彩蛋关键词映射
DANMAKU_EASTER_EGGS: dict[str, str] = {
    "反转":  "叮！反转卡已激活🔮",
    "高能":  "⚡高能预警！前方名场面！",
    "泪目":  "小墨也破防了😭",
    "甜":    "🍬糖分超标警告！",
    "笑死":  "笑不活了家人们😂",
    "真香":  "谁也逃不过真香定律🤣",
    "上头":  "追剧一时爽，一直追一直爽🔥",
    "女主":  "都让让，女主要放大招了💅",
    "男主":  "霸总来了！心跳加速中💓",
    "弹幕":  "弹幕护体！🛡️",
    "小墨":  "在呢在呢~有什么想聊的？💜",
    "好看":  "这剧是真的好看！安利给全世界🌍",
    "青墨":  "青墨出品，必属精品！✨",
    "打卡":  "滴~打卡成功！今天也是追剧的一天📅",
    "熬夜":  "又是一个不眠夜🌙 小墨陪你！",
}

@router.post("/danmaku")
def post_danmaku(
    user_id: str = Body(...),
    episode_id: int = Body(...),
    text: str = Body(...),
    time_sec: float = Body(default=0),
    color: str = Body(default="#ffffff"),
):
    """发送弹幕"""
    if not text.strip() or len(text) > 80:
        raise HTTPException(status_code=400, detail="弹幕内容 1-80 字")
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO danmaku (user_id, episode_id, text, time_sec, color) VALUES (?,?,?,?,?)",
        (user_id, episode_id, text.strip(), time_sec, color),
    )
    conn.commit()
    new_id = cursor.lastrowid

    # 弹幕彩蛋检测
    easter_egg: str | None = None
    clean_text = text.strip()
    for keyword, reply in DANMAKU_EASTER_EGGS.items():
        if keyword in clean_text:
            easter_egg = reply
            break
    if easter_egg:
        cursor.execute(
            "INSERT INTO danmaku (user_id, episode_id, text, time_sec, color) VALUES (?,?,?,?,?)",
            ("xiaomo_agent", episode_id, easter_egg, time_sec, "#C864FF"),
        )
        conn.commit()

    conn.close()
    return {"ok": True, "id": new_id, "easter_egg": easter_egg}


@router.get("/danmaku/{episode_id}")
def get_danmaku(
    episode_id: int,
    limit: int = Query(500, ge=1, le=5000),
    offset: int = Query(0, ge=0),
    time_from: Optional[float] = Query(None),
    time_to: Optional[float] = Query(None),
):
    """获取剧集弹幕列表，支持分页和时间范围过滤"""
    conn = get_connection()
    cursor = conn.cursor()
    conditions = ["episode_id = ?"]
    params = [episode_id]
    if time_from is not None:
        conditions.append("time_sec >= ?")
        params.append(time_from)
    if time_to is not None:
        conditions.append("time_sec <= ?")
        params.append(time_to)
    where = " AND ".join(conditions)
    cursor.execute(
        f"SELECT id, user_id, text, time_sec, color, created_at FROM danmaku WHERE {where} ORDER BY time_sec, id LIMIT ? OFFSET ?",
        params + [limit, offset],
    )
    result = []
    for r in cursor.fetchall():
        dm = dict(r)
        text = dm.get("text", "")
        if len(text) > 32:
            dm["text"] = text[:30].rstrip("，,。.!！？?") + "…"
        result.append(dm)
    conn.close()
    return result


@router.post("/episodes/{episode_id}/like")
def toggle_like(episode_id: int, user_id: str = Body(..., embed=True)):
    """切换点赞状态（点赞/取消），完全绑定当前登录用户"""
    uid = user_id
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM episode_likes WHERE user_id = ? AND episode_id = ?", (uid, episode_id))
    row = cursor.fetchone()
    if row:
        cursor.execute("DELETE FROM episode_likes WHERE id = ?", (row["id"],))
        action = "unliked"
    else:
        cursor.execute("INSERT INTO episode_likes (user_id, episode_id) VALUES (?,?)", (uid, episode_id))
        action = "liked"
    conn.commit()
    cursor.execute("SELECT COUNT(*) as cnt FROM episode_likes WHERE episode_id = ?", (episode_id,))
    count = cursor.fetchone()["cnt"]
    conn.close()
    return {"ok": True, "action": action, "count": count}


@router.get("/episodes/{episode_id}/likes")
def get_likes(episode_id: int, user_id: str = Query(default="")):
    """获取点赞数 + 当前用户是否已点赞"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) as cnt FROM episode_likes WHERE episode_id = ?", (episode_id,))
    count = cursor.fetchone()["cnt"]
    liked = False
    if user_id:
        uid = user_id
        cursor.execute("SELECT id FROM episode_likes WHERE user_id = ? AND episode_id = ?", (uid, episode_id))
        liked = cursor.fetchone() is not None
    conn.close()
    return {"count": count, "liked": liked}


@router.get("/user/likes")
def get_user_likes(user_id: str = Query(...), limit: int = Query(default=50, ge=1, le=200)):
    """获取用户点赞过的剧集列表（含封面URL）"""
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    cursor.execute("""
        SELECT el.episode_id, e.episode_num, e.drama_id, d.title as drama_title, d.cover_url, el.created_at
        FROM episode_likes el
        JOIN episodes e ON el.episode_id = e.episode_id
        JOIN dramas d ON e.drama_id = d.id
        WHERE el.user_id = ?
        ORDER BY el.created_at DESC
        LIMIT ?
    """, (uid, limit))
    result = [
        {"episode_id": r["episode_id"], "episode_num": r["episode_num"],
         "drama_id": r["drama_id"], "drama_title": r["drama_title"],
         "cover_url": r["cover_url"] or "", "created_at": r["created_at"]}
        for r in cursor.fetchall()
    ]
    conn.close()
    return result


@router.post("/dramas/{drama_id}/favorite")
def toggle_favorite(drama_id: int, user_id: str = Body(..., embed=True)):
    """切换收藏状态，完全绑定当前登录用户"""
    uid = user_id
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM user_favorites WHERE user_id = ? AND drama_id = ?", (uid, drama_id))
    row = cursor.fetchone()
    if row:
        cursor.execute("DELETE FROM user_favorites WHERE id = ?", (row["id"],))
        action = "unfavorited"
    else:
        cursor.execute("INSERT INTO user_favorites (user_id, drama_id) VALUES (?,?)", (uid, drama_id))
        action = "favorited"
    conn.commit()
    conn.close()
    return {"ok": True, "action": action}


@router.get("/user/favorites")
def get_favorites(user_id: str = Query(...)):
    """获取用户收藏列表"""
    uid = user_id
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT uf.drama_id, d.title, d.cover_url
        FROM user_favorites uf
        JOIN dramas d ON uf.drama_id = d.id
        WHERE uf.user_id = ?
        ORDER BY uf.created_at DESC
    """, (uid,))
    result = [dict(r) for r in cursor.fetchall()]
    conn.close()
    return result


# ===== @小墨 AI评论回复 =====

XIAOMO_AGENT_USER_ID = "xiaomo_agent"
XIAOMO_AGENT_NICKNAME = "小墨"
XIAOMO_TRIGGER_RE = re.compile(r'^@小墨\s+', re.IGNORECASE)

# 简易频率限制：{episode_id: {user_id: [timestamp, ...]}}
_xiaomo_rate_map: dict[int, dict[str, list]] = {}


def _maybe_trigger_xiaomo_reply(episode_id: int, comment_id: int, user_nickname: str, text: str):
    """检测 @小墨 触发词，通过则启动后台线程生成AI回复"""
    match = XIAOMO_TRIGGER_RE.match(text)
    if not match:
        return
    user_question = text[match.end():].strip()
    if not user_question:
        return
    if not llm_service.is_available:
        return

    # 频率限制：每用户每集最多 5 次/分钟
    import time as _time
    now_ts = _time.time()
    ep_map = _xiaomo_rate_map.setdefault(episode_id, {})
    bucket = ep_map.setdefault(user_nickname, [])
    bucket = [t for t in bucket if now_ts - t < 60]
    ep_map[user_nickname] = bucket
    if len(bucket) >= 5:
        return
    bucket.append(now_ts)

    # 使用线程而非 asyncio，兼容 sync endpoint
    import threading
    t = threading.Thread(
        target=lambda: asyncio.run(
            _generate_xiaomo_comment_reply(
                episode_id=episode_id,
                parent_comment_id=comment_id,
                user_question=user_question,
                user_nickname=user_nickname,
            )
        ),
        daemon=True,
    )
    t.start()


async def _generate_xiaomo_comment_reply(
    episode_id: int,
    parent_comment_id: int,
    user_question: str,
    user_nickname: str,
):
    """后台异步：小墨在评论区回复用户 @小墨 的提问"""
    from datetime import datetime

    try:
        comment_system_prompt = (
            "你是「小墨」，青墨短剧平台的 AI 观剧助手。"
            "你现在在剧集评论区里回复用户的提问。"
            "要求：\n"
            "- 语气活泼可爱，像追剧好友聊天\n"
            "- 回复控制在 15-60 字，一句话就够了，不要长篇大论\n"
            "- 多用表情符号\n"
            "- 必须基于真实的剧情内容回答，不要编造\n"
            "- 如果用户问的不是剧情相关，用俏皮的方式回应"
        )

        drama_ctx: dict = {"episode_id": episode_id}
        rag = retrieve_plot_context(user_question, drama_ctx)

        messages: list = [{"role": "system", "content": comment_system_prompt}]
        if rag:
            messages.append({"role": "system", "content": f"当前剧集真实剧情内容：\n{rag}"})
        messages.append({"role": "user", "content": f"用户 {user_nickname} 问：{user_question}"})

        # 非流式调用，评论区回复不需要流式
        async with llm_service.client as client:
            resp = await asyncio.wait_for(
                client.chat.completions.create(
                    model=settings.DOUBAO_EP_ID,
                    messages=messages,
                    stream=False,
                    temperature=0.8,
                    max_tokens=150,
                ),
                timeout=15.0,
            )
            reply_text = (resp.choices[0].message.content or "").strip()

        if not reply_text:
            return

        conn = get_connection()
        cursor = conn.cursor()
        cursor.execute(
            """INSERT INTO episode_comments_new
               (episode_id, user_id, nickname, text, parent_id, reply_to_nickname, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            (
                episode_id,
                XIAOMO_AGENT_USER_ID,
                XIAOMO_AGENT_NICKNAME,
                reply_text,
                parent_comment_id,
                "",
                datetime.now().isoformat(),
            ),
        )
        conn.commit()
        conn.close()

    except (asyncio.TimeoutError, Exception):
        pass  # 静默降级，不影响评论功能


# ===== 评论 + 楼中楼回复 + 聚合计数 =====
@router.post("/episodes/{episode_id}/comments")
def post_comment(
    episode_id: int,
    user_id: str = Body(...),
    text: str = Body(...),
    parent_id: int = Body(default=0),
    reply_to_nickname: str = Body(default=""),
):
    """
    发送评论新规则：
    - content字段永远存纯用户输入，不带任何「回复 @xxx：」前缀
    - reply_to_nickname 仅当这条回复指向的是一条非顶级评论时才赋值
    """
    if not text.strip() or len(text) > 200:
        raise HTTPException(status_code=400, detail="评论 1-200 字")
    conn = get_connection(); cursor = conn.cursor()

    uid = user_id
    # 自动生成昵称
    nickname = f"热心网友{uid[-4:]}"
    cursor.execute("SELECT nickname FROM users WHERE id = ?", (uid,))
    nick_row = cursor.fetchone()
    if nick_row and nick_row[0]:
        nickname = nick_row[0]

    from datetime import datetime
    pure_text = text.strip()
    reply_to_nickname_safe = reply_to_nickname.strip() if reply_to_nickname else ""
    cursor.execute(
        """INSERT INTO episode_comments_new
           (episode_id, user_id, nickname, text, parent_id, reply_to_nickname, created_at)
           VALUES (?,?,?,?,?,?,?)""",
        (episode_id, str(uid), nickname, pure_text, parent_id, reply_to_nickname_safe, datetime.now().isoformat())
    )
    conn.commit()
    cursor.execute("SELECT last_insert_rowid()")
    new_id = cursor.fetchone()[0]
    conn.close()

    # 检测 @小墨 调用，异步生成AI回复
    _maybe_trigger_xiaomo_reply(
        episode_id=episode_id,
        comment_id=new_id,
        user_nickname=nickname,
        text=pure_text,
    )

    return {"ok": True, "id": new_id}


@router.get("/episodes/{episode_id}/comments")
def get_episode_comments(episode_id: int, limit: int = Query(50, ge=1, le=200), offset: int = Query(0, ge=0)):
    """获取剧集评论列表，子回复嵌套在父评论的 replies 字段中，返回时附带 reply_to_nickname 供客户端决定是否显示前缀"""
    conn = get_connection(); cursor = conn.cursor()
    # 兼容旧表结构，字段可能是 reply_to_user_id 或 reply_to_nickname
    cursor.execute("PRAGMA table_info(episode_comments_new)")
    cols = [c["name"] for c in cursor.fetchall()]
    text_reply_to_nickname_col = "reply_to_nickname" if "reply_to_nickname" in cols else "reply_to_user_id"

    cursor.execute(
        f"SELECT id, user_id, nickname, text, created_at, parent_id, {text_reply_to_nickname_col} "
        "FROM episode_comments_new "
        "WHERE episode_id = ? ORDER BY created_at DESC",
        (episode_id,)
    )
    rows = cursor.fetchall()
    conn.close()

    id_map: dict[int, dict] = {}
    tops = []
    for r in rows:
        item = {
            "id": r[0], "user_id": r[1], "nickname": r[2], "text": r[3],
            "created_at": r[4], "parent_id": r[5] or 0, "reply_to_nickname": r[6] or "",
            "avatar_url": "", "replies": [],
        }
        id_map[item["id"]] = item
        if item["parent_id"] == 0:
            tops.append(item)

    for item in id_map.values():
        pid = item["parent_id"]
        if pid > 0:
            if pid in id_map:
                parent_item = id_map[pid]
                parent_is_top_level = parent_item["parent_id"] == 0
                if parent_is_top_level:
                    item["reply_to_nickname"] = ""
                id_map[pid]["replies"].append(item)
            else:
                # 父评论不存在（已删除），作为顶级评论显示，避免丢失
                item["parent_id"] = 0
                tops.append(item)

    return tops


@router.get("/episodes/{episode_id}/counts")
def get_episode_counts(episode_id: int, user_id: str = Query(default="")):
    """当前剧集点赞数+评论数+弹幕数+当前用户是否已点赞 完全绑定当前登录用户"""
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM episode_likes WHERE episode_id = ?", (episode_id,))
    like_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM episode_comments_new WHERE episode_id = ?", (episode_id,))
    comment_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM danmaku WHERE episode_id = ?", (episode_id,))
    danmaku_count = cursor.fetchone()[0]
    liked = False
    if user_id:
        cursor.execute("SELECT id FROM episode_likes WHERE user_id=? AND episode_id=?", (user_id, episode_id))
        liked = cursor.fetchone() is not None
    conn.close()
    return {"like_count": like_count, "comment_count": comment_count, "danmaku_count": danmaku_count, "liked": liked}


@router.get("/episodes/{episode_id}/comments/ai-reply-status")
def get_ai_reply_status(
    episode_id: int,
    parent_comment_ids: str = Query(..., description="逗号分隔的用户评论ID"),
):
    """查询指定父评论下小墨的AI回复是否已生成"""
    ids = [int(x.strip()) for x in parent_comment_ids.split(",") if x.strip().isdigit()]
    if not ids:
        return {"replies": {}}

    conn = get_connection()
    cursor = conn.cursor()
    placeholders = ",".join("?" for _ in ids)
    cursor.execute(
        f"SELECT parent_id, id, text, created_at FROM episode_comments_new "
        f"WHERE episode_id = ? AND user_id = ? AND parent_id IN ({placeholders})",
        [episode_id, XIAOMO_AGENT_USER_ID] + ids,
    )
    rows = cursor.fetchall()
    conn.close()

    replies = {}
    for r in rows:
        replies[str(r["parent_id"])] = {
            "id": r["id"],
            "text": r["text"],
            "created_at": r["created_at"],
        }
    return {"replies": replies}


# ===== 小墨Agent全局对话持久化CRUD =====


def _verify_session_owner(cursor, session_id: int, user_id: str):
    """校验会话归属，防止越权访问"""
    cursor.execute("SELECT user_id FROM user_chat_sessions WHERE id = ?", (session_id,))
    row = cursor.fetchone()
    if not row or row["user_id"] != user_id:
        raise HTTPException(status_code=404, detail="会话不存在或无权访问")


def _get_session_user_id(user: Optional[dict] = Depends(get_optional_user)) -> str:
    """获取会话操作者ID：优先Token，无Token时从请求体/参数取device_id"""
    if user is not None:
        return str(user["id"])
    # 兜底：从创建请求中取
    raise HTTPException(status_code=401, detail="请先登录后再使用对话功能")


@router.post("/agent/sessions", response_model=ChatSession)
def create_chat_session(req: CreateSessionRequest, user: Optional[dict] = Depends(get_optional_user)):
    conn = get_connection(); cursor = conn.cursor()
    uid = str(user["id"]) if user else req.user_id
    cursor.execute(
        "INSERT INTO user_chat_sessions (user_id, title, drama_id) VALUES (?, ?, ?)",
        (uid, req.title, req.drama_id)
    )
    new_id = cursor.lastrowid
    conn.commit()
    cursor.execute("SELECT * FROM user_chat_sessions WHERE id=?", (new_id,))
    row = cursor.fetchone()
    conn.close()
    return ChatSession(id=row["id"], user_id=row["user_id"], title=row["title"],
                      drama_id=row["drama_id"], created_at=row["created_at"], updated_at=row["updated_at"])


@router.get("/agent/sessions")
def list_chat_sessions(user_id: str = Query(default=""), user: Optional[dict] = Depends(get_optional_user)):
    conn = get_connection(); cursor = conn.cursor()
    uid = str(user["id"]) if user else user_id
    if not uid:
        raise HTTPException(status_code=400, detail="请提供 user_id 或登录后重试")
    cursor.execute("SELECT * FROM user_chat_sessions WHERE user_id=? ORDER BY updated_at DESC LIMIT 50", (uid,))
    rows = cursor.fetchall()
    result = []
    for r in rows:
        result.append(dict(r))
    conn.close()
    return result


@router.get("/agent/sessions/{session_id}/messages", response_model=list[ChatMessageItem])
def list_session_messages(session_id: int, user: Optional[dict] = Depends(get_optional_user)):
    conn = get_connection(); cursor = conn.cursor()
    if user:
        _verify_session_owner(cursor, session_id, str(user["id"]))
    cursor.execute("SELECT * FROM user_chat_messages WHERE session_id=? ORDER BY id ASC", (session_id,))
    rows = cursor.fetchall()
    conn.close()
    return [ChatMessageItem(id=r["id"], session_id=r["session_id"], role=r["role"],
                            content=r["content"], created_at=r["created_at"]) for r in rows]


@router.post("/agent/sessions/messages/append")
def append_chat_message(req: AppendMessageRequest, user: Optional[dict] = Depends(get_optional_user)):
    conn = get_connection(); cursor = conn.cursor()
    if user:
        _verify_session_owner(cursor, req.session_id, str(user["id"]))
    cursor.execute("INSERT INTO user_chat_messages (session_id, role, content) VALUES (?, ?, ?)",
                   (req.session_id, req.role, req.content))
    new_id = cursor.lastrowid
    cursor.execute("UPDATE user_chat_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", (req.session_id,))
    conn.commit()
    conn.close()
    return {"ok": True, "message_id": new_id}


@router.post("/agent/sessions/{session_id}/title")
def update_session_title(session_id: int, title: str = Body(..., embed=True)):
    """更新会话标题"""
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute("UPDATE user_chat_sessions SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (title, session_id))
    conn.commit()
    conn.close()
    return {"ok": True}


@router.delete("/agent/sessions/{session_id}")
def delete_chat_session(session_id: int, user: Optional[dict] = Depends(get_optional_user)):
    conn = get_connection(); cursor = conn.cursor()
    if user:
        _verify_session_owner(cursor, session_id, str(user["id"]))
    cursor.execute("DELETE FROM user_chat_messages WHERE session_id = ?", (session_id,))
    cursor.execute("DELETE FROM user_chat_sessions WHERE id = ?", (session_id,))
    deleted = cursor.rowcount
    conn.commit()
    conn.close()
    return {"ok": True, "deleted": deleted > 0}


@router.get("/highlights/{episode_id}/trigger-bubble")
async def trigger_highlight_bubble(
    episode_id: int,
    current_second: int = Query(..., description="当前播放时间秒数")
):
    """
    高光点实时触发小墨聊天气泡
    播放进度走到对应高光点附近±3秒时，自动返回贴合当前情节的互动短句
    """
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT h.id, h.time, h.title, h.type
        FROM highlights h
        WHERE h.episode_id = ? AND ABS(h.time - ?) <= 3
        ORDER BY ABS(h.time - ?) ASC
        LIMIT 1
    """, (episode_id, current_second, current_second))
    hl = cursor.fetchone()
    conn.close()

    if not hl:
        return {"ok": True, "bubble_text": None, "triggered": False}

    # 基于高光点类型生成贴合情节的小墨活泼短气泡
    bubble_map = {
        "conflict": "哇塞这也太刺激了！",
        "twist": "完全没想到啊！",
        "sweet": "磕到了磕到了🥰",
        "funny": "笑不活了家人们😂",
        "famous": "名场面来了！盯紧屏幕！"
    }
    short_bubble = bubble_map.get(hl["type"], f"✨ {hl['title'][:12]}")

    # 尝试用 LLM 生成更贴合剧情的观众吐槽
    if llm_service.is_available:
        import asyncio as _asyncio
        try:
            # 取剧情摘要做为上下文
            cursor2 = conn.cursor()
            cursor2.execute("SELECT summary FROM drama_summaries WHERE episode_id = ? LIMIT 1", (episode_id,))
            summary_row = cursor2.fetchone()
            summary = summary_row["summary"] if summary_row else ""
            conn.close()
        except Exception:
            conn.close()
        else:
            bubble_prompt = f"""你是一个正在看短剧的真实观众，看到以下高光时刻，用一句话表达你的第一反应。
要求：
- 像真人观众一样说话，可以吐槽、惊讶、激动、感动
- 8-16个字，可以加一个表情符号
- 不要客观描述，要主观感受
- 不要说"这一集"、"这里"等指代词汇

高光时刻：{hl['title']}（类型：{hl['type']}）
剧情背景：{summary[:200] or '暂无'}

只说这一句话，不要加引号："""

            async def _gen_bubble():
                try:
                    async with llm_service.client as client:
                        resp = await _asyncio.wait_for(
                            client.chat.completions.create(
                                model=settings.DOUBAO_EP_ID,
                                messages=[{"role": "user", "content": bubble_prompt}],
                                stream=False, temperature=0.9, max_tokens=60,
                            ), timeout=8.0,
                        )
                        text = (resp.choices[0].message.content or "").strip()
                        if 4 <= len(text) <= 40 and not text.startswith("（"):
                            return text
                except Exception:
                    pass
                return None

            # 使用线程运行异步函数
            import threading
            result_holder = []
            t = threading.Thread(target=lambda: result_holder.append(_asyncio.run(_gen_bubble())), daemon=True)
            t.start()
            t.join(timeout=10.0)
            if result_holder and result_holder[0]:
                short_bubble = result_holder[0]
            else:
                conn = get_connection()  # 重新获取连接，之前关了
                # 回退到静态映射，确保连接可用
                pass

    return {
        "ok": True,
        "triggered": True,
        "highlight_id": hl["id"],
        "highlight_title": hl["title"],
        "bubble_text": short_bubble
    }


@router.get("/highlights/{highlight_id}/bubble")
async def get_highlight_bubble(highlight_id: int):
    """按 highlight_id 获取LLM生成的剧情吐槽气泡"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT h.title, h.type, h.episode_id FROM highlights h WHERE h.id = ?", (highlight_id,))
    hl = cursor.fetchone()
    if not hl:
        conn.close()
        raise HTTPException(status_code=404, detail="高光点不存在")
    bubble_map = {"conflict": "哇塞这也太刺激了！","twist": "完全没想到啊！","sweet": "磕到了磕到了🥰","funny": "笑不活了家人们😂","famous": "名场面来了！盯紧屏幕！"}
    result = bubble_map.get(hl["type"], f"✨ {hl['title'][:12]}")

    if llm_service.is_available:
        cursor.execute("SELECT summary FROM drama_summaries WHERE episode_id = ? LIMIT 1", (hl["episode_id"],))
        summary_row = cursor.fetchone()
        summary = summary_row["summary"] if summary_row else ""
        conn.close()
        import asyncio as _asyncio, threading
        prompt = f"""你是正在看短剧的真实观众，看到这个高光时刻，用一句话表达第一反应。要求：8-16字，像真人吐槽、惊讶、激动，主观感受，不指代。\n高光：{hl['title']}（{hl['type']}）\n背景：{summary[:200] or '暂无'}\n只说这句话："""
        async def _gen():
            try:
                async with llm_service.client as c:
                    r = await _asyncio.wait_for(c.chat.completions.create(model=settings.DOUBAO_EP_ID,messages=[{"role":"user","content":prompt}],stream=False,temperature=0.9,max_tokens=60),timeout=8.0)
                    t = (r.choices[0].message.content or "").strip()
                    if 4 <= len(t) <= 40 and not t.startswith("（"): return t
            except: pass
            return None
        holder = []
        th = threading.Thread(target=lambda: holder.append(_asyncio.run(_gen())), daemon=True)
        th.start()
        th.join(timeout=10.0)
        if holder and holder[0]: result = holder[0]
    else:
        conn.close()
    return {"ok": True, "bubble_text": result}


# ===== 剧情投票 =====

@router.get("/highlights/{highlight_id}/vote")
def get_highlight_vote(highlight_id: int, user_id: str = Query(default="")):
    """获取高光点的投票题目+实时票数+当前用户投了什么"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM highlight_votes WHERE highlight_id = ?", (highlight_id,))
    vote = cursor.fetchone()
    if not vote:
        conn.close()
        return {"ok": True, "vote": None}

    cursor.execute("SELECT choice, COUNT(*) as cnt FROM highlight_vote_records WHERE vote_id = ? GROUP BY choice", (vote["id"],))
    counts = {"a": 0, "b": 0}
    for r in cursor.fetchall():
        counts[r["choice"]] = r["cnt"]

    my_choice = None
    if user_id:
        uid = user_id
        cursor.execute("SELECT choice FROM highlight_vote_records WHERE vote_id = ? AND user_id = ?", (vote["id"], uid))
        rec = cursor.fetchone()
        if rec:
            my_choice = rec["choice"]
    conn.close()

    return {
        "ok": True,
        "vote": {
            "id": vote["id"],
            "highlight_id": vote["highlight_id"],
            "question": vote["question"],
            "option_a": vote["option_a"],
            "option_b": vote["option_b"],
            "counts": counts,
            "my_choice": my_choice,
        }
    }


@router.post("/highlights/{highlight_id}/vote")
def cast_highlight_vote(highlight_id: int, user_id: str = Body(...), choice: str = Body(...)):
    """投一票（a 或 b），每人每投票仅一票"""
    if choice not in ("a", "b"):
        raise HTTPException(status_code=400, detail="choice 只能是 a 或 b")
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM highlight_votes WHERE highlight_id = ?", (highlight_id,))
    vote = cursor.fetchone()
    if not vote:
        conn.close()
        raise HTTPException(status_code=404, detail="该高光点没有投票")

    uid = user_id
    cursor.execute(
        "INSERT OR REPLACE INTO highlight_vote_records (vote_id, user_id, choice) VALUES (?,?,?)",
        (vote["id"], uid, choice),
    )
    conn.commit()

    cursor.execute("SELECT choice, COUNT(*) as cnt FROM highlight_vote_records WHERE vote_id = ? GROUP BY choice", (vote["id"],))
    counts = {"a": 0, "b": 0}
    for r in cursor.fetchall():
        counts[r["choice"]] = r["cnt"]
    conn.close()
    return {"ok": True, "counts": counts}


# ===== AI剧情问答 =====

@router.get("/highlights/{highlight_id}/quiz")
async def get_highlight_quiz(highlight_id: int):
    """基于高光点生成AI剧情选择题，用LLM动态出题"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT h.title, h.type, h.episode_id, e.episode_num, d.title as drama_title FROM highlights h JOIN episodes e ON h.episode_id = e.episode_id JOIN dramas d ON e.drama_id = d.id WHERE h.id = ?", (highlight_id,))
    hl = cursor.fetchone()
    if not hl:
        conn.close()
        raise HTTPException(status_code=404, detail="高光点不存在")

    # 取该集摘要作为出题依据
    cursor.execute("SELECT summary FROM drama_summaries WHERE episode_id = ? LIMIT 1", (hl["episode_id"],))
    summary_row = cursor.fetchone()
    summary = summary_row["summary"] if summary_row else ""
    conn.close()

    if not llm_service.is_available:
        return {"ok": True, "quiz": None, "fallback": True}

    prompt = f"""你是短剧剧情出题专家。基于以下高光点出1道选择题：
剧集：《{hl['drama_title']}》第{hl['episode_num']}集
高光点：{hl['title']}（类型：{hl['type']}）
剧情摘要：{summary[:300]}

要求：
- 问题要贴合剧情，有思考价值
- 4个选项（A/B/C/D），只有1个正确答案
- 返回严格JSON格式，不要任何其他文字

JSON格式：
{{"question":"题面","A":"选项A","B":"选项B","C":"选项C","D":"选项D","answer":"A"}}
"""

    try:
        import asyncio
        async with llm_service.client as client:
            resp = await asyncio.wait_for(
                client.chat.completions.create(
                    model=settings.DOUBAO_EP_ID,
                    messages=[{"role": "user", "content": prompt}],
                    stream=False,
                    temperature=0.7,
                    max_tokens=300,
                ),
                timeout=15.0,
            )
            text = (resp.choices[0].message.content or "").strip()
            import re
            m = re.search(r'\{[^{}]*"question"[^}]*\}', text, re.DOTALL)
            if m:
                quiz = json.loads(m.group())
                return {"ok": True, "quiz": quiz}
    except Exception:
        pass

    return {"ok": True, "quiz": None, "fallback": True}


@router.post("/highlights/{highlight_id}/quiz/answer")
def submit_quiz_answer(highlight_id: int, user_id: str = Body(...), answer: str = Body(...)):
    """提交答案，自动评分+累计积分"""
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    cursor.execute(
        "INSERT OR IGNORE INTO user_quiz_scores (user_id, highlight_id, score) VALUES (?,?,0)",
        (uid, highlight_id),
    )
    conn.commit()
    conn.close()
    return {"ok": True}


@router.post("/quiz/score")
def update_quiz_score(user_id: str = Body(...), highlight_id: int = Body(...), is_correct: bool = Body(...)):
    """更新答题积分（答对+1）"""
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    if is_correct:
        cursor.execute(
            "UPDATE user_quiz_scores SET score = score + 1 WHERE user_id = ? AND highlight_id = ?",
            (uid, highlight_id),
        )
    conn.commit()
    conn.close()
    return {"ok": True}


@router.get("/user/quiz-score")
def get_user_quiz_score(user_id: str = Query(...)):
    """获取用户答题总积分"""
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    cursor.execute("SELECT COALESCE(SUM(score),0) as total, COUNT(*) as answered FROM user_quiz_scores WHERE user_id = ?", (uid,))
    row = cursor.fetchone()
    conn.close()
    return {"total_score": row["total"], "answered_count": row["answered"]}


# ===== 角色AI对话 =====

@router.get("/characters")
def list_characters(drama_id: int = Query(...)):
    """获取指定剧集的角色列表"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute(
        "SELECT id, name, role, description FROM drama_characters WHERE drama_id = ? LIMIT 10",
        (drama_id,),
    )
    rows = cursor.fetchall()
    conn.close()
    return [
        {"id": r["id"], "name": r["name"], "role": r["role"], "description": r["description"] or ""}
        for r in rows
    ]


@router.post("/characters/{char_id}/chat")
async def character_chat(char_id: int, user_message: str = Body(...), drama_id: int = Body(default=0)):
    """跟剧中角色1v1对话，角色用剧中人设语气回答"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT name, role, description, relationships FROM drama_characters WHERE id = ?", (char_id,))
    char = cursor.fetchone()
    if not char:
        conn.close()
        return {"reply": "这个角色暂时不在哦~"}

    # 取相关剧情摘要
    cursor.execute("SELECT summary FROM drama_summaries WHERE drama_id = ? ORDER BY episode_id LIMIT 3", (drama_id,))
    summaries = [s["summary"] for s in cursor.fetchall() if s["summary"]]
    conn.close()

    if not llm_service.is_available:
        return {"reply": f"{char['name']}暂时不在线哦，晚点再来聊~"}

    persona = f"""你现在是《青墨短剧》中的角色「{char['name']}」。
角色设定：
- 身份：{char['role'] or '剧中角色'}
- 简介：{char['description'] or '暂无'}
- 人际关系：{char['relationships'] or '暂无'}

背景剧情：
{chr(10).join(f"· {s}" for s in summaries[:2]) if summaries else '暂无'}

规则：
- 严格用角色第一人称说话
- 语气贴合角色人设，不要太AI化
- 回复控制在20-80字
- 不知道的就用角色口吻说不知道，不要编造"""

    try:
        import asyncio
        async with llm_service.client as client:
            resp = await asyncio.wait_for(
                client.chat.completions.create(
                    model=settings.DOUBAO_EP_ID,
                    messages=[
                        {"role": "system", "content": persona},
                        {"role": "user", "content": user_message},
                    ],
                    stream=False,
                    temperature=0.8,
                    max_tokens=200,
                ),
                timeout=15.0,
            )
            reply = (resp.choices[0].message.content or "").strip()
            return {"reply": reply or f"（{char['name']}正在思考怎么回你...）"}
    except Exception:
        return {"reply": f"（{char['name']}暂时掉线了，等一下再来找他吧~）"}


# ===== 追剧笔记 =====

@router.post("/episodes/{episode_id}/notes")
def create_note(
    episode_id: int,
    user_id: str = Body(...),
    text: str = Body(...),
    time_sec: float = Body(default=0),
    drama_id: int = Body(default=0),
):
    """播放中一键标记高能时刻+吐槽"""
    if not text.strip() or len(text) > 300:
        raise HTTPException(status_code=400, detail="笔记 1-300 字")
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    from datetime import datetime
    cursor.execute(
        "INSERT INTO user_notes (user_id, episode_id, drama_id, note_text, time_sec) VALUES (?,?,?,?,?)",
        (uid, episode_id, drama_id, text.strip(), time_sec),
    )
    conn.commit()
    new_id = cursor.lastrowid
    conn.close()
    return {"ok": True, "id": new_id}


@router.get("/episodes/{episode_id}/notes")
def get_episode_notes(episode_id: int, user_id: str = Query(...)):
    """获取用户在某集的所有笔记"""
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    cursor.execute(
        "SELECT id, note_text, time_sec, created_at FROM user_notes WHERE episode_id = ? AND user_id = ? ORDER BY time_sec",
        (episode_id, uid),
    )
    rows = cursor.fetchall()
    conn.close()
    return [
        {"id": r["id"], "text": r["note_text"], "time_sec": r["time_sec"], "created_at": r["created_at"]}
        for r in rows
    ]


@router.get("/user/notes")
def get_user_all_notes(user_id: str = Query(...), limit: int = Query(default=50, ge=1, le=200)):
    """获取用户所有追剧笔记（跨集聚合）"""
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    cursor.execute("""
        SELECT un.id, un.note_text, un.time_sec, un.created_at, un.episode_id, e.episode_num, d.title as drama_title
        FROM user_notes un
        JOIN episodes e ON un.episode_id = e.episode_id
        JOIN dramas d ON e.drama_id = d.id
        WHERE un.user_id = ?
        ORDER BY un.created_at DESC
        LIMIT ?
    """, (uid, limit))
    rows = cursor.fetchall()
    conn.close()
    return [
        {
            "id": r["id"], "text": r["note_text"], "time_sec": r["time_sec"],
            "created_at": r["created_at"], "episode_id": r["episode_id"],
            "episode_num": r["episode_num"], "drama_title": r["drama_title"],
        }
        for r in rows
    ]


@router.get("/user/notes/count")
def get_user_notes_count(user_id: str = Query(...)):
    """统计用户的追剧笔记总数"""
    conn = get_connection()
    cursor = conn.cursor()
    uid = user_id
    cursor.execute("SELECT COUNT(*) as cnt FROM user_notes WHERE user_id = ?", (uid,))
    row = cursor.fetchone()
    conn.close()
    return {"count": row["cnt"] if row else 0}


@router.delete("/notes/{note_id}")
def delete_note(note_id: int):
    """删除某条笔记"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("DELETE FROM user_notes WHERE id = ?", (note_id,))
    deleted = cursor.rowcount
    conn.commit()
    conn.close()
    if deleted == 0:
        raise HTTPException(status_code=404, detail="笔记不存在")
    return {"ok": True}


# ===== 剧情分支投票 =====

@router.get("/dramas/{drama_id}/branch-vote")
def get_branch_vote(drama_id: int, user_id: str = Query(default="")):
    """获取剧集的分支投票题目+实时票数"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM branch_votes WHERE drama_id = ?", (drama_id,))
    vote = cursor.fetchone()
    if not vote:
        conn.close()
        return {"ok": True, "vote": None}

    cursor.execute("SELECT choice, COUNT(*) as cnt FROM branch_vote_records WHERE vote_id = ? GROUP BY choice", (vote["id"],))
    counts = {"a": 0, "b": 0}
    for r in cursor.fetchall():
        counts[r["choice"]] = r["cnt"]
    total = counts["a"] + counts["b"]

    my_choice = None
    if user_id:
        uid = user_id
        cursor.execute("SELECT choice FROM branch_vote_records WHERE vote_id = ? AND user_id = ?", (vote["id"], uid))
        rec = cursor.fetchone()
        if rec:
            my_choice = rec["choice"]
    conn.close()

    # 检查是否过期
    from datetime import datetime
    expired = False
    if vote["deadline"]:
        try:
            expired = datetime.now() > datetime.fromisoformat(vote["deadline"])
        except Exception:
            pass

    return {
        "ok": True,
        "vote": {
            "id": vote["id"],
            "drama_id": vote["drama_id"],
            "question": vote["question"],
            "option_a": vote["option_a"],
            "option_b": vote["option_b"],
            "deadline": vote["deadline"],
            "expired": expired,
            "total": total,
            "counts": counts,
            "my_choice": my_choice,
        },
    }


@router.post("/dramas/{drama_id}/branch-vote")
def cast_branch_vote(drama_id: int, user_id: str = Body(...), choice: str = Body(...)):
    """对分支投票进行投票"""
    if choice not in ("a", "b"):
        raise HTTPException(status_code=400, detail="choice 只能是 a 或 b")
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("SELECT id, deadline FROM branch_votes WHERE drama_id = ?", (drama_id,))
    vote = cursor.fetchone()
    if not vote:
        conn.close()
        raise HTTPException(status_code=404, detail="该剧集没有分支投票")

    # 检查是否过期
    from datetime import datetime
    if vote["deadline"]:
        try:
            if datetime.now() > datetime.fromisoformat(vote["deadline"]):
                conn.close()
                return {"ok": False, "error": "投票已截止"}
        except Exception:
            pass

    uid = user_id
    cursor.execute(
        "INSERT OR REPLACE INTO branch_vote_records (vote_id, user_id, choice) VALUES (?,?,?)",
        (vote["id"], uid, choice),
    )
    conn.commit()

    cursor.execute("SELECT choice, COUNT(*) as cnt FROM branch_vote_records WHERE vote_id = ? GROUP BY choice", (vote["id"],))
    counts = {"a": 0, "b": 0}
    for r in cursor.fetchall():
        counts[r["choice"]] = r["cnt"]
    conn.close()
    return {"ok": True, "counts": counts}
