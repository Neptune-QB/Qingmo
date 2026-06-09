import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\Desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()
try:
    c.execute("ALTER TABLE episode_comments_new ADD COLUMN reply_to_nickname TEXT DEFAULT ''")
    print("✅ 新增字段 reply_to_nickname 成功")
except sqlite3.OperationalError as e:
    print(f"ℹ️  字段已存在或无需添加: {e}")
conn.commit()
conn.close()
