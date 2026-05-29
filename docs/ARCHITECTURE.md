# 青墨短剧 — 系统架构文档

> 版本：V1.0.0 · 2026-05-29

---

## 一、架构总览

```
┌──────────────────────────────────────────────────────────┐
│                      Android 客户端                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐   │
│  │ NavGraph │  │  Theme   │  │    Screens (3 pages)  │   │
│  │ 路由控制  │  │ 青白主题  │  │ List / Detail / Pager │   │
│  └────┬─────┘  └──────────┘  └──────────┬───────────┘   │
│       │                                  │                │
│  ┌────┴──────────────────────────────────┴───────────┐   │
│  │              Data Layer                            │   │
│  │  ApiService (Retrofit)  ·  Models  ·  Repository   │   │
│  │  ProgressCache (ConcurrentHashMap)                 │   │
│  └──────────────────────┬────────────────────────────┘   │
└─────────────────────────┼─────────────────────────────────┘
                          │ HTTP (127.0.0.1:8000)
┌─────────────────────────┼─────────────────────────────────┐
│                   FastAPI 后端                             │
│  ┌──────────────────────┴────────────────────────────┐   │
│  │  main.py: CORS + StaticFiles + Router             │   │
│  └──────────────────────┬────────────────────────────┘   │
│       ┌─────────────────┼─────────────────┐              │
│  ┌────┴────┐      ┌─────┴──────┐   ┌──────┴──────┐      │
│  │ router  │      │  schemas   │   │ llm_service │      │
│  │ 9 端点   │      │ Pydantic   │   │ Doubao LLM  │      │
│  └────┬────┘      └────────────┘   └──────┬──────┘      │
│       │                                    │              │
│  ┌────┴────────────────────────────────────┴──────┐      │
│  │              database.py                        │      │
│  │  SQLite: dramas · episodes · highlights          │      │
│  │           user_progress · user_interactions      │      │
│  │           user_profiles                          │      │
│  └────────────────────────────────────────────────┘      │
│                                                          │
│  ┌──────────────┐   ┌─────────────────────────┐         │
│  │  crawler/    │   │  火山引擎 Ark            │         │
│  │  Playwright  │   │  Doubao-Seed-2.0-lite   │         │
│  └──────────────┘   └─────────────────────────┘         │
└──────────────────────────────────────────────────────────┘
```

---

## 二、客户端架构 (Android)

### 2.1 分层设计

```
com.qingmo.app/
├── MainActivity.kt          ← 入口，setContent + edge-to-edge
├── QingmoApp.kt             ← Application 子类
├── data/                    ← 数据层
│   ├── api/
│   │   ├── ApiService.kt    ← Retrofit 接口定义（4 端点）
│   │   └── RetrofitClient.kt ← 单例客户端，BASE_URL = 127.0.0.1:8000
│   ├── model/Models.kt      ← 6 个 @Immutable 数据类
│   ├── repository/DramaRepository.kt ← Result 封装
│   └── ProgressCache.kt     ← ConcurrentHashMap 内存进度缓存
└── ui/
    ├── navigation/NavGraph.kt   ← 三页面路由 + 转场动画
    ├── screens/
    │   ├── DramaListScreen.kt   ← 列表页（2列网格 + 骨架屏）
    │   ├── DramaDetailScreen.kt ← 详情页（封面 + 简介 + 选集）
    │   ├── DramaPagerScreen.kt  ← 播放器（ViewPager2 + ExoPlayer）
    │   └── PlayerScreen.kt      ← 播放器薄封装
    └── theme/
        ├── Color.kt             ← 28 色值 + 4 渐变
        └── Theme.kt             ← Material3 lightColorScheme + 13 级字体
```

### 2.2 导航流

```
 LIST ──(点击卡片)──→ PLAYER (episodeId=-1L, 自动首集)
 PLAYER ──(点击剧名)──→ DETAIL
 DETAIL ──(点击剧集)──→ PLAYER (指定 episodeId)

 回退栈: LIST → PLAYER → DETAIL
         ←←← popBackStack ←←←
```

### 2.3 播放器架构

```
DramaPagerScreen
  └── ViewPager2 (垂直滑动切剧)
        └── NativeAdapter (RecyclerView.Adapter)
              └── ViewHolder
                    ├── PlayerView (ExoPlayer)
                    ├── SeekBar (自定义 Canvas 绘制)
                    ├── 工具栏 (返回/标题/倍速/菜单)
                    ├── 右侧互动栏 (评分/评论/点赞/分享)
                    └── 底部信息区 (弹幕开关/标题/简介/选集/全屏)

  分页预加载: 当前页 ±1 (players Map 管理)
  进度缓存: ProgressCache (ConcurrentHashMap), 每 2s 写入
  剧终跳转: STATE_ENDED → markWatched → 自动下一集

  ESheet (Compose) — 选集面板 (5 列网格, 青白主题色)
  SS (Compose) — 倍速面板 (0.75x~2.0x, 青白主题色)
```

### 2.4 主题系统

| 角色 | 色值 | 说明 |
|------|------|------|
| Primary | `#5B8C85` | 石青主色 |
| Background | `#F7F3EC` | 宣纸暖白 |
| Surface | `#FCFAF6` | 卡片背景 |
| OnSurface | `#3D3D3D` | 正文文字 |
| Border | `#D5CFC0` | 边框线 |
| PlayerBg | `Background` | 播放器面板底色（青白，非暗色） |

---

## 三、服务端架构 (FastAPI)

### 3.1 分层设计

```
main.py ── FastAPI 应用入口
  ├── CORS 中间件 (allow_origins=["*"])
  ├── StaticFiles (/covers, /videos)
  └── router (/api/v1)
        ├── config.py      ← Pydantic Settings (.env 加载)
        ├── schemas.py     ← 6 个 Pydantic Model
        ├── database.py    ← SQLite 连接 + 建表 + 迁移
        └── llm_service.py ← Doubao LLM 封装
```

### 3.2 请求处理流程

```
 HTTP Request
     │
     ▼
 main.py → CORS Middleware
     │
     ▼
 router.py (URL 匹配)
     │
     ├── GET  /health        → config.settings
     ├── GET  /dramas        → database.get_connection()
     ├── GET  /dramas/{id}   → database → Pydantic 序列化
     ├── GET  /playback/{id} → database → Pydantic
     ├── POST /progress      → database (UPSERT)
     ├── POST /agent/chat    → llm_service.chat() → StreamingResponse
     ├── POST /agent/story-extension → llm_service → JSON
     ├── POST /agent/generate-highlights → llm_service → JSON
     └── POST /interactions  → database (INSERT)
```

### 3.3 LLM 服务设计

```
llm_service.py — LLMService (单例)

  __init__():
    ├── API Key 存在 → 创建 AsyncArk 客户端
    └── API Key 为空 → 降级为离线模式

  chat() [流式]:
    ├── 组装 system prompt (小墨人格)
    ├── 拼接 history + drama_context
    ├── """包裹用户输入防注入
    ├── 30s 超时 + 3 次指数退避重试
    └── StreamingResponse (text/plain)

  story_extension() [非流式]:
    └── 基于短剧上下文生成 200-500 字续写

  generate_highlights() [非流式]:
    └── 从 JSON 响应中提取高光点数组
```

### 3.4 数据库迁移策略

```
init_db() — 全量建表 (IF NOT EXISTS 保证幂等)
migrate_add_columns() — 增量迁移:
    1. highlights 表: RENAME → CREATE → INSERT → DROP
    2. 字段补全: ALTER TABLE ADD COLUMN (逐个尝试, sqlite3.OperationalError 静默跳过)
    3. user_interactions: 检查→建表
```

---

## 四、数据流

### 4.1 数据获取链路

```
fanqieopen.com
     │
     ▼
crawler/crawl_dramas.py (Playwright)
     │   DOM 提取 + API 拦截
     ▼
crawler/data/dramas_data.json
     │
     ▼
init_db.py (seed)
     │
     ▼
ju_flash.db (SQLite)
     │
     ▼
FastAPI (router.py)
     │   JSON
     ▼
Android (Retrofit + Gson)
     │   Kotlin Data Class
     ▼
Compose UI (State Hoisting)
```

### 4.2 用户交互数据流

```
用户点击互动按钮
     │
     ▼
Android 本地记录 (ProgressCache / interaction_data)
     │
     ▼ HTTP POST /api/v1/interactions
     ▼
router.py → INSERT INTO user_interactions
     │
     ▼
ju_flash.db (持久化)
```

### 4.3 高光点触发流

```
视频播放进度更新
     │ 每 500ms 检查
     ▼
当前时间命中 highlight.time (±2s)
     │
     ▼
widget_type 匹配 → 展示对应互动 UI
     ├── emotion → EmotionModule (情绪弹幕)
     ├── vote    → VoteModule (剧情投票)
     ├── quiz    → QuizModule (AI 问答)
     └── branch  → BranchModule (剧情分支)
```

---

## 五、技术决策记录

| 决策 | 原因 |
|------|------|
| SQLite 而非 PostgreSQL | 本地部署零配置，比赛场景单机运行即可 |
| raw SQL 而非 ORM | 项目规模小（7 张表），ORM 增加复杂度无收益 |
| Compose BOM 2024.10.00 | 规避低版本 BOM 的 CircularProgressIndicator 崩溃 |
| ktlint disabled: no-unused-imports | InfiniteTransition.animateFloat 扩展函数需显式导入 |
| ViewPager2 而非 Compose Pager | 避免 Compose 子组合在 Mate 70 上的崩溃问题 |
| PlayerBg = Background | 强制青白主题，绝不用暗色面板 |
| LLM API Key 仅存后端 | Android 零密钥暴露，通过 FastAPI 代理调用 |
