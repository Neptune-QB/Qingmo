import sqlite3
conn = sqlite3.connect('ju_flash.db')
c = conn.cursor()

multi_tags = [
    (1, '探险/盗墓/悬疑'),
    (2, '古装/穿越/爽文'),
    (3, '穿越/年代/搞笑'),
    (4, '都市/爱情/甜宠'),
    (5, '年代/系统/励志'),
    (6, '家庭/伦理/现实'),
    (7, '仙侠/玄幻/修真'),
    (8, '悬疑/刑侦/破案'),
    (9, '校园/青春/回忆'),
    (10, '年代/奋斗/励志'),
]

for drama_id, tags_str in multi_tags:
    c.execute('UPDATE dramas SET tags = ? WHERE id = ?', (tags_str, drama_id))
    print(f'drama {drama_id} tags set to: {tags_str}')

conn.commit()
c.execute('SELECT id, title, tags FROM dramas')
print('\n最终标签列表：')
for r in c.fetchall():
    print(r)

conn.close()
print('\n✅ 全部多标签补全完成')
