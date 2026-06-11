# 青墨短剧 — 小墨 Agent

> 青墨短剧平台 · AI 观剧助手 · 2026 AI 全栈比赛项目

「小墨」是青墨短剧平台的 AI 观剧助手，以拟人化 GIF 形象常驻播放界面，在高光时刻主动触发互动（情绪按钮/二选一面板/反应面板），播放间隙发送个性化弹幕陪伴，并通过 RAG 检索真实剧情数据回答用户关于角色、情节的问题。

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 客户端 | Kotlin · Jetpack Compose · ExoPlayer (Media3) · ViewPager2 · Retrofit · Coil |
| 服务端 | Python 3.11 · FastAPI · Uvicorn · SQLite |
| AI 模型 | Doubao-Seed-2.0-lite (火山引擎 Ark · OpenAI 兼容接口) |
| 数据层 | SQLite (`ju_flash.db`) · 15 张生产表 · 全量真实数据 |
| 工具链 | ktlint · Gradle 8.7 |

---

## 项目结构

```
Qingmo/
├── android/                         # Android 客户端
│   └── app/src/main/java/com/qingmo/app/
│       ├── MainActivity.kt          # 单 Activity 入口
│       ├── data/                    # 数据层 (API/Model/Repository/Auth/Progress)
│       ├── ui/                      # UI (导航/列表/详情/播放器/登录/个人中心)
│       ├── xiaomo/                  # 小墨 Agent 核心
│       │   ├── XiaoMoCore.kt        # 状态机 + GIF 调度
│       │   ├── XiaoMoSettings.kt    # 功能开关持久化
│       │   ├── InteractionButton.kt # 高光互动按钮 (PNG 动画)
│       │   ├── ModuleRegistry.kt    # 互动模块注册中心
│       │   ├── modules/             # 互动模块 (Emotion/Choice/Vote/Reaction)
│       │   ├── companion/           # 小墨面板组件
│       │   └── ui/                  # 聊天面板/设置
│       └── res/
│           └── drawable/xiaomo/     # 11 种高光类型 GIF/MOV 动画
├── backend/                         # Python 服务端
│   ├── main.py                      # FastAPI 入口 + 静态文件挂载
│   ├── requirements.txt
│   ├── app/
│   │   ├── api/router.py            # REST API (1650+ 行)
│   │   ├── database.py              # SQLite 建表 + 迁移
│   │   ├── schemas.py               # Pydantic 模型
│   │   └── services/llm_service.py  # LLM + RAG 五层检索
│   └── crawler/data/
│       ├── covers/                  # 10 张封面
│       └── videos/                  # 232 集 MP4
├── data/analysis/                   # 逐集 AI 分析 JSON
├── docs/                            # 项目文档
└── tools/                           # 视频分析/数据迁移/清理脚本
```

---

## 快速开始

### 环境要求

- 服务端：Python 3.11+、pip
- 客户端：Android Studio、JDK 11、Gradle 8.7+
- 数据库：SQLite（零配置，文件即数据库）

### 1. 启动后端

```bash
cd backend
pip install -r requirements.txt
cp .env.example .env    # 填入 DOUBAO_API_KEY
python -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload
# http://127.0.0.1:8000/docs 自动生成 API 文档
```

### 2. 编译 Android

```bash
cd android
# 手机 WiFi 热点模式调试：在 local.properties 添加
# qingmo.apiBaseUrl=http://192.168.43.69:8000/
gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 核心功能

| 模块 | 功能 | 状态 |
|------|------|:--:|
| 短剧列表 | 2 列网格、胶囊搜索、封面加载、下拉刷新 | ✅ |
| 视频播放 | ViewPager2 切集、SeekBar 拖拽高光标记、倍速 | ✅ |
| 弹幕系统 | 5 轨道飘屏、发送/暂停/开关、小墨弹幕独立通道 | ✅ |
| 评论系统 | 楼中楼平级嵌套、@小墨 AI 回复、点赞 | ✅ |
| 点赞收藏 | toggle API + 乐观更新 + 计数同步 | ✅ |
| **高光互动** | 情绪按钮 / 二选一面板 / 反应面板，纯 View 零 Compose | ✅ |
| **小墨 Agent** | 全局悬浮常驻、流式对话、RAG 五层检索、多会话持久化 | ✅ |
| **角色对话** | 选人 → 1v1 人设聊天、LLM 角色口吻、本地降级 | ✅ |
| 用户系统 | JWT 注册/登录、匿名设备兼容、12 表数据合并 | ✅ |
| 个人中心 | 观看历史、收藏列表、昵称编辑、进度概览 | ✅ |
| 搜索 | 胶囊搜索框，剧名/标签实时过滤 | ✅ |
| 小墨 GIF | 11 种高光动效、进入/离开区间自动切换 idle | ✅ |
| 分支视频 | 二选一 → 分支播放 → 结束返回 → 续播原位 | ✅ |

---

## AI / 大模型能力

### 模型选型

| 用途 | 模型 | 调用方式 |
|------|------|------|
| **小墨 Agent 对话** | Doubao-Seed-2.0-lite | OpenAI 兼容接口 (Ark API) |
| **角色人设聊天** | Doubao-Seed-2.0-lite | OpenAI 兼容接口 |
| **高光点/摘要生成** | Doubao-Seed-2.0-lite | OpenAI 兼容接口 + JSON Mode |
| **多模态视频分析** | Doubao-Seed-2.0-lite | 音频转文字 (ASR) + 关键帧 + LLM 综合 |
| **视频生成** | 小云雀 (xyq) | 技能插件调用 |
| **开发辅助** | Trae · Qwen Code · DeepSeek V4 | IDE 插件 + CLI Agent |

### 调用架构

```
Android (OkHttp 流式) ──→ FastAPI ──→ Doubao Ark (OpenAI 兼容)
                                          │
                              POST /api/v1/agent/chat (流式)
                              POST /api/v1/characters/{id}/chat
                              POST /api/v1/agent/story-extension
```

- **base_url**: `https://ark.cn-beijing.volces.com/api/v3`
- **model**: EP ID (`ep-20260514111117-s7m8b`)
- **SDK**: `openai.AsyncOpenAI`（非 `volcenginesdkarkruntime`）
- 流式对话**不用** `async with`，非流式**必须用** `async with` 关闭连接
- LLM 调用统一 60s 超时，角色对话 15s 超时并配有本地降级回复

### Prompt 设计

小墨 Agent 对话采用**分层 Prompt 注入**：

1. **System Prompt**: 角色设定（活泼可爱、追剧上头）、禁止输出链接
2. **页面上下文**: 当前观看的剧集、播放进度（由 Android `dramaContext` 传入）
3. **RAG 检索结果**: 五层关键词检索（角色 → 事件 → 弹幕 → 高光 → 摘要），注入为 system message，强制 LLM 基于真实数据回答
4. **用户消息**: 原始输入，最短路径进入 LLM

意图路由为纯关键词匹配（零 LLM 依赖）：
- `search_drama`：找剧/推荐 → SQL 模糊搜索 → LLM 润色推荐文案
- `user_profile`：我的记录 → DB 聚合统计
- `llm_chat`：聊剧情 → RAG 增强 LLM 对话

### RAG 五层检索

```
用户提问 "那个男主反水的情节"
  → 1. drama_characters (角色名匹配)
  → 2. drama_timeline (事件关键词匹配)
  → 3. danmaku (观众弹幕热议匹配)
  → 4. drama_highlight (高光标题匹配)
  → 5. drama_summaries (摘要兜底)
  ⇒ 合并注入 LLM context
```

### 内容审核与降级

- 豆包对盗墓、志怪类题材存在静默拦截（不报错、不返回）
- 已为角色对话添加**本地降级回复池**（随机模板话术）
- 批量 LLM 任务中每剧独立 try/catch，单剧失败不阻塞其他剧
- 高光点目前走手工编排种子数据，LLM 生成通过 `POST /admin/highlights` 管理接口写入

---

## API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/dramas` | 短剧列表 |
| GET | `/api/v1/dramas/{id}` | 短剧详情 |
| GET | `/api/v1/playback/{episode_id}` | 播放信息 + 高光点 |
| POST | `/api/v1/progress` | 上报播放进度 |
| GET/POST | `/api/v1/danmaku/{episode_id}` | 弹幕拉取/发送 |
| GET/POST | `/api/v1/episodes/{id}/comments` | 评论列表/发表 |
| POST | `/api/v1/episodes/{id}/like` | 点赞 toggle |
| POST | `/api/v1/dramas/{id}/favorite` | 收藏 toggle |
| POST | `/api/v1/agent/chat` | 小墨流式对话 (RAG) |
| GET/POST/DELETE | `/api/v1/agent/sessions` | 对话会话 CRUD |
| GET/POST | `/api/v1/characters/{id}/chat` | 角色对话 |
| GET/POST | `/api/v1/highlights/{id}/vote` | 剧情投票 |
| GET/POST | `/api/v1/dramas/{id}/branch-vote` | 分支投票 |
| POST | `/api/v1/interactions` | 互动上报 |
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/login` | 登录 |

---

## 数据库

| 表 | 说明 |
|---|---|
| `dramas` | 10 部短剧，/ 分隔标签 |
| `episodes` | 232 集，100% 真实 MP4 |
| `danmaku` | 17.6 万条弹幕 |
| `drama_highlight` | 高光点 (support/reaction/choice) |
| `highlight_choices` | 二选一配置独立表 |
| `episode_comments` | 评论 (平级嵌套) |
| `episode_likes` | 点赞 |
| `user_favorites` | 收藏 |
| `user_progress` | 播放进度实时上报 |
| `drama_summaries` | 每集 AI 剧情摘要 (RAG) |
| `drama_characters` | 角色人设 (RAG) |
| `drama_timeline` | 事件时间线 (RAG) |
| `user_chat_sessions` | 小墨对话会话 |
| `user_chat_messages` | 对话消息持久化 |
| `users` | 用户认证 |

共 15 张生产表，零 mock 数据。

---

## 文档

| 文档 | 内容 |
|------|------|
| [PRD](docs/青墨短剧_小墨Agent_PRD_V1.0.md) | 产品需求文档 |
| [架构](docs/ARCHITECTURE.md) | 系统架构 + 数据流 |
| [API](docs/API.md) | 完整接口参考 |
| [数据库](docs/DATABASE.md) | 表结构 + 迁移策略 |
| [排期](docs/青墨短剧_小墨Agent_20260521-0610开发排期_V1.0.md) | 21 天开发计划 |
