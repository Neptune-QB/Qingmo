import sqlite3
import os
from typing import Optional

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
            time INTEGER NOT NULL,
            type TEXT NOT NULL,
            title TEXT DEFAULT '',
            widget_type TEXT DEFAULT 'emoji',
            options TEXT DEFAULT NULL,
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
    """)

    conn.commit()
    conn.close()
