import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "ju_flash.db")

print("🔍 扫描数据库中所有表...")
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()
c.execute("SELECT name FROM sqlite_master WHERE type='table';")
all_tables_before = sorted([x[0] for x in c.fetchall()])
print(f"清理前所有表: {all_tables_before}")

# 青墨正式16张标准生产表白名单
ALLOWED = {
    # 内容层
    "dramas", "episodes", "danmaku", "highlights",
    # RAG知识库层
    "drama_characters", "drama_summaries", "drama_tags", "drama_timeline",
    # 互动层
    "episode_comments_new", "episode_likes", "user_favorites", "user_interactions", "user_progress",
    # 用户与Agent层
    "users", "user_chat_sessions", "user_chat_messages"
}

drop_list = [t for t in all_tables_before if t not in ALLOWED and not t.startswith("sqlite_")]
print(f"\n🗑️ 将删除冗余临时表: {drop_list}")
for t in drop_list:
    c.execute(f"DROP TABLE IF EXISTS [{t}];")
    print(f"  ✅ 已删除表 {t}")

c.execute("SELECT name FROM sqlite_master WHERE type='table';")
all_tables_after = sorted([x[0] for x in c.fetchall()])
conn.commit()

print(f"\n✅ 清理完成，剩余正式生产表共 {len(all_tables_after)} 张:")
for t in all_tables_after:
    print(f"  - {t}")

conn.close()
