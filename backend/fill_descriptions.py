import sqlite3
conn = sqlite3.connect('ju_flash.db')
c = conn.cursor()

descriptions = [
    (1, '祖传一本残破笔记，北派摸金后人进山探索尘封千年的古墓秘境，揭开层层机关与诡异传说。'),
    (2, '顶级学霸意外穿越到纨绔废柴身上，凭借现代知识打脸各路反派，逆袭成为天下第一。'),
    (3, '十八岁太奶奶灵魂穿越到重孙儿的现代生活里，闹出来一系列温馨又搞笑的祖孙日常。'),
    (4, '两个错过多年的旧情人在城市再次相遇，在聚散离合中找回彼此之间最珍贵的缘分。'),
    (5, '饥荒年代全村啃树皮度日，女主角意外觉醒美食系统，囤满仓肉带领全家度过灾荒逆袭暴富。'),
    (6, '普通中国家庭跨越三十年的悲欢离合，讲述几代人之间的亲情牵绊与现实生活的温情感人故事。'),
    (7, '凡间少年意外得到上古传承，从底层一步步修仙逆袭，踏碎虚空成为六界最热血的传奇强者。'),
    (8, '连环凶案接连发生，刑侦队长带领刑警队通过蛛丝马迹层层推理，最终破解真相抓获真凶。'),
    (9, '回到高中那年的冬至，女主重新遇见青春里的那个他，弥补学生时代的遗憾重温校园美好时光。'),
    (10, '改革开放大浪潮里普通人从北方南下奋斗，一路历经风雨打拼事业实现人生价值的年代大剧。'),
]

for drama_id, desc in descriptions:
    c.execute('UPDATE dramas SET description = ? WHERE id = ?', (desc, drama_id))
    print(f'drama {drama_id} description 已写入')

conn.commit()
c.execute('SELECT id, title, description FROM dramas')
print('\n全部短剧简介：')
for r in c.fetchall():
    print(f'✅ {r[0]} - {r[1]}: {r[2]}')

conn.close()
print('\n✅ 所有短剧description字段补全完成')
