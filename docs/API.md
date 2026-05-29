# 青墨短剧 — API 接口文档

> 版本：V1.0.0 · Base URL: `http://127.0.0.1:8000`

---

## 通用说明

- **协议**：HTTP/1.1
- **编码**：UTF-8
- **内容类型**：`application/json`（除流式接口外）
- **流式接口**：`text/plain`（SSE 风格逐 chunk 返回）
- **跨域**：CORS 全开放（`*`），生产环境需改为白名单
- **鉴权**：V1.0 无 Token 鉴权，使用 `user_id` 识别用户

---

## 一、健康检查

```
GET /api/v1/health
```

**响应** `200 OK`：
```json
{
  "status": "ok",
  "service": "青墨 API",
  "llm_available": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `llm_available` | bool | `false` 时表示 Doubao API Key 未配置，对话功能不可用 |

---

## 二、短剧资源

### 2.1 获取短剧列表

```
GET /api/v1/dramas
```

**响应** `200 OK`：
```json
[
  {
    "id": 1,
    "title": "北派寻宝笔记",
    "cover_url": "covers/01.jpg",
    "tags": ["探险"],
    "total_episodes": 81
  }
]
```

### 2.2 获取短剧详情

```
GET /api/v1/dramas/{drama_id}
```

**参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| `drama_id` | int | 短剧 ID（1-10） |

**响应** `200 OK`：
```json
{
  "id": 1,
  "title": "北派寻宝笔记",
  "author": "作者名",
  "description": "剧情简介…",
  "cover_url": "covers/01.jpg",
  "tags": ["探险", "悬疑"],
  "episodes": [
    {
      "episode_id": 1001,
      "episode_num": 1,
      "title": "第1集",
      "duration": 300,
      "thumbnail_url": null
    }
  ]
}
```

**错误**：`404` — `{"detail": "Drama not found"}`

---

## 三、播放服务

### 3.1 获取播放信息

```
GET /api/v1/playback/{episode_id}
```

**响应** `200 OK`：
```json
{
  "episode_id": 1001,
  "video_url": "videos/01/1.mp4",
  "duration": 300,
  "highlights": [
    {
      "id": 1,
      "episode_id": 1001,
      "time": 45.5,
      "type": "conflict",
      "title": "剧情冲突",
      "widget_type": "vote",
      "options": null
    }
  ]
}
```

| `widget_type` | 说明 |
|---|---|
| `emotion` | 情绪弹幕（情绪按钮 + 全屏特效） |
| `vote` | 剧情投票（选项 + 实时结果） |
| `quiz` | AI 问答（小墨提问 + 用户回答） |
| `branch` | 剧情分支（选项 + 分支内容） |

**错误**：`404` — `{"detail": "Episode not found"}`

### 3.2 上报播放进度

```
POST /api/v1/progress?episode_id=1001&progress=45000
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `episode_id` | int | 是 | 剧集 ID |
| `progress` | int | 是 | 播放进度（毫秒） |

**响应** `200 OK`：
```json
{ "ok": true }
```

> 采用 UPSERT 策略：`INSERT … ON CONFLICT DO UPDATE`，相同 episode_id 自动覆盖

---

## 四、小墨 Agent

### 4.1 流式对话

```
POST /api/v1/agent/chat
```

**请求体**：
```json
{
  "user_id": "device-abc123",
  "message": "推荐一部好看的甜宠剧",
  "context": {
    "drama_id": 1,
    "episode_id": 1001,
    "current_time": 45.5
  },
  "history": [
    {"role": "user", "content": "你好"},
    {"role": "assistant", "content": "嗨～我是小墨！"}
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `user_id` | string | 是 | 设备/用户唯一标识 |
| `message` | string | 是 | 用户输入文本 |
| `context` | object | 否 | 当前观剧上下文 |
| `history` | array | 否 | 最近对话历史 |

**响应** `200 OK`：`text/plain` 流式逐块返回

> 安全：用户输入自动做 `\n` 转义 + `"""` 包裹防 Prompt 注入
> 重试：30s 超时 + 3 次指数退避重试
> 降级：API Key 未配置时返回 "小墨当前离线，请检查 API Key 配置~"

### 4.2 AI 剧情续写

```
POST /api/v1/agent/story-extension
```

**请求体**：
```json
{
  "drama_title": "北派寻宝笔记",
  "drama_desc": "一群探险者在古墓中发现惊天秘密…",
  "latest_episodes": ["第80集", "第81集"],
  "user_preferences": ["探险", "悬疑"]
}
```

**响应** `200 OK`：
```json
{
  "extension": "第八十二集：随着古墓深处的那扇门缓缓打开…（200-500字续写内容）"
}
```

### 4.3 智能生成高光点

```
POST /api/v1/agent/generate-highlights
```

**请求体**：
```json
{
  "drama_title": "北派寻宝笔记",
  "episode_transcript": "第1集台词摘要：男主进入古墓…",
  "episode_duration": 300.0
}
```

**响应** `200 OK`：
```json
{
  "highlights": [
    {
      "time": 45.5,
      "type": "conflict",
      "title": "男主霸气护妻",
      "widget_type": "emotion",
      "emotion_hints": ["爽！", "太解气了"]
    }
  ]
}
```

---

## 五、用户互动

### 5.1 上报互动数据

```
POST /api/v1/interactions
```

**请求体**：
```json
{
  "user_id": "device-abc123",
  "episode_id": 1001,
  "highlight_id": 1,
  "module_id": "emotion",
  "interaction_data": {
    "emotion": "爽！"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `user_id` | string | 是 | 用户标识 |
| `episode_id` | int | 是 | 剧集 ID |
| `highlight_id` | int | 否 | 关联的高光点 ID |
| `module_id` | string | 是 | 模块标识（`emotion`/`vote`/`quiz`） |
| `interaction_data` | object | 是 | 互动详情 JSON（最大 4KB） |

**响应** `200 OK`：
```json
{ "ok": true, "interaction_id": 42 }
```

**错误**：`413` — `{"detail": "互动数据过大，请精简至 4KB 以内"}`

---

## 六、错误码总览

| 状态码 | 含义 | 常见原因 |
|--------|------|---------|
| 200 | 成功 | — |
| 404 | 资源不存在 | drama_id / episode_id 无效 |
| 413 | 请求体过大 | interaction_data 超过 4KB |
| 500 | 服务器内部错误 | 数据库连接失败 / LLM 异常 |

---

## 七、静态资源路径

| 路径 | 映射目录 | 示例 |
|------|---------|------|
| `/covers/{filename}` | `crawler/data/covers/` | `http://127.0.0.1:8000/covers/01.jpg` |
| `/videos/{dir}/{file}` | `crawler/data/videos/` | `http://127.0.0.1:8000/videos/01/1.mp4` |
