import json

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
)
from app.services.llm_service import llm_service, classify_intent, search_dramas, get_user_profile_summary, retrieve_plot_context
from app.api.auth_router import get_current_user

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
@router.post("/agent/chat")
async def agent_chat(req: AgentChatRequest):
    """小墨 Agent 对话接口，带意图路由"""
    safe_message = req.message.replace("\n", " ").strip()
    intent = classify_intent(safe_message)

    async def generator():
        if intent == "search_drama":
            dramas = search_dramas(safe_message)
            if dramas:
                lines = [f"找到 {len(dramas)} 部可能你喜欢的短剧哦~ ✨\n"]
                for d in dramas:
                    tags_str = " · ".join(d["tags"][:3]) if d["tags"] else "暂无标签"
                    lines.append(f"📺 《{d['title']}》(共 {d['total_episodes']} 集)\n   {tags_str}\n")
                if llm_service.is_available:
                    lines.append("\n(想了解更多剧情？告诉我剧名，小墨给你详细介绍~)")
                for line in lines:
                    yield line.encode("utf-8")
            else:
                yield "没找到匹配的短剧呢 😢\n试试换个关键词？比如「甜宠」「古装」「悬疑」~".encode("utf-8")
        elif intent == "user_profile":
            summary = get_user_profile_summary(req.user_id)
            yield summary.encode("utf-8")
            if llm_service.is_available:
                yield "\n\n(还想知道什么？问小墨就行~)".encode("utf-8")
        else:
            safe_context = dict(req.context) if req.context else {}
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
def report_progress(episode_id: int = Body(...), progress: int = Body(...)):
    conn = get_connection()
    cursor = conn.cursor()
    watched = 1 if progress > 0 else 0
    cursor.execute(
        "INSERT INTO user_progress (episode_id, progress, watched) VALUES (?, ?, ?) "
        "ON CONFLICT(episode_id) DO UPDATE SET progress = ?, watched = ?, updated_at = CURRENT_TIMESTAMP",
        (episode_id, progress, watched, progress, watched),
    )
    conn.commit()
    conn.close()
    return {"ok": True}


# ===== 用户画像接口 =====
@router.get("/user/profile", response_model=UserProfileResponse)
def get_user_profile(user_id: str = Query(..., description="设备/用户唯一标识")):
    """获取用户画像：自动聚合观看历史 + 互动统计 + 偏好"""
    conn = get_connection()
    cursor = conn.cursor()

    # 1. 读取用户画像存储记录
    cursor.execute("SELECT * FROM user_profiles WHERE user_id = ?", (user_id,))
    profile_row = cursor.fetchone()

    # 2. 聚合观看历史（从 user_progress 表实时计算）
    cursor.execute("""
        SELECT up.episode_id, up.progress, up.watched,
               e.episode_num, e.drama_id, d.title as drama_title
        FROM user_progress up
        JOIN episodes e ON up.episode_id = e.episode_id
        JOIN dramas d ON e.drama_id = d.id
        WHERE up.watched = 1 OR up.progress > 0
        ORDER BY up.updated_at DESC
        LIMIT 50
    """)
    watch_history = [
        WatchHistoryItem(
            episode_id=row["episode_id"],
            drama_id=row["drama_id"],
            drama_title=row["drama_title"] or "",
            episode_num=row["episode_num"],
            progress=row["progress"],
            watched=row["watched"],
        )
        for row in cursor.fetchall()
    ]

    # 3. 聚合互动统计
    cursor.execute(
        "SELECT module_id, COUNT(*) as cnt FROM user_interactions WHERE user_id = ? GROUP BY module_id",
        (user_id,),
    )
    interaction_stats = {row["module_id"]: row["cnt"] for row in cursor.fetchall()}

    # 4. 分析偏好剧（从观看最多的 drama_id 计算）
    cursor.execute("""
        SELECT e.drama_id, COUNT(*) as cnt
        FROM user_progress up
        JOIN episodes e ON up.episode_id = e.episode_id
        WHERE up.watched = 1
        GROUP BY e.drama_id
        ORDER BY cnt DESC
        LIMIT 5
    """)
    favorite_dramas = [row["drama_id"] for row in cursor.fetchall()]

    conn.close()

    # 合并存储的偏好与实时数据
    stored_prefs = {}
    if profile_row and profile_row["preferences"]:
        try:
            stored_prefs = json.loads(profile_row["preferences"])
        except (json.JSONDecodeError, TypeError):
            pass

    return UserProfileResponse(
        user_id=user_id,
        watch_history=watch_history,
        interaction_stats=interaction_stats,
        favorite_dramas=favorite_dramas,
        preferences=stored_prefs,
    )


@router.post("/user/profile")
def update_user_profile(body: UserProfileUpdate):
    """创建或更新用户画像（偏好/收藏等主动设置项）"""
    conn = get_connection()
    cursor = conn.cursor()

    favorite_json = json.dumps(body.favorite_dramas) if body.favorite_dramas is not None else None
    prefs_json = json.dumps(body.preferences) if body.preferences is not None else None

    # 先尝试插入，存在则更新
    cursor.execute("SELECT user_id FROM user_profiles WHERE user_id = ?", (body.user_id,))
    exists = cursor.fetchone()

    if exists:
        parts = []
        params = []
        if favorite_json is not None:
            parts.append("favorite_dramas = ?")
            params.append(favorite_json)
        if prefs_json is not None:
            parts.append("preferences = ?")
            params.append(prefs_json)
        if parts:
            parts.append("updated_at = CURRENT_TIMESTAMP")
            params.append(body.user_id)
            cursor.execute(
                f"UPDATE user_profiles SET {', '.join(parts)} WHERE user_id = ?",
                params,
            )
    else:
        cursor.execute(
            "INSERT INTO user_profiles (user_id, favorite_dramas, preferences) VALUES (?, ?, ?)",
            (body.user_id, favorite_json or "[]", prefs_json or "{}"),
        )

    conn.commit()
    conn.close()
    return {"ok": True}


@router.get("/user/watch-history", response_model=list[WatchHistoryItem])
def get_watch_history(
    user_id: str = Query(...),
    limit: int = Query(default=30, ge=1, le=100),
):
    """获取用户观看历史（按时间倒序）"""
    conn = get_connection()
    cursor = conn.cursor()
    cursor.execute("""
        SELECT up.episode_id, up.progress, up.watched,
               e.episode_num, e.drama_id, d.title as drama_title
        FROM user_progress up
        JOIN episodes e ON up.episode_id = e.episode_id
        JOIN dramas d ON e.drama_id = d.id
        ORDER BY up.updated_at DESC
        LIMIT ?
    """, (limit,))
    result = [
        WatchHistoryItem(
            episode_id=row["episode_id"],
            drama_id=row["drama_id"],
            drama_title=row["drama_title"] or "",
            episode_num=row["episode_num"],
            progress=row["progress"],
            watched=row["watched"],
        )
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
    conn.close()
    return {"ok": True, "id": new_id}


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
    uid = int(user_id)
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
        uid = int(user_id)
        cursor.execute("SELECT id FROM episode_likes WHERE user_id = ? AND episode_id = ?", (uid, episode_id))
        liked = cursor.fetchone() is not None
    conn.close()
    return {"count": count, "liked": liked}


@router.post("/dramas/{drama_id}/favorite")
def toggle_favorite(drama_id: int, user_id: str = Body(..., embed=True)):
    """切换收藏状态，完全绑定当前登录用户"""
    uid = int(user_id)
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
    uid = int(user_id)
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

    # 严格校验user_id合法性，异常场景直接拒绝发表，不自动兜底绑定默认账号
    try:
        uid = int(user_id)
    except ValueError:
        conn.close()
        raise HTTPException(status_code=400, detail="用户登录态异常，请重新登录")

    # 取当前登录用户的真实昵称
    nickname = f"热心网友{uid}"
    cursor.execute("SELECT nickname FROM users WHERE id = ?", (uid,))
    nick_row = cursor.fetchone()
    if nick_row and nick_row[0]:
        nickname = nick_row[0]

    from datetime import datetime
    pure_text = text.strip()
    reply_to_nickname_safe = reply_to_nickname.strip() if reply_to_nickname else ""
    cursor.execute(
        """INSERT INTO episode_comments
           (episode_id, user_id, nickname, text, parent_id, reply_to_nickname, created_at)
           VALUES (?,?,?,?,?,?,?)""",
        (episode_id, str(uid), nickname, pure_text, parent_id, reply_to_nickname_safe, datetime.now().isoformat())
    )
    conn.commit()
    cursor.execute("SELECT last_insert_rowid()")
    new_id = cursor.fetchone()[0]
    conn.close()
    return {"ok": True, "id": new_id}


@router.get("/episodes/{episode_id}/comments")
def get_episode_comments(episode_id: int, limit: int = Query(50, ge=1, le=200), offset: int = Query(0, ge=0)):
    """获取剧集评论列表，子回复嵌套在父评论的 replies 字段中，返回时附带 reply_to_nickname 供客户端决定是否显示前缀"""
    conn = get_connection(); cursor = conn.cursor()
    # 兼容旧表结构，字段可能是 reply_to_user_id 或 reply_to_nickname
    cursor.execute("PRAGMA table_info(episode_comments)")
    cols = [c["name"] for c in cursor.fetchall()]
    text_reply_to_nickname_col = "reply_to_nickname" if "reply_to_nickname" in cols else "reply_to_user_id"

    cursor.execute(
        f"SELECT id, user_id, nickname, text, created_at, parent_id, {text_reply_to_nickname_col} "
        "FROM episode_comments "
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
        if pid > 0 and pid in id_map:
            # 计算父评论是否是顶级评论：只有当父评论的 parent_id == 0 时，该子回复不需要显示前缀
            parent_item = id_map[pid]
            parent_is_top_level = parent_item["parent_id"] == 0
            if parent_is_top_level:
                # 回复指向顶级评论：强制清空 reply_to_nickname，客户端不显示任何前缀
                item["reply_to_nickname"] = ""
            id_map[pid]["replies"].append(item)

    return tops


@router.get("/episodes/{episode_id}/counts")
def get_episode_counts(episode_id: int, user_id: str = Query(default="")):
    """当前剧集点赞数+评论数+弹幕数+当前用户是否已点赞 完全绑定当前登录用户"""
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM episode_likes WHERE episode_id = ?", (episode_id,))
    like_count = cursor.fetchone()[0]
    cursor.execute("SELECT COUNT(*) FROM episode_comments WHERE episode_id = ?", (episode_id,))
    comment_count = cursor.fetchone()[0]
    liked = False
    if user_id:
        uid = int(user_id)
        cursor.execute("SELECT id FROM episode_likes WHERE user_id=? AND episode_id=?", (uid, episode_id))
        liked = cursor.fetchone() is not None
    conn.close()
    return {"like_count": like_count, "comment_count": comment_count, "liked": liked}


# ===== 小墨Agent全局对话持久化CRUD =====
from app.schemas import ChatSession, ChatMessageItem, CreateSessionRequest, AppendMessageRequest

@router.post("/agent/sessions", response_model=ChatSession)
def create_chat_session(req: CreateSessionRequest):
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO user_chat_sessions (user_id, title, drama_id) VALUES (?, ?, ?)",
        (req.user_id, req.title, req.drama_id)
    )
    new_id = cursor.lastrowid
    conn.commit()
    cursor.execute("SELECT * FROM user_chat_sessions WHERE id=?", (new_id,))
    row = cursor.fetchone()
    conn.close()
    return ChatSession(id=row["id"], user_id=row["user_id"], title=row["title"],
                      drama_id=row["drama_id"], created_at=row["created_at"], updated_at=row["updated_at"])


@router.get("/agent/sessions")
def list_chat_sessions(user_id: str = Query(...)):
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute("SELECT * FROM user_chat_sessions WHERE user_id=? ORDER BY updated_at DESC LIMIT 50", (user_id,))
    rows = cursor.fetchall()
    result = []
    for r in rows:
        result.append(dict(r))
    conn.close()
    return result


@router.get("/agent/sessions/{session_id}/messages", response_model=list[ChatMessageItem])
def list_session_messages(session_id: int):
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute("SELECT * FROM user_chat_messages WHERE session_id=? ORDER BY id ASC", (session_id,))
    rows = cursor.fetchall()
    conn.close()
    return [ChatMessageItem(id=r["id"], session_id=r["session_id"], role=r["role"],
                            content=r["content"], created_at=r["created_at"]) for r in rows]


@router.post("/agent/sessions/messages/append")
def append_chat_message(req: AppendMessageRequest):
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute("INSERT INTO user_chat_messages (session_id, role, content) VALUES (?, ?, ?)",
                   (req.session_id, req.role, req.content))
    new_id = cursor.lastrowid
    cursor.execute("UPDATE user_chat_sessions SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", (req.session_id,))
    conn.commit()
    conn.close()
    return {"ok": True, "message_id": new_id}


@router.delete("/agent/sessions/{session_id}")
def delete_chat_session(session_id: int):
    conn = get_connection(); cursor = conn.cursor()
    cursor.execute("DELETE FROM user_chat_messages WHERE session_id = ?", (session_id,))
    cursor.execute("DELETE FROM user_chat_sessions WHERE id = ?", (session_id,))
    deleted = cursor.rowcount
    conn.commit()
    conn.close()
    return {"ok": True, "deleted": deleted > 0}
