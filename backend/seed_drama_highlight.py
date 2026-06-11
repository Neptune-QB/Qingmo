"""drama_highlight 测试种子数据——幂等运行"""
import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).parent / "ju_flash.db"

# interaction_type 映射规则
TYPE_TO_INTERACTION = {
    "cliffhanger": "support_button",
    "choice_point": "choice_panel",
    "emotional_burst": "reaction_panel",
    "power_moment": "support_button",
    "comedy": "reaction_panel",
    "suspense": "support_button",
    "heartbreak": "reaction_panel",
    "sweet_moment": "reaction_panel",
    "reversal": "reaction_panel",
    "slapback": "support_button",
}

# interaction_config 模板
INTERACTION_CONFIGS = {
    "cliffhanger": {"button_text": "我要看后续！", "effect": "shake"},
    "heartbreak": {"emotions": ["😭", "😢", "💔", "🥺", "虐到了"]},
    "slapback": {"button_text": "爽！解气！", "effect": "fire"},
    "reversal": {"emotions": ["😱", "🤯", "不会吧！", "反转了！"]},
    "sweet_moment": {"emotions": ["🥰", "❤️", "甜到了", "磕死我了"]},
}

# 5 条测试数据：drama_id=1, episode_id=1063（北派寻宝笔记第63集）
SEED_HIGHLIGHTS = [
    {
        "highlight_type": "cliffhanger",
        "start_time_ms": 8000,
        "end_time_ms": 13000,
        "title": "密道入口出现",
        "description": "主角发现隐藏密道入口，悬念陡升",
    },
    {
        "highlight_type": "heartbreak",
        "start_time_ms": 25000,
        "end_time_ms": 32000,
        "title": "伙伴为救主角牺牲",
        "description": "关键时刻伙伴舍命相救，主角崩溃痛哭",
    },
    {
        "highlight_type": "slapback",
        "start_time_ms": 45000,
        "end_time_ms": 52000,
        "title": "身份揭露打脸反派",
        "description": "主角真实身份曝光，反派震惊跪地",
    },
    {
        "highlight_type": "reversal",
        "start_time_ms": 70000,
        "end_time_ms": 78000,
        "title": "寻到的宝物竟是赝品",
        "description": "千辛万苦寻到的宝物居然是赝品，真正宝物另有下落",
    },
    {
        "highlight_type": "sweet_moment",
        "start_time_ms": 90000,
        "end_time_ms": 98000,
        "title": "男女主终于牵手",
        "description": "经历了各种误会的男女主终于心意相通牵手成功",
    },
]


def seed():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    # 幂等：先删该 episode 已有测试数据
    cur.execute("DELETE FROM drama_highlight WHERE episode_id = 1063")
    print(f"已清理 episode 1063 旧数据 ({cur.rowcount} 条)")

    inserted = 0
    for h in SEED_HIGHLIGHTS:
        h_type = h["highlight_type"]
        interaction_type = TYPE_TO_INTERACTION.get(h_type, "reaction_panel")
        interaction_config = json.dumps(INTERACTION_CONFIGS.get(h_type, {}))
        xiaomo_gif_code = h_type  # highlight_type 即对应 xiaomo_gif.code

        cur.execute(
            """INSERT INTO drama_highlight
               (drama_id, episode_id, highlight_type, start_time_ms, end_time_ms,
                hint_offset_ms, title, description, interaction_type,
                interaction_config, xiaomo_gif_code, priority, status)
               VALUES (1, 1063, ?, ?, ?, 2000, ?, ?, ?, ?, ?, 0, 'enabled')""",
            (h_type, h["start_time_ms"], h["end_time_ms"],
             h["title"], h["description"], interaction_type,
             interaction_config, xiaomo_gif_code),
        )
        inserted += 1

    conn.commit()

    # 验证
    cur.execute("SELECT id, highlight_type, title, start_time_ms, interaction_type, xiaomo_gif_code FROM drama_highlight WHERE episode_id = 1063 ORDER BY start_time_ms")
    rows = cur.fetchall()
    print(f"\n已插入 {inserted} 条测试数据：")
    for r in rows:
        print(f"  #{r[0]} | {r[1]:20s} | {r[2]:20s} | {r[3]}ms | {r[4]:16s} | gif={r[5]}")

    conn.close()
    print("\n种子数据写入完成。")


if __name__ == "__main__":
    import json
    seed()
