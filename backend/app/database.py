import sqlite3
import os
from typing import Optional
from app.config import settings

DB_PATH = os.path.join(os.path.dirname(os.path.dirname(__file__)), "ju_flash.db")


def get_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_connection()
    cursor = conn.cursor()

    cursor.executescript("""
        CREATE TABLE IF NOT EXISTS dramas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            title TEXT NOT NULL,
            author TEXT DEFAULT '',
            description TEXT DEFAULT '',
            cover_url TEXT DEFAULT '',
            category TEXT DEFAULT '',
            total_episodes INTEGER DEFAULT 0
        );

        CREATE TABLE IF NOT EXISTS drama_tags (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            tag TEXT NOT NULL,
            FOREIGN KEY (drama_id) REFERENCES dramas(id)
        );

        CREATE TABLE IF NOT EXISTS episodes (
            episode_id INTEGER PRIMARY KEY,
            drama_id INTEGER NOT NULL,
            episode_num INTEGER NOT NULL,
            title TEXT DEFAULT '',
            duration INTEGER DEFAULT 0,
            video_url TEXT DEFAULT '',
            thumbnail_url TEXT DEFAULT '',
            FOREIGN KEY (drama_id) REFERENCES dramas(id)
        );

        CREATE TABLE IF NOT EXISTS highlights (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            episode_id INTEGER NOT NULL,
            time REAL NOT NULL,
            type TEXT NOT NULL,
            title TEXT DEFAULT '',
            widget_type TEXT DEFAULT 'emotion',
            options TEXT DEFAULT NULL,
            emotion_hints TEXT DEFAULT NULL,
            duration INTEGER DEFAULT 15,
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
        );

        CREATE TABLE IF NOT EXISTS user_progress (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            episode_id INTEGER NOT NULL,
            progress INTEGER DEFAULT 0,
            watched INTEGER DEFAULT 0,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(episode_id)
        );

        CREATE TABLE IF NOT EXISTS user_interactions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            episode_id INTEGER NOT NULL,
            highlight_id INTEGER,
            module_id TEXT NOT NULL,
            interaction_data TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id),
            FOREIGN KEY (highlight_id) REFERENCES highlights(id)
        );

        CREATE INDEX IF NOT EXISTS idx_interactions_user ON user_interactions(user_id);
        CREATE INDEX IF NOT EXISTS idx_interactions_episode ON user_interactions(episode_id);

        CREATE TABLE IF NOT EXISTS user_profiles (
            user_id TEXT PRIMARY KEY,
            watch_history TEXT,
            favorite_dramas TEXT,
            interaction_stats TEXT,
            preferences TEXT,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    """)

    conn.commit()
    conn.close()


def migrate_add_columns():
    """安全迁移：给已存在的旧数据库补充新字段，不会删除数据"""
    conn = get_connection()
    cursor = conn.cursor()

    # highlights.time 改为 REAL 支持小数秒
    try:
        cursor.execute("ALTER TABLE highlights RENAME TO highlights_old")
        cursor.execute("""
            CREATE TABLE highlights (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                episode_id INTEGER NOT NULL,
                time REAL NOT NULL,
                type TEXT NOT NULL,
                title TEXT DEFAULT '',
                widget_type TEXT DEFAULT 'emotion',
                options TEXT DEFAULT NULL,
                emotion_hints TEXT DEFAULT NULL,
                duration INTEGER DEFAULT 15,
                FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
            )
        """)
        cursor.execute("""
            INSERT INTO highlights(id, episode_id, time, type, title, widget_type, options)
            SELECT id, episode_id, CAST(time AS REAL), type, title, 
                   COALESCE(widget_type, 'emotion'), options
            FROM highlights_old
        """)
        cursor.execute("DROP TABLE highlights_old")
    except sqlite3.OperationalError:
        pass  # 迁移已执行过，静默跳过
    # 检查新增字段是否存在
    for col in ["widget_type", "emotion_hints", "duration"]:
        try:
            cursor.execute(f"ALTER TABLE highlights ADD COLUMN {col} TEXT DEFAULT NULL")
        except sqlite3.OperationalError:
            pass

    # user_interactions 表
    try:
        cursor.execute("SELECT 1 FROM user_interactions LIMIT 1")
    except sqlite3.OperationalError:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS user_interactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id TEXT NOT NULL,
                episode_id INTEGER NOT NULL,
                highlight_id INTEGER,
                module_id TEXT NOT NULL,
                interaction_data TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (episode_id) REFERENCES episodes(episode_id),
                FOREIGN KEY (highlight_id) REFERENCES highlights(id)
            )
        """)

    conn.commit()
    conn.close()
