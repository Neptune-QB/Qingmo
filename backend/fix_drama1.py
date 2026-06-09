import sqlite3, os
conn = sqlite3.connect('ju_flash.db')
cur = conn.cursor()
cur.execute('''
  SELECT e.episode_id, e.episode_num
  FROM episodes e
  WHERE e.drama_id = 1
  ORDER BY e.episode_id
''')
rows = cur.fetchall()
updated = 0
for ep_id, ep_num in rows:
    real_path = f'videos/1/{63 + ep_num - 1}.mp4'
    full_local = os.path.join(r'crawler\data', real_path)
    if os.path.exists(full_local):
        cur.execute('UPDATE episodes SET video_url = ? WHERE episode_id = ?', (real_path, ep_id))
        updated += 1
        print(f'✅ episode {ep_id} → {real_path}')
conn.commit()
print(f'\n✅ drama_id=1 补充完成！共更新 {updated} 条')
conn.close()
