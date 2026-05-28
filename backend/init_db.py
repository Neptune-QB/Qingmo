"""
Initialize SQLite database with seed data.
If crawler/data/dramas_data.json exists, use scraped real data.
Otherwise, use placeholder data.
"""
import json
import os
import sys
import random
from pathlib import Path

sys.path.insert(0, os.path.dirname(__file__))
from app.database import init_db, get_connection

DATA_DIR = Path(__file__).parent / "crawler" / "data"
DRAMAS_JSON = DATA_DIR / "dramas_data.json"

HIGHLIGHT_TYPES = ["conflict", "twist", "sweet", "famous", "funny", "branch"]
HIGHLIGHT_TITLES = {
    "conflict": "\u5267\u60C5\u51B2\u7A81",
    "twist": "\u60CA\u5929\u53CD\u8F6C",
    "sweet": "\u751C\u871C\u65F6\u523B",
    "famous": "\u540D\u573A\u9762",
    "funny": "\u6458\u7B11\u6865\u6BB5",
    "branch": "\u5267\u60C5\u5206\u5C94\u70B9",
}

# Fallback placeholder data when no scraped data available
PLACEHOLDER_DRAMAS = [
    {"name": "\u5317\u6D3E\u5BFB\u5B9D\u7B14\u8BB0", "author": "", "description": "",
     "cover_url": "covers/01.jpg", "tags": ["\u63A2\u9669"], "episodes": 50},
    {"name": "\u5929\u4E0B\u7B2C\u4E00\u7EA8\u7ED4", "author": "", "description": "",
     "cover_url": "covers/02.jpg", "tags": ["\u53E4\u88C5"], "episodes": 60},
    {"name": "\u5341\u516B\u5C81\u592A\u5976\u5976\u9A7E\u5230\u7B2C\u4E09\u90E8", "author": "", "description": "",
     "cover_url": "covers/03.jpg", "tags": ["\u7A7F\u8D8A"], "episodes": 45},
    {"name": "\u5E78\u5F97\u76F8\u9047\u79BB\u5A5A\u65F6", "author": "", "description": "",
     "cover_url": "covers/04.jpg", "tags": ["\u90FD\u5E02"], "episodes": 55},
    {"name": "\u8352\u5E74\u5168\u6751\u5543\u6811\u76AE\u6211\u6709\u7CFB\u7EDF\u6EE1\u4ED3\u8089", "author": "", "description": "",
     "cover_url": "covers/05.jpg", "tags": ["\u5E74\u4EE3"], "episodes": 70},
    {"name": "\u5BB6\u91CC\u5BB6\u5916", "author": "", "description": "",
     "cover_url": "covers/06.jpg", "tags": ["\u5BB6\u5EAD"], "episodes": 40},
    {"name": "\u4E91\u6E3A1", "author": "", "description": "",
     "cover_url": "covers/07.jpg", "tags": ["\u4ED9\u4FA0"], "episodes": 65},
    {"name": "\u6495\u591C", "author": "", "description": "",
     "cover_url": "covers/08.jpg", "tags": ["\u60AC\u7591"], "episodes": 48},
    {"name": "\u90A3\u5E74\u51AC\u81F3", "author": "", "description": "",
     "cover_url": "covers/09.jpg", "tags": ["\u6821\u56ED"], "episodes": 36},
    {"name": "\u5317\u5F80", "author": "", "description": "",
     "cover_url": "covers/10.jpg", "tags": ["\u5E74\u4EE3"], "episodes": 52},
]


def load_dramas() -> list[dict]:
    """Load dramas from scraped JSON or fallback to placeholders."""
    if DRAMAS_JSON.exists():
        print(f"Loading from {DRAMAS_JSON}")
        data = json.loads(DRAMAS_JSON.read_text(encoding="utf-8"))
        result = []
        for i, d in enumerate(data):
            chapters = d.get("chapters", [])
            ep_count = len(chapters) if chapters else 50
            result.append({
                "name": d.get("title", d["name"]),
                "author": d.get("author", ""),
                "description": d.get("description", ""),
                "cover_url": d.get("local_cover", f"covers/{i+1:02d}.jpg"),
                "tags": d.get("tags", []),
                "episodes": ep_count,
                "chapter_titles": chapters,
            })
        return result
    else:
        print(f"No {DRAMAS_JSON} found, using placeholder data.")
        return PLACEHOLDER_DRAMAS


def seed():
    init_db()
    dramas = load_dramas()
    conn = get_connection()
    try:
        cursor = conn.cursor()

        # Clear existing data
        cursor.execute("DELETE FROM highlights")
        cursor.execute("DELETE FROM episodes")
        cursor.execute("DELETE FROM drama_tags")
        cursor.execute("DELETE FROM dramas")

        for i, drama in enumerate(dramas):
            title = drama["name"]
            author = drama["author"]
            description = drama["description"]
            cover_url = drama["cover_url"]
            tags = drama.get("tags", [])
            episode_count = drama.get("episodes", 50)
            chapter_titles = drama.get("chapter_titles", [])

            cursor.execute(
                "INSERT INTO dramas (id, title, author, description, cover_url, category, total_episodes) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (i + 1, title, author, description, cover_url, tags[0] if tags else "", episode_count),
            )
            for tag in tags[:5]:
                cursor.execute("INSERT INTO drama_tags (drama_id, tag) VALUES (?, ?)", (i + 1, tag))

            for ep_num in range(1, episode_count + 1):
                ep_id = i * 1000 + ep_num
                ep_title = chapter_titles[ep_num - 1] if ep_num - 1 < len(chapter_titles) else f"\u7B2C{ep_num}\u96C6"
                cursor.execute(
                    "INSERT INTO episodes (episode_id, drama_id, episode_num, title, duration, video_url, thumbnail_url) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (ep_id, i + 1, ep_num, ep_title, 300, "videos/placeholder.mp4", ""),
                )
                num_h = random.randint(1, 3)
                for _ in range(num_h):
                    h_type = random.choice(HIGHLIGHT_TYPES)
                    h_time = random.randint(10, 280)
                    widget = "branch" if h_type == "branch" else "emoji"
                    cursor.execute(
                        "INSERT INTO highlights (episode_id, time, type, title, widget_type) VALUES (?, ?, ?, ?, ?)",
                        (ep_id, h_time, h_type, HIGHLIGHT_TITLES[h_type], widget),
                    )

        conn.commit()
        print(f"Seed complete: {len(dramas)} dramas, ~{sum(d.get('episodes', 50) for d in dramas)} episodes inserted.")
    finally:
        conn.close()


if __name__ == "__main__":
    seed()
