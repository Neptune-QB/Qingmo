import sqlite3
from datetime import datetime

conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

# 1. 创建正式 comments 表结构
c.execute('''
CREATE TABLE IF NOT EXISTS comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL,
    user_id TEXT NOT NULL,
    nickname TEXT NOT NULL,
    text TEXT NOT NULL,
    parent_id INTEGER NOT NULL DEFAULT 0,
    reply_to_nickname TEXT NOT NULL DEFAULT '',
    like_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL
)
''')
print('✅ 正式 comments 表创建完成')

# 2. 把 episode_comments_new 所有数据迁移过来，避免重复
c.execute('''
INSERT OR IGNORE INTO comments(id, episode_id, user_id, nickname, text, parent_id, reply_to_nickname, like_count, created_at)
SELECT id, episode_id, user_id, nickname, text, parent_id, reply_to_nickname, 0, created_at FROM episode_comments_new
''')
migrated = c.rowcount
print(f'✅ 迁移了 {migrated} 条真实用户评论')

conn.commit()

# 3. 验证数据
total = c.execute('SELECT COUNT(*) FROM comments').fetchone()[0]
print(f'✅ comments 表总记录数: {total}')
for r in c.execute('SELECT id, nickname, text, parent_id FROM comments ORDER BY id').fetchall():
    print(f'  [{r[0]}] {r[1]} → {r[2]} (parent={r[3]})')

conn.close()
