import sqlite3, os
conn = sqlite3.connect('ju_flash.db')
cur = conn.cursor()
cur.execute('''
  SELECT e.episode_id, e.episode_num, d.id AS drama_id
  FROM episodes e
  JOIN dramas d ON e.drama_id = d.id
  ORDER BY e.episode_id
''')
rows = cur.fetchall()
updated = 0
for ep_id, ep_num, drama_id in rows:
    # 你物理目录 drama_id=1 → videos/1/, drama_id=10 → videos/10/
    real_path = f'videos/{drama_id}/{ep_num}.mp4'
    # 校验这个文件真的存在
    full_local = os.path.join(r'crawler\data', real_path)
    if os.path.exists(full_local):
        cur.execute('UPDATE episodes SET video_url = ? WHERE episode_id = ?', (real_path, ep_id))
        updated += 1
        print(f'✅ episode {ep_id} → {real_path} (exists)')
    else:
        print(f'⚠️  skip ep {ep_id} {real_path} NOT FOUND')
conn.commit()
print(f'\n🎉 全部完成！实际更新 {updated} 条有效视频路径！')
conn.close()
