# 青墨短剧 — 数据库设计文档

> 版本：V1.0.0 · SQLite · 7 张表

---

## 一、ER 图

```
┌──────────┐       ┌──────────────┐
│  dramas  │──1:N──│ drama_tags   │
│          │       └──────────────┘
│  id (PK) │
│  title   │──1:N──┌──────────────┐
│  author  │       │  episodes    │
│  desc    │       │              │
│  cover   │       │ episode_id   │──1:N──┌──────────────┐
│  total   │       │ drama_id(FK) │       │ highlights   │
└──────────┘       │ title        │       │              │
                   │ video_url    │       │ id (PK)      │
                   └──────────────┘       │ episode_id   │
                                          │ time (REAL)  │
                                          │ type / title │
                   ┌──────────────┐       │ widget_type  │
                   │user_progress │       │ options      │
                   │              │       │ emotion_hints│
                   │ episode_id   │       │ duration     │
                   │ progress     │       └──────────────┘
                   │ watched      │
                   └──────────────┘       ┌──────────────────┐
                                          │user_interactions │
                   ┌──────────────┐       │ user_id          │
                   │user_profiles │       │ episode_id       │
                   │ user_id (PK) │       │ highlight_id     │
                   │ watch_history│       │ module_id        │
                   │ preferences  │       │ interaction_data │
                   └──────────────┘       └──────────────────┘
```

---

## 二、表结构

### 2.1 dramas — 短剧元数据

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | INTEGER | PK AUTOINCREMENT | 短剧 ID（1-10） |
| `title` | TEXT | NOT NULL | 短剧名称 |
| `author` | TEXT | DEFAULT '' | 作者 |
| `description` | TEXT | DEFAULT '' | 剧情简介 |
| `cover_url` | TEXT | DEFAULT '' | 封面图路径 |
| `category` | TEXT | DEFAULT '' | 分类标签 |
| `total_episodes` | INTEGER | DEFAULT 0 | 总集数 |

### 2.2 drama_tags — 短剧标签

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | INTEGER | PK AUTOINCREMENT | — |
| `drama_id` | INTEGER | FK → dramas.id | 所属短剧 |
| `tag` | TEXT | NOT NULL | 标签值 |

> 每部剧最多 5 个标签，de-duplicated by Web

### 2.3 episodes — 剧集

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `episode_id` | INTEGER | PK | 剧集全局 ID（`drama_id * 1000 + ep_num`） |
| `drama_id` | INTEGER | FK → dramas.id | 所属短剧 |
| `episode_num` | INTEGER | NOT NULL | 集数编号（1 ~ total_episodes） |
| `title` | TEXT | DEFAULT '' | 剧集标题 |
| `duration` | INTEGER | DEFAULT 0 | 时长（秒） |
| `video_url` | TEXT | DEFAULT '' | 视频文件路径 |
| `thumbnail_url` | TEXT | DEFAULT '' | 缩略图路径 |

> episode_id 复合编码：`1 * 1000 + 1 = 1001` = 第 1 部剧的第 1 集

### 2.4 highlights — 高光时刻

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | INTEGER | PK AUTOINCREMENT | — |
| `episode_id` | INTEGER | FK → episodes.episode_id | 所属剧集 |
| `time` | REAL | NOT NULL | 高光时间点（秒，支持小数） |
| `type` | TEXT | NOT NULL | 类型枚举 |
| `title` | TEXT | DEFAULT '' | 高光标题 |
| `widget_type` | TEXT | DEFAULT 'emotion' | 互动组件类型 |
| `options` | TEXT | — | JSON 配置 |
| `emotion_hints` | TEXT | — | JSON 推荐情绪按钮 |
| `duration` | INTEGER | DEFAULT 15 | 互动面板展示时长（秒） |

**type 枚举值**：

| 值 | 含义 | 对应 widget_type |
|---|---|---|
| `conflict` | 剧情冲突 | `vote` |
| `twist` | 惊天反转 | `emotion` |
| `sweet` | 甜蜜时刻 | `emotion` |
| `famous` | 名场面 | `emotion` |
| `funny` | 搞笑桥段 | `emotion` |
| `branch` | 剧情分岐点 | `branch` |

**widget_type 枚举值**：

| 值 | 含义 |
|---|---|
| `emotion` | 情绪弹幕互动 |
| `vote` | 剧情投票互动 |
| `quiz` | AI 剧情问答 |
| `branch` | 剧情分支 |

### 2.5 user_progress — 播放进度

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | INTEGER | PK AUTOINCREMENT | — |
| `episode_id` | INTEGER | UNIQUE, NOT NULL | 剧集 ID |
| `progress` | INTEGER | DEFAULT 0 | 播放进度（毫秒） |
| `watched` | INTEGER | DEFAULT 0 | 是否已看完 |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 最后更新时间 |

> UPSERT：同一 episode_id 自动覆盖 progress + watched

### 2.6 user_interactions — 用户互动记录

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | INTEGER | PK AUTOINCREMENT | — |
| `user_id` | TEXT | NOT NULL | 用户标识 |
| `episode_id` | INTEGER | FK → episodes | 剧集 ID |
| `highlight_id` | INTEGER | FK → highlights | 高光点 ID |
| `module_id` | TEXT | NOT NULL | 互动模块标识 |
| `interaction_data` | TEXT | — | JSON 互动详情 |
| `created_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 互动时间 |

**索引**：
- `idx_interactions_user` ON `user_id`
- `idx_interactions_episode` ON `episode_id`

### 2.7 user_profiles — 用户画像

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `user_id` | TEXT | PK | 用户唯一标识 |
| `watch_history` | TEXT | — | JSON 观看历史 |
| `favorite_dramas` | TEXT | — | JSON 收藏短剧 |
| `interaction_stats` | TEXT | — | JSON 互动统计 |
| `preferences` | TEXT | — | JSON 偏好标签 |
| `updated_at` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP | 最后更新时间 |

---

## 三、迁移策略

### 3.1 全量重建

```bash
python init_db.py
```

1. 调用 `init_db()` 创建所有表（`IF NOT EXISTS` 保证幂等）
2. 清空旧数据（`DELETE FROM` 全表）
3. 从 `dramas_data.json` 或内置占位数据重新插入
4. 每集随机生成 1-3 个高光点（含 `emotion_hints` 和 `duration`）

### 3.2 增量迁移

`database.py::migrate_add_columns()` 在启动时自动执行：

1. **highlights.time 类型迁移**：`INTEGER → REAL`（rename-recreate 安全策略）
2. **字段补全**：逐个 `ALTER TABLE ADD COLUMN`，已存在则静默跳过
3. **user_interactions 表**：不存在则创建

```python
# 安全：已执行过的迁移不会重复操作
except sqlite3.OperationalError:
    pass  # 迁移已执行过，静默跳过
```

---

## 四、种子数据

### 4.1 数据来源

优先从 `crawler/data/dramas_data.json` 加载真实爬取的短剧元数据，不存在时回退到 10 部内置中文占位短剧：

| ID | 短剧名 | 标签 |
|----|--------|------|
| 1 | 北派寻宝笔记 | 探险 |
| 2 | 天下第一纨绔 | 古装 |
| 3 | 十八岁太奶奶驾到第三部 | 穿越 |
| 4 | 幸得相遇离婚时 | 都市 |
| 5 | 荒年全村啃树皮我有系统满仓肉 | 年代 |
| 6 | 家里家外 | 家庭 |
| 7 | 云渺1 | 仙侠 |
| 8 | 撕夜 | 悬疑 |
| 9 | 那年冬至 | 校园 |
| 10 | 北往 | 年代 |

### 4.2 高光点生成规则

- 每集随机 1-3 个高光点
- 时间随机分布在 10-280 秒（浮点精度 0.1 秒）
- 类型按权重随机分配
- 每种类型自动匹配对应的 `widget_type` 和 `emotion_hints`

---

## 五、文件路径

| 文件 | 说明 |
|------|------|
| `backend/ju_flash.db` | SQLite 数据库主文件 |
| `backend/init_db.py` | 初始化与种子数据脚本 |
| `backend/app/database.py` | 连接管理 + 建表 + 迁移 |
| `backend/crawler/data/dramas_data.json` | 爬取的短剧元数据 |
