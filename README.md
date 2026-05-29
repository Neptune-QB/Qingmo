# 青墨短剧 — 小墨 Agent

> 青墨短剧平台 · AI 观剧助手 · AI 全栈比赛项目

「小墨」是青墨短剧平台的 AI 观剧助手，以拟人化角色形象常驻播放界面侧边，在高光时刻主动触发互动，提供情绪陪伴，并承担短剧资源检索和用户信息管理的 Agent 职责。

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 客户端 | Kotlin · Jetpack Compose · ExoPlayer (Media3 1.2.1) · ViewPager2 · Retrofit 2.9.0 |
| 服务端 | Python 3.11+ · FastAPI 0.115.0 · Uvicorn · SQLite |
| AI 模型 | Doubao-Seed-2.0-lite (火山引擎 Ark) · `volcenginesdkarkruntime` |
| 数据层 | SQLite (`ju_flash.db`) · `pydantic-settings` |
| 工具链 | Playwright · ktlint 1.3.1 · Gradle 8.7 |

---

## 项目结构

```
Qingmo/
├── android/                      # Android 客户端
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/qingmo/app/
│   │   │   │   ├── MainActivity.kt          # 单 Activity 入口
│   │   │   │   ├── data/                    # 数据层
│   │   │   │   │   ├── api/                 # Retrofit API 接口
│   │   │   │   │   ├── model/               # 数据模型
│   │   │   │   │   ├── repository/          # 数据仓库
│   │   │   │   │   └── ProgressCache.kt     # 播放进度缓存
│   │   │   │   └── ui/                      # UI 层
│   │   │   │       ├── navigation/          # 导航图
│   │   │   │       ├── screens/             # 列表 / 详情 / 播放器 三页面
│   │   │   │       └── theme/               # 青白主题色板 + 字体
│   │   │   └── res/xml/network_security_config.xml
│   │   └── build.gradle.kts
│   └── build.gradle.kts
├── backend/                      # Python 服务端
│   ├── main.py                   # FastAPI 入口
│   ├── init_db.py                # 数据库初始化与种子数据
│   ├── requirements.txt
│   ├── .env                      # 环境变量（不纳入版本控制）
│   ├── .env.example              # 环境变量模板
│   ├── app/
│   │   ├── config.py             # Pydantic Settings 配置
│   │   ├── database.py           # SQLite 连接 + 建表 + 迁移
│   │   ├── schemas.py            # Pydantic 数据模型
│   │   ├── api/router.py         # REST API 路由
│   │   └── services/llm_service.py  # Doubao LLM 服务
│   └── crawler/                  # 短剧资源爬虫
│       └── data/
│           ├── covers/           # 10 张封面图
│           └── videos/           # 232 集视频文件
└── docs/                         # 项目文档
    ├── 青墨短剧_小墨Agent_PRD_V1.0.md
    └── 青墨短剧_小墨Agent_20260521-0610开发排期_V1.0.md
```

---

## 快速开始

### 环境要求

- **服务端**：Python 3.11+、pip
- **客户端**：Android Studio Hedgehog+、JDK 11、Gradle 8.7+
- **数据库**：无需安装，SQLite 内嵌

### 1. 启动后端

```bash
cd backend

# 安装依赖
pip install -r requirements.txt

# 配置环境变量（复制模板后修改）
cp .env.example .env
# 编辑 .env 填入真实的 DOUBAO_API_KEY

# 初始化数据库（10 部短剧 + 高光点种子数据）
python init_db.py

# 启动服务
python main.py
# 服务运行在 http://127.0.0.1:8000
# API 文档自动生成：http://127.0.0.1:8000/docs
```

### 2. 启动 Android 客户端

```bash
cd android

# 用 Android Studio 打开项目
# 或命令行编译
gradlew assembleDebug
```

- 包名：`com.qingmo.app`
- 最低 SDK：26 (Android 8.0)
- 目标 SDK：34 (Android 14)

### 3. 端到端验证

1. 启动后端 `python main.py`
2. Android 模拟器运行 App
3. `http://127.0.0.1:8000/docs` 手动测试接口
4. 列表中应显示 10 部短剧，点击进入播放页

---

## 核心功能

| 模块 | 功能 | 当前状态 |
|------|------|---------|
| 短剧列表 | 网格展示、标签筛选、封面加载 | ✅ 已完成 |
| 短剧详情 | 封面、简介展开、选集网格 | ✅ 已完成 |
| 视频播放器 | ViewPager2 切剧、SeekBar 拖拽、倍速选择、选集面板 | ✅ 已完成 |
| 播放进度 | 内存缓存、续播恢复、自动切集 | ✅ 已完成 |
| 高光点数据 | 数据库存储、API 下发、类型标注 | ✅ 已完成 |
| 小墨 Agent | Peek/Expanded 状态机、模块注册中心 | ⌛ 排期中 |
| 高光互动 | 情绪弹幕、剧情投票、AI 问答模块 | ⌛ 排期中 |
| AI 续写 | 剧集结尾 LLM 剧情续写 | ⌛ 排期中 |
| 对话引擎 | 意图识别、短剧检索、用户画像 | ⌛ 排期中 |

---

## API 一览

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/health` | 健康检查（含 LLM 可用性） |
| GET | `/api/v1/dramas` | 短剧列表 |
| GET | `/api/v1/dramas/{drama_id}` | 短剧详情（含剧集） |
| GET | `/api/v1/playback/{episode_id}` | 播放信息（含高光点） |
| POST | `/api/v1/progress` | 上报播放进度 |
| POST | `/api/v1/agent/chat` | 小墨 Agent 流式对话 |
| POST | `/api/v1/agent/story-extension` | AI 剧情续写 |
| POST | `/api/v1/agent/generate-highlights` | 智能生成高光点 |
| POST | `/api/v1/interactions` | 上报用户互动数据 |

---

## 数据库表

| 表 | 说明 | 行数 |
|---|---|---|
| `dramas` | 短剧元数据 | 10 |
| `drama_tags` | 短剧标签（多值） | ~30 |
| `episodes` | 剧集信息 | ~500 |
| `highlights` | 高光时刻 | ~1000 |
| `user_progress` | 播放进度 | 按用户 |
| `user_interactions` | 用户互动记录 | 按用户 |
| `user_profiles` | 用户画像 | 按用户 |

---

## 文档索引

| 文档 | 内容 |
|------|------|
| [PRD V1.0](docs/青墨短剧_小墨Agent_PRD_V1.0.md) | 产品需求文档（1049 行） |
| [开发排期](docs/青墨短剧_小墨Agent_20260521-0610开发排期_V1.0.md) | 21 天开发计划（6.11 交付） |
| [架构文档](docs/ARCHITECTURE.md) | 系统架构、数据流、组件关系 |
| [接口文档](docs/API.md) | 完整 API 参考 |
| [数据库设计](docs/DATABASE.md) | 表结构、迁移策略 |

---

## 许可

内部比赛项目，非开源。
