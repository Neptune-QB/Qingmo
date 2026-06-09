import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()
c.execute("PRAGMA table_info(episode_comments)")
for col in c.fetchall():
    print(col)
conn.close()
