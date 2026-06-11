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
            description TEXT DEFAULT '',
            cover_url TEXT DEFAULT '',
            total_episodes INTEGER DEFAULT 0,
            tags TEXT DEFAULT ''
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

        CREATE TABLE IF NOT EXISTS drama_highlight (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            highlight_type TEXT NOT NULL,
            start_time_ms INTEGER NOT NULL,
            end_time_ms INTEGER NOT NULL,
            hint_offset_ms INTEGER DEFAULT 2000,
            title TEXT NOT NULL,
            description TEXT DEFAULT NULL,
            interaction_type TEXT NOT NULL,
            interaction_config TEXT NOT NULL,
            xiaomo_gif_code TEXT NOT NULL,
            priority INTEGER DEFAULT 0,
            status TEXT DEFAULT 'enabled',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (drama_id) REFERENCES dramas(id),
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id),
            FOREIGN KEY (xiaomo_gif_code) REFERENCES xiaomo_gif(code)
        );

        CREATE INDEX IF NOT EXISTS idx_dh_episode_time ON drama_highlight(episode_id, start_time_ms, end_time_ms);
        CREATE INDEX IF NOT EXISTS idx_dh_drama_episode ON drama_highlight(drama_id, episode_id);
        CREATE INDEX IF NOT EXISTS idx_dh_type ON drama_highlight(highlight_type);
        CREATE INDEX IF NOT EXISTS idx_dh_status ON drama_highlight(status);

        CREATE TABLE IF NOT EXISTS user_progress (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL DEFAULT '0',
            episode_id INTEGER NOT NULL,
            progress INTEGER DEFAULT 0,
            watched INTEGER DEFAULT 0,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(user_id, episode_id)
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
            FOREIGN KEY (highlight_id) REFERENCES drama_highlight(id)
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

        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            nickname TEXT DEFAULT '',
            avatar TEXT DEFAULT '',
            device_ids TEXT DEFAULT '[]',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS drama_summaries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            summary TEXT DEFAULT '',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (drama_id) REFERENCES dramas(id),
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
        );

        CREATE TABLE IF NOT EXISTS drama_characters (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            role TEXT DEFAULT '',
            description TEXT DEFAULT '',
            relationships TEXT DEFAULT '[]',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (drama_id) REFERENCES dramas(id)
        );

        CREATE TABLE IF NOT EXISTS drama_timeline (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            time_sec REAL DEFAULT 0,
            event_type TEXT DEFAULT '',
            event_desc TEXT DEFAULT '',
            characters TEXT DEFAULT '',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (drama_id) REFERENCES dramas(id),
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
        );

        CREATE TABLE IF NOT EXISTS danmaku (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            episode_id INTEGER NOT NULL,
            text TEXT NOT NULL,
            time_sec REAL DEFAULT 0,
            color TEXT DEFAULT '#ffffff',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
        );

        CREATE TABLE IF NOT EXISTS user_interaction (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT DEFAULT NULL,
            device_id TEXT DEFAULT NULL,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            highlight_id INTEGER NOT NULL,
            interaction_type TEXT NOT NULL,
            option_key TEXT DEFAULT NULL,
            option_label TEXT DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (highlight_id) REFERENCES drama_highlight(id)
        );

        CREATE INDEX IF NOT EXISTS idx_ui_highlight ON user_interaction(highlight_id);
        CREATE INDEX IF NOT EXISTS idx_ui_episode ON user_interaction(episode_id);
        CREATE INDEX IF NOT EXISTS idx_ui_device_hl ON user_interaction(device_id, highlight_id);
        CREATE INDEX IF NOT EXISTS idx_ui_created ON user_interaction(created_at);

        CREATE TABLE IF NOT EXISTS episode_likes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            episode_id INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(user_id, episode_id),
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
        );

        CREATE TABLE IF NOT EXISTS user_favorites (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            drama_id INTEGER NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(user_id, drama_id),
            FOREIGN KEY (drama_id) REFERENCES dramas(id)
        );

        CREATE TABLE IF NOT EXISTS episode_comments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            episode_id INTEGER NOT NULL,
            parent_id INTEGER DEFAULT 0,
            reply_to_user_id TEXT DEFAULT '',
            reply_to_nickname TEXT DEFAULT '',
            user_id TEXT NOT NULL,
            nickname TEXT DEFAULT '热心网友',
            avatar_url TEXT DEFAULT '',
            text TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
        );

        CREATE TABLE IF NOT EXISTS highlight_votes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            highlight_id INTEGER NOT NULL UNIQUE,
            question TEXT NOT NULL,
            option_a TEXT NOT NULL,
            option_b TEXT NOT NULL,
            FOREIGN KEY (highlight_id) REFERENCES drama_highlight(id)
        );

        CREATE TABLE IF NOT EXISTS highlight_vote_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vote_id INTEGER NOT NULL,
            user_id TEXT NOT NULL,
            choice TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(vote_id, user_id),
            FOREIGN KEY (vote_id) REFERENCES highlight_votes(id)
        );

        CREATE TABLE IF NOT EXISTS user_quiz_scores (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            highlight_id INTEGER NOT NULL,
            score INTEGER NOT NULL DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(user_id, highlight_id)
        );

        CREATE TABLE IF NOT EXISTS user_notes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            episode_id INTEGER NOT NULL,
            drama_id INTEGER NOT NULL DEFAULT 0,
            note_text TEXT NOT NULL,
            time_sec REAL NOT NULL DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (episode_id) REFERENCES episodes(episode_id)
        );

        CREATE TABLE IF NOT EXISTS branch_votes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL UNIQUE,
            question TEXT NOT NULL,
            option_a TEXT NOT NULL,
            option_b TEXT NOT NULL,
            deadline TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (drama_id) REFERENCES dramas(id)
        );

        CREATE TABLE IF NOT EXISTS branch_vote_records (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            vote_id INTEGER NOT NULL,
            user_id TEXT NOT NULL,
            choice TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(vote_id, user_id),
            FOREIGN KEY (vote_id) REFERENCES branch_votes(id)
        );

        CREATE TABLE IF NOT EXISTS user_chat_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id TEXT NOT NULL,
            title TEXT DEFAULT '新对话',
            drama_id INTEGER DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS user_chat_messages (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id INTEGER NOT NULL,
            role TEXT NOT NULL,
            content TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (session_id) REFERENCES user_chat_sessions(id)
        );

        CREATE INDEX IF NOT EXISTS idx_chat_sessions_user ON user_chat_sessions(user_id);
        CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON user_chat_messages(session_id);

        -- xiaomo_gif 小墨GIF动效资源表（drama_highlight 外键依赖）
        CREATE TABLE IF NOT EXISTS xiaomo_gif (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            gif_url TEXT NOT NULL,
            highlight_type TEXT,
            description TEXT DEFAULT '',
            status TEXT NOT NULL DEFAULT 'published',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_xiaomo_gif_highlight_type ON xiaomo_gif(highlight_type);
        CREATE INDEX IF NOT EXISTS idx_xiaomo_gif_status ON xiaomo_gif(status);

        -- video_analysis_task 视频分析任务记录
        CREATE TABLE IF NOT EXISTS video_analysis_task (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            video_path TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending',
            progress INTEGER DEFAULT 0,
            result_json TEXT DEFAULT NULL,
            error_message TEXT DEFAULT NULL,
            started_at TEXT DEFAULT NULL,
            finished_at TEXT DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_vat_episode ON video_analysis_task(episode_id, created_at);

        -- episode_transcript 每集台词表
        CREATE TABLE IF NOT EXISTS episode_transcript (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            start_time_ms INTEGER NOT NULL,
            end_time_ms INTEGER NOT NULL,
            speaker TEXT DEFAULT '',
            text TEXT NOT NULL,
            confidence REAL DEFAULT NULL,
            source_type TEXT NOT NULL DEFAULT 'asr',
            language TEXT DEFAULT 'zh',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_et_episode_time ON episode_transcript(episode_id, start_time_ms);
        CREATE INDEX IF NOT EXISTS idx_et_drama_episode ON episode_transcript(drama_id, episode_id);

        -- episode_scene_segment 剧情片段切分
        CREATE TABLE IF NOT EXISTS episode_scene_segment (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            start_time_ms INTEGER NOT NULL,
            end_time_ms INTEGER NOT NULL,
            summary TEXT NOT NULL,
            dialogue_text TEXT DEFAULT '',
            visual_summary TEXT DEFAULT '',
            emotion_tags_json TEXT DEFAULT '[]',
            candidate_highlight_type TEXT DEFAULT NULL,
            confidence REAL DEFAULT NULL,
            evidence_json TEXT DEFAULT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_ess_episode_time ON episode_scene_segment(episode_id, start_time_ms);
        CREATE INDEX IF NOT EXISTS idx_ess_candidate_hl ON episode_scene_segment(candidate_highlight_type);

        -- episode_content_summary 每集内容整体摘要
        CREATE TABLE IF NOT EXISTS episode_content_summary (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            drama_id INTEGER NOT NULL,
            episode_id INTEGER NOT NULL,
            title TEXT DEFAULT '',
            short_summary TEXT DEFAULT '',
            long_summary TEXT DEFAULT '',
            character_actions_json TEXT DEFAULT NULL,
            plot_points_json TEXT DEFAULT NULL,
            conflict_json TEXT DEFAULT NULL,
            ending_hook TEXT DEFAULT NULL,
            generated_by TEXT DEFAULT 'ai_video_analysis',
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
    """)

    conn.commit()
    conn.close()


def migrate_add_columns():
    """安全迁移：给已存在的旧数据库补充新字段，不会删除数据"""
    conn = get_connection()
    cursor = conn.cursor()

    # drama_highlight 新增 AI 分析字段
    for col, col_type in [
        ("source_type", "TEXT DEFAULT 'manual'"),
        ("confidence", "REAL DEFAULT NULL"),
        ("evidence_json", "TEXT DEFAULT NULL"),
        ("review_status", "TEXT DEFAULT 'approved'"),
        ("bubble_text", "TEXT DEFAULT ''"),
    ]:
        try:
            cursor.execute(f"ALTER TABLE drama_highlight ADD COLUMN {col} {col_type}")
        except sqlite3.OperationalError:
            pass

    # episode_comments 评论表 reply_to_nickname 字段安全迁移
    for col, col_type in [("reply_to_nickname", "TEXT DEFAULT ''"), ("parent_id", "INTEGER DEFAULT 0")]:
        try:
            cursor.execute(f"ALTER TABLE episode_comments ADD COLUMN {col} {col_type}")
        except sqlite3.OperationalError:
            pass

    # user_progress 表 user_id 字段安全迁移（支持多用户进度隔离）
    try:
        cursor.execute("ALTER TABLE user_progress ADD COLUMN user_id TEXT NOT NULL DEFAULT '0'")
    except sqlite3.OperationalError:
        pass

    # dramas 表新增 tags 字段（合并原来的独立 drama_tags 表）
    try:
        cursor.execute("ALTER TABLE dramas ADD COLUMN tags TEXT DEFAULT ''")
    except sqlite3.OperationalError:
        pass

    # 安全迁移：移除冗余 author / category 字段，SQLite 不支持直接 DROP COLUMN，用重建表方式
    try:
        cursor.execute("PRAGMA table_info(dramas)")
        cols = [c["name"] for c in cursor.fetchall()]
        if "author" in cols or "category" in cols:
            cursor.execute("ALTER TABLE dramas RENAME TO dramas_old")
            cursor.execute("""
                CREATE TABLE dramas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT DEFAULT '',
                    cover_url TEXT DEFAULT '',
                    total_episodes INTEGER DEFAULT 0,
                    tags TEXT DEFAULT ''
                )
            """)
            cursor.execute("""
                INSERT INTO dramas(id, title, description, cover_url, total_episodes, tags)
                SELECT id, title, description, cover_url, total_episodes, tags FROM dramas_old
            """)
            cursor.execute("DROP TABLE dramas_old")
            print("[MIGRATION] 已移除冗余 author 和 category 字段，表结构简化完成")
    except Exception:
        pass

    # 自动统计更新真实 total_episodes：完全由 episodes 实际数量计算，零硬编码
    cursor.execute("""
        UPDATE dramas SET total_episodes = (
            SELECT COUNT(*) FROM episodes WHERE episodes.drama_id = dramas.id
        )
    """)

    conn.commit()
    conn.close()
