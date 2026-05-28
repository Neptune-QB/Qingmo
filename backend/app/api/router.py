from fastapi import APIRouter, HTTPException, Query
from app.database import get_connection
from app.config import settings
from app.schemas import (
    DramaBrief, DramaDetail, EpisodeBrief,
    PlaybackInfo, HighlightItem, HealthResponse,
)

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health():
    return {"status": "ok", "service": settings.APP_NAME}


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
