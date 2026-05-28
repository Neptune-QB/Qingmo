from typing import List, Optional
from pydantic import BaseModel


class DramaBrief(BaseModel):
    id: int
    title: str
    cover_url: str = ""
    tags: List[str] = []
    total_episodes: int = 0


class EpisodeBrief(BaseModel):
    episode_id: int
    episode_num: int
    title: Optional[str] = None
    duration: int = 0
    thumbnail_url: Optional[str] = None


class DramaDetail(BaseModel):
    id: int
    title: str
    author: Optional[str] = None
    description: Optional[str] = None
    cover_url: str = ""
    tags: List[str] = []
    episodes: List[EpisodeBrief] = []


class HighlightItem(BaseModel):
    id: int
    episode_id: int
    time: int
    type: str
    title: str
    widget_type: str = "emoji"
    options: Optional[List[str]] = None


class PlaybackInfo(BaseModel):
    episode_id: int
    video_url: str
    duration: int = 0
    highlights: List[HighlightItem] = []


class ProgressReport(BaseModel):
    episode_id: int
    progress: int


class HealthResponse(BaseModel):
    status: str
    service: str
