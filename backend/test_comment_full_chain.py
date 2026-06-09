"""
评论互动全链路自动化回归测试
覆盖：弹幕→点赞→收藏→互动统计 全路径
"""
import requests
import uuid

BASE = "http://127.0.0.1:8000/api/v1"
TEST_UID = str(uuid.uuid4().int % 100000)

def main():
    print("="*80)
    print(" 🧪 青墨评论互动全链路自动化测试 正式启动")
    print("="*80)

    # 取第一个有效episode_id
    resp = requests.get(f"{BASE}/dramas")
    dramas = resp.json()
    first_did = dramas[0]["id"]
    print(f"\n✅ 选取测试剧集 ID={first_did}")
    resp = requests.get(f"{BASE}/playback/1")
    ep_info = resp.json()
    test_ep_id = ep_info["episode_id"]
    print(f"✅ 选取测试集 episode_id={test_ep_id}")

    # 1. 测试弹幕发送
    print("\n---[1/4] 测试弹幕发送---")
    dm_text = f"全链路自动化测试弹幕 {uuid.uuid4().hex[:6]}"
    r = requests.post(f"{BASE}/danmaku", json={
        "user_id": TEST_UID,
        "episode_id": test_ep_id,
        "text": dm_text,
        "time_sec": 120.0
    })
    assert r.status_code == 200, f"弹幕发送失败 {r.text}"
    dm_id = r.json()["id"]
    print(f"✅ 弹幕发送成功 ID={dm_id} 内容: {dm_text}")

    # 2. 测试弹幕拉取
    r2 = requests.get(f"{BASE}/danmaku/{test_ep_id}?time_from=100&time_to=150")
    dms = r2.json()
    matched = any(d["id"] == dm_id for d in dms)
    assert matched, "刚发的弹幕没有拉取到"
    print(f"✅ 弹幕拉取成功 匹配到刚发送的测试弹幕")

    # 3. 测试点赞
    print("\n---[2/4] 测试点赞链路---")
    r3 = requests.post(f"{BASE}/episodes/{test_ep_id}/like", json={"user_id": TEST_UID})
    assert r3.status_code == 200 and r3.json()["action"] == "liked", "点赞失败"
    print("✅ 点赞操作成功")
    r3b = requests.get(f"{BASE}/episodes/{test_ep_id}/likes?user_id={TEST_UID}")
    assert r3b.json()["liked"] == True, "点赞后状态未同步"
    like_count_after = r3b.json()["count"]
    print(f"✅ 点赞状态校验成功 当前总点赞数={like_count_after}")
    # 取消点赞
    r3c = requests.post(f"{BASE}/episodes/{test_ep_id}/like", json={"user_id": TEST_UID})
    assert r3c.json()["action"] == "unliked", "取消点赞失败"
    print("✅ 取消点赞操作成功")
    r3d = requests.get(f"{BASE}/episodes/{test_ep_id}/likes?user_id={TEST_UID}")
    assert r3d.json()["liked"] == False, "取消点赞后状态未同步"
    print("✅ 取消点赞状态校验成功")

    # 4. 测试收藏
    print("\n---[3/4] 测试收藏链路---")
    r4 = requests.post(f"{BASE}/dramas/{first_did}/favorite", json={"user_id": TEST_UID})
    assert r4.status_code == 200 and r4.json()["action"] == "favorited", "收藏失败"
    print("✅ 收藏操作成功")
    r4b = requests.get(f"{BASE}/user/favorites?user_id={TEST_UID}")
    assert any(x["drama_id"] == first_did for x in r4b.json()), "收藏后不在用户收藏列表中"
    print("✅ 收藏列表校验成功")
    # 取消收藏
    r4c = requests.post(f"{BASE}/dramas/{first_did}/favorite", json={"user_id": TEST_UID})
    assert r4c.json()["action"] == "unfavorited", "取消收藏失败"
    print("✅ 取消收藏操作成功")
    r4d = requests.get(f"{BASE}/user/favorites?user_id={TEST_UID}")
    assert all(x["id"] != first_did for x in r4d.json()), "取消收藏后还在用户收藏列表中"
    print("✅ 取消收藏列表校验成功")

    # 5. 测试互动记录上报
    print("\n---[4/4] 测试互动记录上报---")
    r5 = requests.post(f"{BASE}/interactions", json={
        "user_id": TEST_UID,
        "episode_id": test_ep_id,
        "module_id": "test_full_chain",
        "interaction_data": {"type":"happy", "score":5}
    })
    assert r5.status_code == 200, "互动上报失败"
    inter_id = r5.json()["interaction_id"]
    print(f"✅ 互动上报成功 ID={inter_id}")
    r5b = requests.get(f"{BASE}/interactions?user_id={TEST_UID}&episode_id={test_ep_id}")
    assert any(x["id"] == inter_id for x in r5b.json()), "刚上报的互动记录没有拉取到"
    print("✅ 互动记录回拉校验成功")

    print("\n" + "="*80)
    print(" 🎉 全部评论互动全链路自动化测试100%通过！0失败！")
    print("="*80)

if __name__ == "__main__":
    main()
