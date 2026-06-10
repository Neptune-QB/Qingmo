import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).parent / "ju_flash.db"

def restore_all_deleted_tables():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    print("[Restore] 重建所有之前删除的表...")

    # 1. users 正式用户主表
    cur.execute("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id TEXT UNIQUE NOT NULL,
        username TEXT UNIQUE NOT NULL,
        password_hash TEXT NOT NULL,
        nickname TEXT,
        avatar TEXT,
        device_ids TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)
    print("✅ 重建 users 表")

    # 2. user_profiles 用户画像表
    cur.execute("""
    CREATE TABLE IF NOT EXISTS user_profiles (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id TEXT UNIQUE NOT NULL,
        gender TEXT,
        birthday TEXT,
        bio TEXT,
        favorite_tags TEXT,
        last_watched_drama_id INTEGER,
        total_watch_duration INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)
    print("✅ 重建 user_profiles 表")

    # 3. highlight_votes 高光投票主表
    cur.execute("""
    CREATE TABLE IF NOT EXISTS highlight_votes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        highlight_id INTEGER NOT NULL,
        title TEXT NOT NULL,
        option_a TEXT NOT NULL,
        option_b TEXT NOT NULL,
        count_a INTEGER DEFAULT 0,
        count_b INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (highlight_id) REFERENCES highlights(id) ON DELETE CASCADE
    );
    """)
    print("✅ 重建 highlight_votes 表")

    # 4. highlight_vote_records 高光投票记录表
    cur.execute("""
    CREATE TABLE IF NOT EXISTS highlight_vote_records (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vote_id INTEGER NOT NULL,
        device_id TEXT NOT NULL,
        selected_option TEXT NOT NULL,
        voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (vote_id) REFERENCES highlight_votes(id) ON DELETE CASCADE
    );
    """)
    print("✅ 重建 highlight_vote_records 表")

    # 5. user_quiz_scores 用户问答题得分表
    cur.execute("""
    CREATE TABLE IF NOT EXISTS user_quiz_scores (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        highlight_id INTEGER NOT NULL,
        device_id TEXT NOT NULL,
        score INTEGER NOT NULL,
        answered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (highlight_id) REFERENCES highlights(id) ON DELETE CASCADE
    );
    """)
    print("✅ 重建 user_quiz_scores 表")

    # 6. branch_votes 全剧分支投票主表
    cur.execute("""
    CREATE TABLE IF NOT EXISTS branch_votes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        drama_id INTEGER NOT NULL,
        title TEXT NOT NULL,
        option_a TEXT NOT NULL,
        option_b TEXT NOT NULL,
        count_a INTEGER DEFAULT 0,
        count_b INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (drama_id) REFERENCES dramas(id) ON DELETE CASCADE
    );
    """)
    print("✅ 重建 branch_votes 表")

    # 7. branch_vote_records 全剧分支投票记录表
    cur.execute("""
    CREATE TABLE IF NOT EXISTS branch_vote_records (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vote_id INTEGER NOT NULL,
        device_id TEXT NOT NULL,
        selected_option TEXT NOT NULL,
        voted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (vote_id) REFERENCES branch_votes(id) ON DELETE CASCADE
    );
    """)
    print("✅ 重建 branch_vote_records 表")

    conn.commit()

    print("\n🎉 全部7张表重建完成！当前全库所有表清单：")
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;")
    for (t,) in cur.fetchall():
        print(f"   - {t}")

    conn.close()

if __name__ == "__main__":
    restore_all_deleted_tables()
