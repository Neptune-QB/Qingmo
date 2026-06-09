import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()
c.execute('DELETE FROM episode_comments')
conn.commit()
c.execute('SELECT COUNT(*) FROM episode_comments')
print(f'✅ 评论表已完全清空，剩余记录数: {c.fetchone()[0]}')
conn.close()
