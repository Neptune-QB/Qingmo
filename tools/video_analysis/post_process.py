"""高光点后处理：筛选、合并、去重、排序"""

import json
from typing import Optional

# 10 类高光点允许值
VALID_HIGHLIGHT_TYPES = {
    "cliffhanger", "choice_point", "emotional_burst",
    "power_moment", "comedy", "suspense",
    "heartbreak", "sweet_moment", "reversal", "slapback",
}

# highlight_type → interaction_type 映射
INTERACTION_TYPE_MAP = {
    "cliffhanger":     "support_button",
    "choice_point":    "choice_panel",
    "emotional_burst": "reaction_panel",
    "power_moment":    "support_button",
    "comedy":          "reaction_panel",
    "suspense":        "support_button",
    "heartbreak":      "reaction_panel",
    "sweet_moment":    "reaction_panel",
    "reversal":        "reaction_panel",
    "slapback":        "support_button",
}

# 高光点时长规则（毫秒）
HIGHLIGHT_DURATION_RULES = {
    "cliffhanger":     (8000,  15000),
    "choice_point":    (10000, 20000),
    "emotional_burst": (10000, 20000),
    "power_moment":    (6000,  12000),
    "comedy":          (3000,  8000),
    "suspense":        (8000,  18000),
    "heartbreak":      (10000, 20000),
    "sweet_moment":    (8000,  15000),
    "reversal":        (5000,  12000),
    "slapback":        (5000,  12000),
}


def build_interaction_config(highlight_type: str) -> dict:
    """根据 highlight_type 生成默认 interaction_config"""
    xiaomo_danmaku = {
        "enabled": True,
        "text": "前方高能！",
        "style": "highlight",
        "duration_ms": 3000,
        "position": "near_xiaomo",
    }

    if highlight_type in ("cliffhanger", "power_moment", "suspense", "slapback"):
        # support_button
        base = {
            "button_text": "互动一下",
            "clicked_text": "已互动",
            "effect_text": "互动值 +1",
            "auto_hide_seconds": 6,
            "pause_video": False,
            "show_stats_after_click": True,
            "xiaomo_danmaku": xiaomo_danmaku,
        }
        overrides = {
            "cliffhanger": {
                "button_text": "继续看",
                "clicked_text": "已锁定",
                "effect_text": "悬念值 +1",
            },
            "power_moment": {
                "button_text": "爽到了",
                "clicked_text": "已爽到",
                "effect_text": "爽感值 +1",
            },
            "suspense": {
                "button_text": "屏住呼吸",
                "clicked_text": "已屏息",
                "effect_text": "紧张值 +1",
            },
            "slapback": {
                "button_text": "帮她反击",
                "clicked_text": "已助力",
                "effect_text": "反击值 +1",
            },
        }
        dm_overrides = {
            "cliffhanger": "前方有悬念，别眨眼",
            "power_moment": "爽点来了",
            "suspense": "屏住呼吸",
            "slapback": "反击时刻到了",
        }
        base.update(overrides.get(highlight_type, {}))
        base["xiaomo_danmaku"] = {**xiaomo_danmaku, "text": dm_overrides.get(highlight_type, "前方高能！")}
        return base

    elif highlight_type == "choice_point":
        # choice_panel
        return {
            "title": "你希望剧情怎么走？",
            "options": [
                {"key": "fight_back", "label": "直接反击"},
                {"key": "wait", "label": "先忍一下"},
                {"key": "ask_help", "label": "找人帮忙"},
            ],
            "auto_hide_seconds": 8,
            "pause_video": True,
            "show_stats_after_click": True,
            "xiaomo_danmaku": {
                "enabled": True,
                "text": "关键选择来了",
                "style": "highlight",
                "duration_ms": 3000,
                "position": "near_xiaomo",
            },
        }

    else:
        # reaction_panel: emotional_burst, comedy, heartbreak, sweet_moment, reversal
        base = {
            "title": "看到这里你是什么反应？",
            "options": [
                {"key": "option_1", "label": "太上头了"},
                {"key": "option_2", "label": "有感觉"},
                {"key": "option_3", "label": "再看一遍"},
            ],
            "auto_hide_seconds": 6,
            "pause_video": False,
            "show_stats_after_click": True,
            "xiaomo_danmaku": xiaomo_danmaku,
        }
        overrides = {
            "emotional_burst": {
                "title": "这一刻你什么感受？",
                "options": [
                    {"key": "touched", "label": "被打动了"},
                    {"key": "understand", "label": "我懂她"},
                    {"key": "heartbroken", "label": "有点破防"},
                ],
            },
            "comedy": {
                "title": "这段你笑了吗？",
                "options": [
                    {"key": "haha", "label": "哈哈哈"},
                    {"key": "funny", "label": "太逗了"},
                    {"key": "lmao", "label": "笑不活了"},
                ],
            },
            "heartbreak": {
                "title": "看到这里你想说什么？",
                "options": [
                    {"key": "feel_bad", "label": "心疼她"},
                    {"key": "stop", "label": "别虐了"},
                    {"key": "angry", "label": "气死我了"},
                ],
            },
            "sweet_moment": {
                "title": "这一刻你磕到了吗？",
                "options": [
                    {"key": "sweet", "label": "太甜了"},
                    {"key": "cp", "label": "磕到了"},
                    {"key": "again", "label": "再来一遍"},
                ],
            },
            "reversal": {
                "title": "这个反转你猜到了吗？",
                "options": [
                    {"key": "shock", "label": "震惊"},
                    {"key": "unexpected", "label": "没想到"},
                    {"key": "knew_it", "label": "早猜到了"},
                ],
            },
        }
        dm_overrides = {
            "emotional_burst": "情绪要爆了",
            "comedy": "注意，这里很好笑",
            "heartbreak": "小心破防",
            "sweet_moment": "甜度超标",
            "reversal": "反转来了",
        }
        base.update(overrides.get(highlight_type, {}))
        base["xiaomo_danmaku"] = {**xiaomo_danmaku, "text": dm_overrides.get(highlight_type, "你怎么看这一段？")}
        return base


def clamp_duration(start_ms: int, end_ms: int, highlight_type: str, video_duration_ms: int) -> tuple[int, int]:
    """根据类型规则修正高光点持续时间"""
    min_dur, max_dur = HIGHLIGHT_DURATION_RULES.get(highlight_type, (3000, 8000))
    dur = end_ms - start_ms

    if dur < min_dur:
        end_ms = start_ms + min_dur
    elif dur > max_dur:
        end_ms = start_ms + max_dur

    if start_ms < 0:
        start_ms = 0
    if end_ms > video_duration_ms:
        end_ms = video_duration_ms
    if start_ms >= end_ms:
        end_ms = start_ms + min_dur

    return start_ms, end_ms


def merge_adjacent(candidates: list[dict], video_duration_ms: int, merge_gap_ms: int = 10000) -> list[dict]:
    """合并相邻且同类型的高光点（间隔 < merge_gap_ms）"""
    if not candidates:
        return []
    merged = []
    current = dict(candidates[0])
    for c in candidates[1:]:
        if c["highlight_type"] == current["highlight_type"] and \
           c["start_time_ms"] - current["end_time_ms"] < merge_gap_ms:
            current["end_time_ms"] = c["end_time_ms"]
            current["confidence"] = max(current.get("confidence", 0), c.get("confidence", 0))
            if c.get("confidence", 0) > current.get("confidence", 0):
                current["title"] = c["title"]
                current["description"] = c["description"]
                current["evidence"] = c.get("evidence", {})
        else:
            merged.append(current)
            current = dict(c)
    merged.append(current)
    return merged


def remove_dense(
    candidates: list[dict],
    max_per_minute: int = 1,
    max_per_episode: int = 8,
    min_gap_ms: int = 20000,
    same_type_min_gap_ms: int = 45000,
    video_duration_ms: int = 0,
) -> list[dict]:
    """删除过密高光点"""
    if not candidates:
        return []

    # 按 start_time_ms 升序
    candidates.sort(key=lambda x: x["start_time_ms"])

    # 同类型最小间隔过滤
    filtered = []
    last_by_type: dict[str, int] = {}
    for c in candidates:
        ht = c["highlight_type"]
        last_end = last_by_type.get(ht)
        if last_end is not None and c["start_time_ms"] - last_end < same_type_min_gap_ms:
            continue
        filtered.append(c)
        last_by_type[ht] = c["end_time_ms"]

    # 任意两高光点最小间隔过滤
    result = []
    last_end_ms = -min_gap_ms
    for c in filtered:
        if c["start_time_ms"] - last_end_ms < min_gap_ms:
            # 保留 confidence 更高的
            if result and c.get("confidence", 0) > result[-1].get("confidence", 0):
                result[-1] = c
            continue
        result.append(c)
        last_end_ms = c["end_time_ms"]

    # 每分钟最多 max_per_minute 个
    if max_per_minute > 0 and len(result) > 0:
        minute_filtered = []
        minute_bucket: dict[int, list[dict]] = {}
        for c in result:
            bucket = c["start_time_ms"] // 60000
            minute_bucket.setdefault(bucket, []).append(c)
        for _bucket, items in sorted(minute_bucket.items()):
            items.sort(key=lambda x: x.get("confidence", 0), reverse=True)
            minute_filtered.extend(items[:max_per_minute])
        minute_filtered.sort(key=lambda x: x["start_time_ms"])
        result = minute_filtered

    # 每集最多 max_per_episode 个
    if len(result) > max_per_episode:
        result.sort(key=lambda x: x.get("confidence", 0), reverse=True)
        result = result[:max_per_episode]
        result.sort(key=lambda x: x["start_time_ms"])

    return result


def post_process_highlights(
    candidates: list[dict],
    video_duration_ms: int,
    min_confidence: float = 0.70,
    max_per_minute: int = 1,
    max_per_episode: int = 8,
    min_gap_ms: int = 20000,
    same_type_min_gap_ms: int = 45000,
) -> list[dict]:
    """高光点后处理总入口"""
    processed = []

    for c in candidates:
        # 过滤无效类型
        if c.get("highlight_type") not in VALID_HIGHLIGHT_TYPES:
            continue

        # 过滤低置信度
        confidence = c.get("confidence", 0)
        if confidence < min_confidence:
            continue

        # 修正时间范围
        start_ms = int(c.get("start_time_ms", 0))
        end_ms = int(c.get("end_time_ms", 0))
        start_ms, end_ms = clamp_duration(start_ms, end_ms, c["highlight_type"], video_duration_ms)

        processed.append({
            "highlight_type": c["highlight_type"],
            "start_time_ms": start_ms,
            "end_time_ms": end_ms,
            "title": c.get("title", ""),
            "description": c.get("description", ""),
            "confidence": confidence,
            "evidence": c.get("evidence", {}),
            "interaction_type": INTERACTION_TYPE_MAP.get(c["highlight_type"], "support_button"),
            "xiaomo_gif_code": c["highlight_type"],
            "interaction_config": build_interaction_config(c["highlight_type"]),
        })

    # 合并相邻同类型
    processed = merge_adjacent(processed, video_duration_ms)

    # 去重去密
    processed = remove_dense(
        processed,
        max_per_minute=max_per_minute,
        max_per_episode=max_per_episode,
        min_gap_ms=min_gap_ms,
        same_type_min_gap_ms=same_type_min_gap_ms,
        video_duration_ms=video_duration_ms,
    )

    return processed
