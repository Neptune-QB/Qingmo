import json

from fastapi import APIRouter, HTTPException, Query, Body
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from app.database import get_connection
from app.config import settings
from app.schemas import (
    DramaBrief, DramaDetail, EpisodeBrief,
    PlaybackInfo, HighlightItem, HealthResponse,
)
from app.services.llm_service import llm_service

router = APIRouter()


# ===== Pydantic 请求模型 =====
class AgentChatRequest(BaseModel):
    user_id: str = Field(..., description="设备/用户唯一标识")
    message: str = Field(..., description="用户输入消息")
    context: Optional[Dict[str, Any]] = Field(default=None, description="当前观剧上下文")
    history: Optional[List[Dict[str, str]]] = Field(default=None, description="历史对话")


class StoryExtensionRequest(BaseModel):
    drama_title: str
    drama_desc: str
    latest_episodes: List[str]
    user_preferences: Optional[List[str]] = None


class GenerateHighlightsRequest(BaseModel):
    drama_title: str
    episode_transcript: str
    episode_duration: float


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
    """小墨 Agent 对话接口，流式返回"""
    # 清洗用户输入，防止 Prompt 注入
    safe_message = req.message.replace("\n", "\\n")
    safe_context = dict(req.context) if req.context else None

    async def generator():
        async for chunk in llm_service.chat(
            user_message=safe_message,
            history=req.history,
            drama_context=safe_context
        ):
            yield chunk.encode("utf-8")

    return StreamingResponse(generator(), media_type="text/plain")


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


@router.get("/dramas", response_model=list[DramaBrief])
def get_dramas():
    conn = get_connection()
    cursor = conn.cursor()
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
    highlights = [
        HighlightItem(
            id=h["id"], episode_id=h["episode_id"], time=h["time"],
            type=h["type"], title=h["title"], widget_type=h["widget_type"],
            options=h["options"].split(",") if h["options"] else None,
        ) for h in cursor.fetchall()
    ]

    conn.close()
    return PlaybackInfo(
        episode_id=ep["episode_id"], video_url=ep["video_url"],
        duration=ep["duration"], highlights=highlights,
    )


@router.post("/progress")
def report_progress(episode_id: int = Query(...), progress: int = Query(...)):
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
