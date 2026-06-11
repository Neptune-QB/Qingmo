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
    fav_count: int = 0


class DramaHighlight(BaseModel):
    id: Optional[int] = None
    drama_id: int
    episode_id: int
    highlight_type: str
    start_time_ms: int
    end_time_ms: int
    hint_offset_ms: int = 2000
    title: str
    description: Optional[str] = None
    interaction_type: str
    interaction_config: dict
    xiaomo_gif_code: str
    priority: int = 0
    status: str = "enabled"
    source_type: str = "manual"
    confidence: Optional[float] = None
    evidence_json: Optional[str] = None
    review_status: Optional[str] = "approved"
    bubble_text: str = ""
    created_at: Optional[str] = None
    updated_at: Optional[str] = None


class PlaybackInfo(BaseModel):
    episode_id: int
    video_url: str
    duration: int = 0
    highlights: List[DramaHighlight] = []


class EpisodePlayInfo(BaseModel):
    episode_id: int
    drama_id: int
    title: str
    duration_ms: int
    highlights: List[DramaHighlight] = []


class ProgressReport(BaseModel):
    episode_id: int
    progress: int
    user_id: str = "0"


class HealthResponse(BaseModel):
    status: str
    service: str
    llm_available: bool = False


# ===== 用户画像 Schema =====
class WatchHistoryItem(BaseModel):
    episode_id: int
    drama_id: int
    drama_title: str = ""
    cover_url: str = ""
    episode_num: int = 0
    progress: int = 0
    watched: int = 0
    total_episodes: int = 0


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


# ===== 用户互动上报 Schema =====
class InteractionReport(BaseModel):
    user_id: Optional[str] = None
    device_id: Optional[str] = None
    drama_id: int
    episode_id: int
    highlight_id: int
    interaction_type: str
    option_key: Optional[str] = None
    option_label: Optional[str] = None


class InteractionStatsItem(BaseModel):
    option_key: Optional[str] = None
    option_label: Optional[str] = None
    count: int
    percent: float


class InteractionStats(BaseModel):
    highlight_id: int
    total_count: int
    options: List[InteractionStatsItem] = []


# ===== 小墨 GIF 动效 Schema =====
class XiaoMoGif(BaseModel):
    id: int
    code: str
    name: str
    gif_url: str
    highlight_type: Optional[str] = None
    description: str = ""
    status: str = "published"
    created_at: str = ""
    updated_at: str = ""


class XiaoMoGifCreate(BaseModel):
    code: str
    name: str
    gif_url: str
    highlight_type: Optional[str] = None
    description: str = ""
    status: str = "published"
