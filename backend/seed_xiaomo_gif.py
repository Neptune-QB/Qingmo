"""
种子脚本：从 Android drawable 目录扫描小墨 GIF 动效文件，
幂等写入 xiaomo_gif 表（已存在的 code 自动跳过）。
"""
import os
import sys
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from app.database import init_db, get_connection

# Android drawable 中 xiaomo GIF 目录
PROJECT_ROOT = Path(__file__).parent.parent
DRAWABLE_XIAOMO = PROJECT_ROOT / "android" / "app" / "src" / "main" / "res" / "drawable" / "xiaomo"

# GIF 种子数据：{文件夹名: (中文名称, 描述, 高光类型)}
GIF_SEEDS = {
    "cliffhanger":     ("悬念钩子",    "剧情悬念反转前的小墨期待动效", "cliffhanger"),
    "choice_point":    ("选择节点",    "观众需要做出选择的剧情分歧点", "choice_point"),
    "emotional_burst": ("情感爆发",    "角色情绪激烈爆发时的感染动效", "emotional_burst"),
    "power_moment":    ("爽点时刻",    "主角打脸逆袭的高光爽点动效",   "power_moment"),
    "comedy":          ("搞笑瞬间",    "轻松搞笑的幽默桥段动效",       "comedy"),
    "suspense":        ("紧张悬念",    "紧张悬疑氛围的压迫感动效",     "suspense"),
    "heartbreak":      ("虐心时刻",    "虐心泪目片段的伤感动效",       "heartbreak"),
    "sweet_moment":    ("甜蜜时刻",    "甜宠撒糖的浪漫心动效",         "sweet_moment"),
    "reversal":        ("惊天反转",    "剧情大反转时的震撼动效",       "reversal"),
    "slapback":        ("打脸爽点",    "打脸反杀的痛快解气动效",       "slapback"),
}


def seed():
    init_db()
    conn = get_connection()
    cursor = conn.cursor()

    inserted = 0
    skipped = 0
    missing = 0

    for folder, (name, description, highlight_type) in GIF_SEEDS.items():
        folder_path = DRAWABLE_XIAOMO / folder
        gif_file = folder_path / f"{folder}.gif"

        # 检查 GIF 文件是否存在
        if not gif_file.exists():
            print(f"  [WARN] GIF 文件不存在: {gif_file}")
            missing += 1
            continue

        code = folder
        gif_url = str(gif_file)

        # 幂等：code 已存在则跳过
        cursor.execute("SELECT id FROM xiaomo_gif WHERE code = ?", (code,))
        if cursor.fetchone():
            skipped += 1
            continue

        cursor.execute(
            "INSERT INTO xiaomo_gif (code, name, gif_url, highlight_type, description) VALUES (?, ?, ?, ?, ?)",
            (code, name, gif_url, highlight_type, description),
        )
        inserted += 1

    conn.commit()

    # 汇总统计
    cursor.execute("SELECT COUNT(*) as cnt FROM xiaomo_gif")
    total = cursor.fetchone()["cnt"]
    conn.close()

    print(f"种子数据写入完成: 新增 {inserted}, 跳过(已存在) {skipped}, 文件缺失 {missing}, 表中总数 {total}")
    return total


if __name__ == "__main__":
    seed()
