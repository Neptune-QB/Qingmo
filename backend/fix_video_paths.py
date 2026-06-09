import sqlite3
conn = sqlite3.connect('ju_flash.db')
cur = conn.cursor()
cur.execute('SELECT episode_id, episode_num FROM episodes ORDER BY episode_id')
rows = cur.fetchall()
updated = 0
for ep_db_id, ep_num in rows:
    real_path = f'videos/{ep_db_id}/1.mp4'
    cur.execute('UPDATE episodes SET video_url = ? WHERE episode_id = ?', (real_path, ep_db_id))
    updated += 1
conn.commit()
print(f'UPDATED {updated} ROWS!')
cur.execute('SELECT episode_id, video_url FROM episodes LIMIT 20')
for r in cur.fetchall():
    print(r)
conn.close()
