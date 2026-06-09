"""
演示高光点种子数据生成脚本
为 5 部 Demo 短剧各生成 10-15 条多样化高光点，可直接用于比赛演示

生成策略：
- 每部短剧 12 条高光点，分布在不少于 6 集中
- widget_type 分布：emotion 60%, vote 30%, branch 10%
- emotion 类型带 emotion_hints（表情 emoji 列表）
- vote 类型带 options（2-4 个选项）
- branch 类型带 options（2-3 个分支选项）
- 时间点分散在各集的不同位置（开头 5-15s / 中部 100-180s / 结尾 240-280s）
"""
import json
import random
import sys
import os

sys.path.insert(0, os.path.dirname(__file__))
from app.database import get_connection

# 5 部演示短剧 ID
DEMO_DRAMA_IDS = [2, 5, 7, 8, 9]

# 高光点场景模板（按短剧类型定制）
DRAMA_HIGHLIGHT_TEMPLATES = {
    # Drama 2: 天下第一纨绔 — 古装
    2: [
        # (episode_spread, time_sec, type, title, widget, emotion_hints/options, duration)
        (1, 25, "conflict", "纨绔打脸众人", "vote",
         ["选A！", "选B！", "这也太爽了！"], 20),
        (1, 180, "twist", "惊天反转：真实身份曝光", "emotion",
         ["卧槽！", "反转了！", "没想到！"], 18),
        (2, 15, "sweet", "初遇心动一刻", "emotion",
         ["甜哭了！", "磕到了！", "好甜！"], 15),
        (2, 120, "famous", "公子大闹朝堂", "emotion",
         ["名场面！", "封神！", "燃炸！"], 22),
        (3, 90, "conflict", "情敌公开叫板", "vote",
         ["干他！", "不忍！", "先忍一手"], 20),
        (3, 250, "twist", "发现惊天秘密", "emotion",
         ["震惊！", "头皮发麻！", "细思极恐！"], 18),
        (4, 30, "funny", "纨绔日常翻车", "emotion",
         ["笑死！", "笑出鹅叫！", "哈哈哈！"], 15),
        (4, 200, "sweet", "此生不负你", "emotion",
         ["甜哭了！", "好心动！", "原地结婚！"], 20),
        (5, 100, "conflict", "身世之谜揭晓", "vote",
         ["不可能！", "果然如此！"], 22),
        (5, 270, "famous", "终极一战", "emotion",
         ["封神名场面！", "燃炸了！", "太帅了！"], 25),
        (6, 50, "branch", "选择哪条路？", "branch",
         ["回到家族", "独自闯荡"], 20),
        (7, 160, "sweet", "大婚之日", "emotion",
         ["甜甜甜！", "在一起！", "祝福！"], 20),
    ],
    # Drama 5: 荒年全村啃树皮 — 年代
    5: [
        (1, 30, "conflict", "系统到账！全村震惊", "vote",
         ["这系统无敌！", "低调发育！"], 20),
        (1, 200, "famous", "囤粮震惊全村", "emotion",
         ["名场面！", "社会！", "大佬！"], 18),
        (2, 15, "twist", "被村民发现秘密", "emotion",
         ["完蛋！", "怎么解释？", "别慌！"], 15),
        (2, 140, "funny", "村长带着全村来认错", "emotion",
         ["笑死！", "真香定律！", "哈哈哈！"], 18),
        (3, 80, "sweet", "竹马重逢", "emotion",
         ["好甜！", "青梅竹马YYDS！", "磕！"], 15),
        (3, 260, "conflict", "天灾降临，分粮还是独善其身", "vote",
         ["分粮救人！", "先保住自己！"], 25),
        (4, 45, "twist", "系统升级！解锁新功能", "emotion",
         ["起飞！", "这才是爽文！", "无敌了！"], 20),
        (4, 190, "famous", "带领全村奔小康", "emotion",
         ["名场面！", "正能量！", "感动！"], 22),
        (5, 120, "branch", "要不要带全村进城？", "branch",
         ["进城闯荡", "留在村里"], 20),
        (6, 60, "funny", "系统又整新活", "emotion",
         ["笑喷！", "这系统太会了！", "绝了！"], 15),
        (6, 240, "sweet", "那个他表白了", "emotion",
         ["终于来了！", "甜炸！", "在一起！"], 20),
        (7, 170, "conflict", "外来富商要买断田地", "vote",
         ["绝不卖！", "考虑一下"], 22),
    ],
    # Drama 7: 云渺1 — 仙侠
    7: [
        (1, 20, "famous", "开局捡到绝世功法", "emotion",
         ["主角光环！", "名场面！", "起飞！"], 18),
        (1, 170, "conflict", "宗门大比，对手使诈", "vote",
         ["揭穿他！", "用实力打脸！"], 20),
        (2, 90, "sweet", "秘境中救下女主", "emotion",
         ["甜甜甜！", "英雄救美！", "磕到！"], 15),
        (2, 250, "twist", "师父原来是大反派", "emotion",
         ["不可能！", "细思极恐！", "反转了！"], 22),
        (3, 40, "funny", "灵兽认主闹乌龙", "emotion",
         ["哈哈哈！", "笑死！", "这灵兽太可爱！"], 15),
        (3, 180, "famous", "一剑破万法", "emotion",
         ["燃炸！", "万剑归宗！", "帅！"], 20),
        (4, 130, "conflict", "魔道入侵，选择救谁？", "vote",
         ["救宗门！", "救爱人！", "两个都救！"], 25),
        (4, 275, "twist", "渡劫飞升成功", "emotion",
         ["封神！", "终于飞升！", "这也太帅了吧！"], 22),
        (5, 70, "sweet", "携手云游四海", "emotion",
         ["好甜！", "神仙眷侣！", "羡慕！"], 15),
        (5, 200, "branch", "选择加入哪个仙门？", "branch",
         ["正道仙门", "隐世散修"], 18),
        (6, 30, "famous", "收服上古神兽", "emotion",
         ["名场面！", "太帅了！", "这神兽绝了！"], 22),
        (7, 150, "conflict", "最终决战前的抉择", "vote",
         ["正面硬刚！", "智取迂回！"], 20),
    ],
    # Drama 8: 撕夜 — 悬疑
    8: [
        (1, 60, "twist", "第一桩命案手法曝光", "emotion",
         ["卧槽！", "这也太狠了！", "头皮发麻！"], 20),
        (1, 220, "conflict", "发现重要线索", "vote",
         ["直接追踪！", "先分析再行动！"], 18),
        (2, 35, "famous", "主角高光推理时刻", "emotion",
         ["名场面！", "逻辑满分！", "聪明的男主！"], 20),
        (2, 160, "sweet", "搭档关系微妙升温", "emotion",
         ["磕到了！", "这对可以！", "好配！"], 15),
        (3, 100, "twist", "关键证人突然死亡", "emotion",
         ["震惊！", "这下要怎么查？", "太突然了！"], 18),
        (3, 255, "conflict", "警匪对峙：救不救线人", "vote",
         ["救线人！", "先保证群众安全！"], 22),
        (4, 20, "funny", "法医同事讲冷笑话", "emotion",
         ["哈哈哈！", "笑死！", "这个角色太逗！"], 12),
        (4, 190, "twist", "真凶另有其人", "emotion",
         ["反转了！", "果然是他！", "我猜错了！"], 22),
        (5, 80, "branch", "追凶路线选择", "branch",
         ["跟线人提供的线索", "相信直觉"], 18),
        (5, 240, "famous", "天台终极对决", "emotion",
         ["名场面！", "燃！", "太刺激了！"], 25),
        (6, 50, "sweet", "危机中的告白", "emotion",
         ["甜哭！", "在危险中表白也太浪漫了！", "好磕！"], 18),
        (7, 140, "twist", "幕后黑手身份揭晓", "emotion",
         ["卧槽！", "居然是他！", "从头到尾都在布局！"], 22),
    ],
    # Drama 9: 那年冬至 — 校园
    9: [
        (1, 40, "sweet", "转学生第一次见面", "emotion",
         ["好甜！", "一见钟情！", "这对颜值好高！"], 15),
        (1, 190, "funny", "体育课翻车现场", "emotion",
         ["哈哈哈！", "笑死我了！", "名场面！"], 15),
        (2, 120, "conflict", "学霸vs学渣：期末考试", "vote",
         ["我赌学霸赢！", "学渣逆袭预定！"], 20),
        (2, 260, "sweet", "图书馆偷偷对视", "emotion",
         ["甜哭！", "这种悸动！", "磕到了！"], 15),
        (3, 55, "twist", "校庆表演才艺炸场", "emotion",
         ["原来这么厉害！", "反转！", "隐藏大佬！"], 18),
        (3, 210, "famous", "操场上的真心话大冒险", "emotion",
         ["名场面！", "笑死！", "青春！"], 20),
        (4, 30, "conflict", "被老师误会早恋", "vote",
         ["解释清楚！", "将错就错！"], 18),
        (4, 170, "sweet", "冬至那天的心动", "emotion",
         ["泪目！", "好甜！", "这就是青春！"], 20),
        (5, 90, "branch", "文理分科选择", "branch",
         ["选文科", "选理科"], 15),
        (6, 140, "funny", "食堂抢饭名场面", "emotion",
         ["太真实了！", "笑死！", "食堂阿姨手抖！"], 15),
        (6, 270, "sweet", "毕业季的表白", "emotion",
         ["泪目了！", "青春不散场！", "好感人！"], 22),
        (7, 80, "twist", "高考成绩公布", "emotion",
         ["卧槽！", "逆天改命！", "太强了！"], 20),
    ],
}


def seed_demo_highlights():
    conn = get_connection()
    cursor = conn.cursor()

    # 清空旧高光点
    cursor.execute("DELETE FROM highlights")

    total = 0
    for drama_id in DEMO_DRAMA_IDS:
        templates = DRAMA_HIGHLIGHT_TEMPLATES.get(drama_id)
        if not templates:
            continue

        # 获取该短剧的剧集列表
        cursor.execute(
            "SELECT episode_id, episode_num FROM episodes WHERE drama_id = ? ORDER BY episode_num",
            (drama_id,),
        )
        episodes = cursor.fetchall()
        if not episodes:
            print(f"  Drama {drama_id}: 无剧集数据，跳过")
            continue

        # 计算 offset：模板中的 episode_spread 是相对数值（1-7），
        # 需要映射到实际的 episode_num
        # 取总集数的 70% 作为高光点分布范围，确保高光点在前 70% 的剧集中
        max_ep_spread = max(t[0] for t in templates)  # 模板中的最大 ep_spread
        episode_count = len(episodes)
        actual_max = min(episode_count - 1, int(episode_count * 0.7))
        # 确保 actual_max >= max_ep_spread
        ratio = max(1.0, actual_max / max_ep_spread) if max_ep_spread > 0 else 1.0

        count = 0
        for ep_spread, time_sec, h_type, title, widget, extras, duration in templates:
            actual_ep_idx = int(ep_spread * ratio)
            if actual_ep_idx >= len(episodes):
                actual_ep_idx = len(episodes) - 1
            ep = episodes[actual_ep_idx]

            # 解析 extras
            options = None
            emotion_hints = None
            if widget == "emotion":
                emotion_hints = json.dumps(extras, ensure_ascii=False)
            elif widget in ("vote", "branch"):
                options = json.dumps(extras, ensure_ascii=False)

            cursor.execute(
                """INSERT INTO highlights
                   (episode_id, time, type, title, widget_type, options, emotion_hints, duration)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                (ep["episode_id"], time_sec, h_type, title, widget, options, emotion_hints, duration),
            )
            count += 1
            total += 1

        print(f"  Drama {drama_id}: {count} 条高光点已生成")

    conn.commit()
    conn.close()
    print(f"\n总计: {total} 条演示高光点，分布在 {len(DEMO_DRAMA_IDS)} 部短剧中")


if __name__ == "__main__":
    seed_demo_highlights()
    print("演示高光点种子数据生成完毕！")
