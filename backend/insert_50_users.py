import sqlite3
conn = sqlite3.connect(r'C:\Users\12730\desktop\Qingmo\backend\ju_flash.db')
c = conn.cursor()

nicenames = [
    '梧桐听雨', '深海蓝鲸', '晚风告白', '山川赴约', '星河入梦', '橘子汽水', '青山见我', '月下独行',
    '海盐味风', '落日邮差', '流浪星球', '云边小卖部', '山野萤火', '半亩方塘', '人间理想', '晚风邮递员',
    '北派寻宝人', '东北大花袄', '深山挖参客', '摸金传人', '老把头徒弟', '关外老烟枪', '长白山猎户',
    '追剧到凌晨', '弹幕十级选手', '下饭神器', '沙发土豆', '快进终结者', '倍速大师', '不看难受',
    '会员抢先看', '广告绝缘体', '剧情分析师', '细节控玩家', '伏笔收割机', '弹幕刷屏侠', '弹幕守门员',
    '深夜剧荒', '三倍速达人', '高清爱好者', '投屏狂魔', '手办收藏家', '正版支持者', '会员年费党',
    '女主铁粉', '男主脑残粉', '反派共情者', '结局党', '二刷爱好者', '三刷常客', 'N刷长老'
]
c.execute('CREATE TABLE IF NOT EXISTS mock_users(user_id TEXT PRIMARY KEY, nickname TEXT)')
for idx,nick in enumerate(nicenames):
    uid = f'mock-user-{idx+1:03d}'
    c.execute('INSERT OR REPLACE INTO mock_users(user_id, nickname) VALUES(?,?)', (uid, nick))
conn.commit()
c.execute('SELECT COUNT(*) FROM mock_users')
print(f'✅ 50条用户最终全部入库成功，总数={c.fetchone()[0]}')
for row in c.execute('SELECT * FROM mock_users LIMIT 10').fetchall():
    print(row)
conn.close()
