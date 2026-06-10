from typing import List, Optional, Any, Dict
from pydantic import BaseModel, Field


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
    description: Optional[str] = None
    cover_url: str = ""
    tags: List[str] = []
    episodes: List[EpisodeBrief] = []


class HighlightItem(BaseModel):
    id: int
    episode_id: int
    time: float
    type: str
    title: str
    widget_type: str = "emoji"
    options: Optional[List[str]] = None
    emotion_hints: Optional[List[str]] = None
    duration: int = 15


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
    llm_available: bool = False


# ===== 互动模块 Schema =====
class InteractionRecord(BaseModel):
    id: int
    user_id: str
    episode_id: int
    highlight_id: Optional[int] = None
    module_id: str
    interaction_data: Optional[dict] = None
    created_at: Optional[str] = None


class InteractionStats(BaseModel):
    total_count: int
    by_module: dict[str, int] = {}
    by_emotion: dict[str, int] = {}


class InteractionDetail(BaseModel):
    episode_id: int
    user_id: str
    total: int
    stats: InteractionStats


# ===== 用户画像 Schema =====
class WatchHistoryItem(BaseModel):
    episode_id: int
    drama_id: int
    drama_title: str = ""
    episode_num: int = 0
    progress: int = 0
    watched: int = 0


class UserProfileResponse(BaseModel):
    user_id: str
    watch_history: List[WatchHistoryItem] = []
    interaction_stats: dict[str, int] = {}
    favorite_dramas: List[int] = []
    preferences: dict = {}


class UserProfileUpdate(BaseModel):
    user_id: str
    favorite_dramas: Optional[List[int]] = None
    preferences: Optional[dict] = None


# ===== Agent 模块 Schema =====
class PageContext(BaseModel):
    page_type: str = Field(default="home", description="当前页面类型: home/drama_list/playback/comment/profile")
    drama_id: int = Field(default=0, description="当前所在剧集ID")
    episode_id: int = Field(default=0, description="当前所在集数ID")
    episode_num: int = Field(default=0, description="当前集数号")
    playback_progress: float = Field(default=0.0, description="当前播放时间秒")
    drama_title: str = Field(default="", description="当前剧集标题")


class AgentChatRequest(BaseModel):
    user_id: str = Field(..., description="设备/用户唯一标识")
    message: str = Field(..., description="用户输入消息")
    context: Optional[Dict[str, Any]] = Field(default=None, description="当前观剧上下文")
    history: Optional[List[Dict[str, str]]] = Field(default=None, description="历史对话")
    page_context: Optional[PageContext] = Field(default=None, description="当前页面上下文感知信息")


class StoryExtensionRequest(BaseModel):
    drama_title: str
    drama_desc: str
    latest_episodes: List[str]
    user_preferences: Optional[List[str]] = None


class GenerateHighlightsRequest(BaseModel):
    drama_title: str
    episode_transcript: str
    episode_duration: float


# ===== 小墨Agent全局对话会话 Schema =====
class ChatSession(BaseModel):
    id: int
    user_id: str
    title: str
    drama_id: int = 0
    created_at: str = ""
    updated_at: str = ""


class ChatMessageItem(BaseModel):
    id: int
    session_id: int
    role: str
    content: str
    created_at: str = ""


class CreateSessionRequest(BaseModel):
    user_id: str = ""
    drama_id: int = 0
    title: str = "新对话"


class AppendMessageRequest(BaseModel):
    session_id: int
    role: str
    content: str
