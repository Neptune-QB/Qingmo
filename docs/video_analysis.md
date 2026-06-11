# 短剧 MP4 自动分析脚本

基于 LLM 的视频自动分析脚本，输入 MP4 文件，自动提取台词、剧情分段、每集内容摘要、高光点草稿，并写入数据库。

## 依赖项安装

```bash
# 核心依赖
pip install openai

# ASR (语音识别) - 二选一
pip install faster-whisper    # 本地方案（推荐）
# 或使用豆包 ASR（暂未实现接口）

# ffmpeg / ffprobe (必须)
# Windows: 下载 https://ffmpeg.org/download.html 并加入 PATH
# macOS: brew install ffmpeg
# Linux: apt install ffmpeg

# 可选：视觉理解
# 豆包多模态自动读取 DOUBAO_API_KEY 环境变量
```

## ffmpeg / ffprobe 要求

- `ffprobe`：用于读取视频元信息（时长、帧率、分辨率、音频流）
- `ffmpeg`：用于抽取音频（16kHz wav）和抽取关键帧

验证安装：
```bash
ffprobe -version
ffmpeg -version
```

## ASR Provider 配置

### faster-whisper（本地，推荐）

```bash
pip install faster-whisper
```

首次运行会自动下载模型（默认 small，约 2GB），支持通过参数指定模型大小：
```bash
--asr-provider faster_whisper
```

### 豆包 ASR

暂未实现接口，可以后续扩展 `providers/asr.py`。

## LLM Provider 配置

通过环境变量配置（与后端 `.env` 共享）：

```bash
# .env 或环境变量
DOUBAO_API_KEY=your-api-key
DOUBAO_EP_ID=your-endpoint-id
DOUBAO_BASE_URL=https://ark.cn-beijing.volces.com/api/v3  # 默认值
```

## 数据库写入规则

分析结果写入以下表：

| 表名 | 内容 | 写入规则 |
|------|------|---------|
| `video_analysis_task` | 任务记录 | 每次分析创建一条 |
| `episode_transcript` | 台词 | ASR 每句一行，`--force` 时先删旧 |
| `episode_scene_segment` | 剧情片段 | 每个窗口一行，`--force` 时先删旧 |
| `episode_content_summary` | 每集摘要 | 同 episode_id 已有则更新 |
| `drama_highlight` | 高光点 | 新增，status=draft，source_type=ai_video_analysis |

## 使用方式

### dry-run：仅输出 JSON 报告，不写数据库

```bash
uv run python -m tools.video_analysis.analyze_episode \
  --episode-id 1 \
  --video-path ./data/videos/episode_1.mp4 \
  --dry-run
```

报告输出到 `data/analysis/episode_{episode_id}_analysis.json`。

### 写入数据库

确认 dry-run 报告合理后：

```bash
uv run python -m tools.video_analysis.analyze_episode \
  --episode-id 1 \
  --video-path ./data/videos/episode_1.mp4 \
  --save
```

### 强制重新分析（清理旧数据）

```bash
uv run python -m tools.video_analysis.analyze_episode \
  --episode-id 1 \
  --video-path ./data/videos/episode_1.mp4 \
  --save --force
```

### 跳过 ASR（仅有视觉分析）

```bash
uv run python -m tools.video_analysis.analyze_episode \
  --episode-id 1 \
  --video-path ./data/videos/episode_1.mp4 \
  --dry-run --skip-asr
```

### 完整参数列表

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--episode-id` | 必填 | 剧集 ID |
| `--drama-id` | 自动 | 短剧 ID（从 episode 表自动查询） |
| `--video-path` | 必填 | MP4 文件路径 |
| `--sample-interval-seconds` | 3 | 每隔几秒抽一帧 |
| `--window-seconds` | 10 | 剧情分析窗口大小 |
| `--window-overlap-seconds` | 2 | 窗口重叠 |
| `--min-confidence` | 0.70 | 最低置信度 |
| `--max-highlights-per-minute` | 1 | 每分钟最多高光点 |
| `--max-highlights-per-episode` | 8 | 每集最多高光点 |
| `--min-gap-between-highlights-ms` | 20000 | 高光点最小间隔（ms） |
| `--same-type-min-gap-ms` | 45000 | 同类型最小间隔（ms） |
| `--dry-run` | - | 仅输出 JSON |
| `--save` | - | 写入数据库 |
| `--force` | - | 清理旧数据重新分析 |
| `--output` | auto | JSON 报告输出路径 |
| `--skip-asr` | - | 跳过语音识别 |
| `--skip-frames` | - | 跳过抽帧 |
| `--asr-provider` | faster_whisper | ASR Provider 类型 |
| `--vision-provider` | noop | 视觉 Provider 类型 |

## 如何查看生成的高光点

### 方式1：API 查询

```bash
curl http://localhost:8000/api/v1/episodes/1/play | jq .highlights
```

### 方式2：直接查询数据库

```sql
SELECT * FROM drama_highlight
WHERE episode_id = 1 AND source_type = 'ai_video_analysis'
ORDER BY start_time_ms;
```

### 方式3：查看分析报告

查看 `data/analysis/episode_{episode_id}_analysis.json`。

## 如何让播放页读取这些高光点

播放接口已更新，`GET /api/v1/episodes/{episode_id}/play` 和 `GET /api/v1/playback/{episode_id}` 现在会返回 `status IN ('enabled', 'draft', 'ai_pending_review')` 的所有高光点。

AI 生成的高光点 status 为 `draft`，播放页可以直接看到。审核通过后手动将 status 改为 `enabled` 即可正式发布。

## 高光点类型说明

10 类高光点及其时长规则：

| 类型 | 中文名 | 时长范围 | interaction_type |
|------|--------|----------|-----------------|
| cliffhanger | 悬念钩子 | 8-15s | support_button |
| choice_point | 选择节点 | 10-20s | choice_panel |
| emotional_burst | 情感爆发 | 10-20s | reaction_panel |
| power_moment | 爽点时刻 | 6-12s | support_button |
| comedy | 搞笑瞬间 | 3-8s | reaction_panel |
| suspense | 紧张悬念 | 8-18s | support_button |
| heartbreak | 虐心时刻 | 10-20s | reaction_panel |
| sweet_moment | 甜蜜时刻 | 8-15s | reaction_panel |
| reversal | 惊天反转 | 5-12s | reaction_panel |
| slapback | 打脸爽点 | 5-12s | support_button |

## 文件结构

```
tools/video_analysis/
├── __init__.py          # 包初始化
├── __main__.py          # 模块入口
├── analyze_episode.py   # 主分析脚本
├── prompts.py           # LLM Prompt 模板
├── post_process.py      # 高光点后处理
├── db_writer.py         # 数据库写入
└── providers/
    ├── __init__.py      # Provider 接口
    ├── asr.py           # ASR Provider (faster-whisper)
    ├── vision.py        # 视觉理解 Provider
    └── llm.py           # LLM Provider
```

## 分析流程

```
输入 MP4
  │
  ├─ 1. ffprobe → 视频元信息
  ├─ 2. ffmpeg → 抽取音频 (16kHz wav)
  ├─ 3. ASR → 台词 + 时间戳 → episode_transcript 表
  ├─ 4. ffmpeg → 抽取关键帧 (每 3s)
  ├─ 5. Vision (可选) → 画面描述
  ├─ 6. 按时间窗口切分 (每 10s, 重叠 2s)
  ├─ 7. LLM → 每窗口剧情摘要 → episode_scene_segment 表
  ├─ 8. LLM → 每窗口高光点候选
  ├─ 9. LLM → 每集整体摘要 → episode_content_summary 表
  ├─ 10. 后处理：筛选/合并/去重 → drama_highlight 表
  └─ 11. 输出 JSON 报告
```

## 日志

每个分析阶段都有日志输出：

```
ffprobe: 读取视频元信息...
ffmpeg: 抽取音频...
ASR: 开始语音识别...
ffmpeg: 抽取关键帧...
LLM 分析窗口 1/15: 0-10000ms
LLM: 生成每集整体摘要...
高光点后处理: 12 个候选
写入数据库...
报告已保存: data/analysis/episode_1_analysis.json
```

## 错误处理

- ffprobe/ffmpeg 不可用 → 明确错误提示并退出
- ASR 失败 → 可降级继续（标记 transcript 缺失）
- LLM 不可用 → 退出并提示配置环境变量
- LLM 返回非 JSON → 尝试修复一次，仍失败则跳过该窗口
- 任何步骤失败 → task.status = failed，记录 error_message
