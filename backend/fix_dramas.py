import sqlite3
conn = sqlite3.connect('ju_flash.db')
c = conn.cursor()

fallback = [
    (1, '北派寻宝笔记', '', 'covers/01.jpg', 50, '探险'),
    (2, '天下第一纨绔', '', 'covers/02.jpg', 60, '古装'),
    (3, '十八岁太奶奶驾到第三代', '', 'covers/03.jpg', 45, '穿越'),
    (4, '幸得相遇别离时', '', 'covers/04.jpg', 55, '都市'),
    (5, '荒年全村啃树皮我有系统满仓肉', '', 'covers/05.jpg', 70, '年代'),
    (6, '家里家外', '', 'covers/06.jpg', 40, '家庭'),
    (7, '云琊1', '', 'covers/07.jpg', 65, '仙侠'),
    (8, '撕夜', '', 'covers/08.jpg', 48, '悬疑'),
    (9, '那年冬至', '', 'covers/09.jpg', 36, '校园'),
    (10, '北往', '', 'covers/10.jpg', 52, '年代'),
]

for row in fallback:
    c.execute('INSERT OR IGNORE INTO dramas (id, title, description, cover_url, total_episodes, tags) VALUES (?,?,?,?,?,?)', row)

c.execute('UPDATE dramas SET total_episodes = (SELECT COUNT(*) FROM episodes WHERE episodes.drama_id = dramas.id)')
conn.commit()

c.execute('SELECT id, title, total_episodes, cover_url, tags FROM dramas')
for r in c.fetchall():
    print(r)

conn.close()
print("✅ dramas表补全完成")
