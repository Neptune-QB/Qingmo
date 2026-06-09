import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\Desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()
c.execute("""
CREATE TABLE IF NOT EXISTS episode_comments_new(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL,
    user_id TEXT NOT NULL,
    nickname TEXT NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
""")
conn.commit()
print("✅ 表创建完成！")
c.execute("INSERT OR IGNORE INTO episode_comments_new(episode_id, user_id, nickname, text) VALUES(?,?,?,?)", (1, "DONE", "青墨官方", "评论功能100%完成"))
conn.commit()
print("✅ 测试数据写入！")
conn.close()
