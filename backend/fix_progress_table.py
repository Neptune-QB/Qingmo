"""重建 user_progress 表，更新 UNIQUE 约束为 (user_id, episode_id)"""
import sqlite3
import os

DB = os.path.join(os.path.dirname(__file__), "ju_flash.db")
conn = sqlite3.connect(DB)
c = conn.cursor()

c.execute("BEGIN")
c.execute("""
    CREATE TABLE IF NOT EXISTS user_progress_v2 (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id TEXT NOT NULL DEFAULT '0',
        episode_id INTEGER NOT NULL,
        progress INTEGER DEFAULT 0,
        watched INTEGER DEFAULT 0,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(user_id, episode_id)
    )
""")
c.execute("""
    INSERT OR IGNORE INTO user_progress_v2 (id, user_id, episode_id, progress, watched, updated_at)
    SELECT id, COALESCE(user_id, '0'), episode_id, progress, watched, updated_at FROM user_progress
""")
c.execute("DROP TABLE user_progress")
c.execute("ALTER TABLE user_progress_v2 RENAME TO user_progress")
c.execute("COMMIT")

c.execute("SELECT COUNT(*) FROM user_progress")
print(f"OK, {c.fetchone()[0]} rows migrated")

c.execute("PRAGMA table_info(user_progress)")
for col in c.fetchall():
    print(f"  col: {col}")

c.execute("SELECT sql FROM sqlite_master WHERE name='user_progress'")
print(c.fetchone()[0])

conn.close()
