# 青墨短剧 — 小墨 Agent

> 青墨短剧平台 · AI 观剧助手 · 2026 AI 全栈比赛项目

「小墨」是青墨短剧平台的 AI 观剧助手，以拟人化 GIF 形象常驻播放界面，在高光时刻主动触发互动（情绪按钮/二选一面板/反应面板），播放间隙发送个性化弹幕陪伴，并通过 RAG 检索真实剧情数据回答用户关于角色、情节的问题。

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 客户端 | Kotlin · Jetpack Compose · ExoPlayer (Media3) · ViewPager2 · Retrofit · Coil |
| 服务端 | Python 3.11 · FastAPI · Uvicorn · SQLite |
| AI 模型 | Doubao-Seed-2.0-lite / DeepSeek V4 Flash（OpenAI 兼容接口，配置切换） |
| 数据层 | SQLite (`ju_flash.db`) · 21 张生产表 · 全量真实数据 |
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

### 直接安装

项目根目录提供 `qingmo.apk`，传输到 Android 手机直接安装即可使用。

> ⚠️ 网络要求：APK 默认连接 `http://192.168.43.69:8000/`（手机热点模式下 PC 的 IP）。
> 1. 手机开启「便携式 WLAN 热点」
> 2. PC 连接该热点
> 3. PC 启动后端 `python main.py`
> 4. 手机打开青墨 App
>
> 如使用路由器 WiFi，PC IP 会变化，需重新编译 APK 并修改 `local.properties` 中的 `qingmo.apiBaseUrl`。

### 环境要求

- 服务端：Python 3.11+、pip
- 客户端：Android Studio、JDK 11、Gradle 8.7+
- 数据库：SQLite（零配置，文件即数据库）
- 默认用户：`test` / `123456`（可直接登录，也可注册新账号）

### 1. 启动后端

```bash
cd backend
pip install -r requirements.txt
cp .env.example .env    # 填入 LLM 配置（见下方）
python -m uvicorn main:app --host 0.0.0.0 --port 8000
# http://127.0.0.1:8000/docs 自动生成 API 文档
```

### Agent 接口 LLM 配置

Agent 支持 Doubao 和 DeepSeek 两种 LLM，通过 `.env` 文件切换：

```env
# 选择 provider: doubao / deepseek
LLM_PROVIDER=deepseek

# DeepSeek
DEEPSEEK_API_KEY=sk-xxx
DEEPSEEK_BASE_URL=https://api.deepseek.com/v1
DEEPSEEK_MODEL=deepseek-chat

# Doubao
DOUBAO_API_KEY=your-access-key
DOUBAO_EP_ID=ep-xxxx
DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
```

`LLM_PROVIDER` 决定使用哪个厂商，只需填对应的一组密钥即可。

### 2. 准备视频数据

视频文件存放于 `backend/crawler/data/videos/`，按剧集 ID 分目录：

```
backend/crawler/data/videos/
├── 1/   # 北派寻宝笔记
├── 2/   # 天下第一纨绔 (含 AIGC 分支)
├── 3/   # 十八岁太奶奶
├── 4/   # 幸得相遇别离时
├── 5/   # 荒年全村啃树皮 (暂无资源)
├── ...
└── 10/  # 北往 (暂无资源)
```

> 前 4 部剧集视频资源通过百度网盘分发，下载后解压到 `backend/crawler/data/videos/` 目录即可。
> 后 6 部暂无视频资源，播放页会显示「视频资源缺失」。
> 
> 链接: https://pan.baidu.com/s/1nARYVyDOefBJBqVXOc2aOw?pwd=h3qx 提取码: h3qx

### 3. 编译 Android

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
| `episodes` | 233 集，含 AIGC 分支 |
| `danmaku` | 17.6 万条弹幕 |
| `drama_highlight` | 高光点 (support/reaction/choice) |
| `episode_comments` | 评论 (楼中楼平级) |
| `episode_likes` | 点赞 |
| `user_favorites` | 收藏 |
| `user_progress` | 播放进度实时上报 |
| `user_notes` | 追剧笔记 |
| `user_interaction` | 互动上报 |
| `drama_summaries` | 每集 AI 剧情摘要 (RAG) |
| `drama_characters` | 角色人设 (RAG) |
| `drama_timeline` | 事件时间线 (RAG) |
| `episode_transcript` | 台词 (ASR) |
| `episode_scene_segment` | 剧情片段切分 |
| `episode_content_summary` | 集内容摘要 |
| `video_analysis_task` | AI 分析任务 |
| `user_chat_sessions` | 小墨对话会话 |
| `user_chat_messages` | 对话消息持久化 |
| `xiaomo_gif` | 11 种高光 GIF 动效 |
| `users` | 用户认证 |

共 21 张表。

---

## 剧情分析流水线

自动从 MP4 视频提取台词、画面描述、高光点和每集摘要，写入数据库供小墨 RAG 检索。

### 流程

```
MP4 → ffmpeg抽音频 → ASR语音识别 → 角色名修正
    → ffmpeg抽帧(每5s) → 多模态画面描述
    → 10s滑动窗口(2s重叠) → LLM逐窗口分析
    → 后处理(去重/密度控制) → 高光点+摘要+气泡 → SQLite
```

### 单集分析

```bash
python -m tools.video_analysis.analyze_episode \
  --episode-id 1064 \
  --video-path backend/crawler/data/videos/1/64.mp4 \
  --asr-provider funasr --device cuda \
  --correct-asr \
  --vision-provider doubao \
  --force --save
```

| 参数 | 说明 |
|------|------|
| `--episode-id` | 剧集 ID |
| `--video-path` | MP4 文件路径 |
| `--asr-provider` | funasr / whisper |
| `--correct-asr` | 启用 LLM 后纠错 |
| `--vision-provider` | doubao / noop |
| `--force` | 覆盖旧分析数据 |
| `--save` | 写入数据库 |
| `--max-highlights-per-episode` | 每集最多高光点（默认 2） |

### 批量分析

```bash
python -m tools.video_analysis.batch_analyze --save --drama-id 1
```

支持断点续传，单集失败不阻塞其余。

### 目录结构

```text
tools/video_analysis/
├── analyze_episode.py    # 单集入口
├── batch_analyze.py      # 批量入口
├── providers/
│   ├── asr.py            # FunASR + faster-whisper
│   ├── vision.py         # 多模态画面描述
│   └── llm.py            # LLM 调用
├── prompts.py            # System Prompt 模板
├── post_process.py       # 高光后处理
├── db_writer.py          # 数据库写入
└── fill_bubble_text.py   # 气泡文案生成
```
