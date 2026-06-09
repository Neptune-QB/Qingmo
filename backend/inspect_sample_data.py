import sqlite3
import os
import json

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

# 全部正式业务表白名单
tables = [
    "dramas", "episodes", "highlights", "danmaku",
    "drama_characters", "drama_summaries", "drama_tags", "drama_timeline",
    "users", "user_chat_sessions", "user_chat_messages",
    "episode_comments_new", "episode_likes", "user_favorites", "user_interactions", "user_progress"
]

print("=" * 80)
print("🔍 所有正式数据表样例数据抽查报告")
print("=" * 80)

for t in tables:
    print(f"\n\n📋 表名: [{t}]")
    print("-" * 60)
    try:
        c.execute(f"PRAGMA table_info([{t}])")
        cols = [x[1] for x in c.fetchall()]
        print(f"字段列表: {cols}")
        c.execute(f"SELECT * FROM [{t}] LIMIT 5")
        rows = c.fetchall()
        print(f"样例数据(前{len(rows)}条):")
        for idx, row in enumerate(rows):
            print(f"  #{idx+1}: ", end="")
            for k, v in zip(cols, row):
                # JSON字段格式化显示
                if k in ("device_ids", "options", "emotion_hints") and isinstance(v, str):
                    try: v = json.loads(v)
                    except: pass
                print(f"{k}={repr(v):<25}", end=" ")
            print()
        # 总数统计
        c.execute(f"SELECT COUNT(*) FROM [{t}]")
        total = c.fetchone()[0]
        print(f"  👉 该表总记录数: {total}")
    except Exception as e:
        print(f"  ❌ 读取失败: {e}")

print("\n" + "=" * 80)
print("✅ 所有表样例数据抽查完成！")
print("=" * 80)

conn.close()
