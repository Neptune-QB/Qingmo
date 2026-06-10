import sqlite3
conn = sqlite3.connect('ju_flash.db')
c = conn.cursor()

tags_4 = [
    (1, '探险/盗墓/悬疑/冒险'),
    (2, '古装/穿越/爽文/搞笑'),
    (3, '穿越/年代/搞笑/甜宠'),
    (4, '都市/爱情/甜宠/职场'),
    (5, '年代/系统/励志/爽文'),
    (6, '家庭/伦理/现实/温情'),
    (7, '仙侠/玄幻/修真/热血'),
    (8, '悬疑/刑侦/破案/惊悚'),
    (9, '校园/青春/回忆/励志'),
    (10, '年代/奋斗/励志/剧情'),
]

for drama_id, tags_str in tags_4:
    c.execute('UPDATE dramas SET tags = ? WHERE id = ?', (tags_str, drama_id))

conn.commit()
c.execute('SELECT id, title, tags FROM dramas')
for r in c.fetchall():
    print(r)

conn.close()
print('\n✅ 每剧补全到4个标签')
