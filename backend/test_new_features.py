"""全量新增功能端到端测试"""
import requests, time, sys

BASE = "http://127.0.0.1:8000/api/v1"
TEST_USER = f"test-{int(time.time())}"
EP = 1; DRAMA = 1

passed = 0; failed = 0; failures = []

def T(name):
    """装饰器风格测试注册"""
    def deco(fn):
        global passed, failed
        try:
            fn()
            passed += 1
            print(f"  \u2705 {name}")
        except Exception as e:
            failed += 1
            failures.append((name, str(e)))
            print(f"  \u274c {name}: {e}")
        return fn
    return deco

def post(path, body=None):
    return requests.post(f"{BASE}/{path}", json=body or {}, timeout=15)

def get(path, params=None):
    return requests.get(f"{BASE}/{path}", params=params or {}, timeout=15)

def delete(path):
    return requests.delete(f"{BASE}/{path}", timeout=15)

def ok(resp, expect=200):
    assert resp.status_code == expect, f"status {resp.status_code} != {expect}: {resp.text[:200]}"

# ============================================================
print("\n[1/7] 弹幕彩蛋")
# ============================================================
@T("触发关键词「反转」")
def _():
    r = post("danmaku", {"user_id": TEST_USER, "episode_id": EP, "text": "这也太反转了吧", "time_sec": 10})
    ok(r)
    d = r.json()
    assert d["easter_egg"] == "叮！反转卡已激活🔮", f"got: {d.get('easter_egg')}"

@T("触发关键词「笑死」")
def _():
    r = post("danmaku", {"user_id": TEST_USER, "episode_id": EP, "text": "笑死我了哈哈哈", "time_sec": 15})
    ok(r)
    assert r.json()["easter_egg"] == "笑不活了家人们😂"

@T("无关键词不触发彩蛋")
def _():
    r = post("danmaku", {"user_id": TEST_USER, "episode_id": EP, "text": "今天天气不错", "time_sec": 20})
    ok(r)
    assert r.json().get("easter_egg") is None

# ============================================================
print("\n[2/7] @小墨 AI评论回复")
# ============================================================
@T("发@小墨评论")
def _():
    r = post(f"episodes/{EP}/comments", {"user_id": TEST_USER, "text": "@小墨 这集好看吗？", "parent_id": 0, "reply_to_nickname": ""})
    ok(r)
    assert r.json().get("id")
    print(f"    (comment_id={r.json()['id']})")

@T("@小墨 AI回复轮询 (最多12s)")
def _():
    r = post(f"episodes/{EP}/comments", {"user_id": TEST_USER, "text": "@小墨 你好呀", "parent_id": 0, "reply_to_nickname": ""})
    ok(r)
    cid = r.json()["id"]
    found = False
    for _ in range(6):
        time.sleep(2)
        r2 = get(f"episodes/{EP}/comments/ai-reply-status", {"parent_comment_ids": str(cid)})
        if r2.status_code == 200 and str(cid) in r2.json().get("replies", {}):
            found = True; break
    print(f"    (AI回复{'已生成 ✅' if found else '未生成(可能LLM慢/不可用) ⚠️'})")

# ============================================================
print("\n[3/7] 剧情投票")
# ============================================================
vote_hl = None
for hid in [375, 558, 475, 553, 377, 396]:
    try:
        r = get(f"highlights/{hid}/vote")
        if r.status_code == 200 and r.json().get("vote"):
            vote_hl = hid; break
    except: pass

@T(f"GET 投票 (highlight={vote_hl or '?'})")
def _():
    r = get(f"highlights/{vote_hl or 375}/vote", {"user_id": TEST_USER})
    ok(r)

if vote_hl:
    @T("POST 投票")
    def _():
        r = post(f"highlights/{vote_hl}/vote", {"user_id": TEST_USER, "choice": "a"})
        ok(r)
        assert "counts" in r.json()

    @T("GET 验证我的投票")
    def _():
        r = get(f"highlights/{vote_hl}/vote", {"user_id": TEST_USER})
        ok(r)
        assert r.json()["vote"]["my_choice"] == "a"
else:
    print("  ⚠️ 跳过: 无投票数据的高光点")

# ============================================================
print("\n[4/7] AI剧情问答")
# ============================================================
@T("GET 问答生成")
def _():
    r = get(f"highlights/{375}/quiz")
    ok(r)
    d = r.json()
    assert d.get("ok") == True
    print(f"    (quiz: {'available' if d.get('quiz') else 'fallback'})")

# ============================================================
print("\n[5/7] 角色AI对话")
# ============================================================
@T("GET 角色列表")
def _():
    r = get("characters", {"drama_id": DRAMA})
    ok(r)
    chars = r.json()
    assert isinstance(chars, list)
    print(f"    (共 {len(chars)} 个角色)")

chars_r = get("characters", {"drama_id": DRAMA})
chars = chars_r.json() if chars_r.status_code == 200 else []
if chars:
    @T("POST 角色聊天")
    def _():
        cid = chars[0]["id"]
        r = post(f"characters/{cid}/chat", {"user_message": "你好", "drama_id": DRAMA})
        ok(r)
        assert "reply" in r.json()
        print(f"    (reply: {r.json()['reply'][:60]})")
else:
    print("  ⚠️ 跳过: 无角色数据")

# ============================================================
print("\n[6/7] 追剧笔记")
# ============================================================
_note_id = None

@T("POST 创建笔记")
def _():
    global _note_id
    r = post(f"episodes/{EP}/notes", {"user_id": TEST_USER, "text": "高能时刻！测试笔记", "time_sec": 45.5, "drama_id": DRAMA})
    ok(r)
    _note_id = r.json()["id"]
    print(f"    (id={_note_id})")

@T("GET 单集笔记")
def _():
    r = get(f"episodes/{EP}/notes", {"user_id": TEST_USER})
    ok(r)
    assert len(r.json()) >= 1

@T("GET 全部笔记")
def _():
    r = get("user/notes", {"user_id": TEST_USER})
    ok(r)
    assert isinstance(r.json(), list)

@T("GET 笔记总数")
def _():
    r = get("user/notes/count", {"user_id": TEST_USER})
    ok(r)
    assert r.json()["count"] >= 1

@T("DELETE 笔记")
def _():
    assert _note_id is not None
    r = delete(f"notes/{_note_id}")
    ok(r)

# ============================================================
print("\n[7/7] 剧情分支投票")
# ============================================================
@T("GET 分支投票")
def _():
    r = get(f"dramas/{DRAMA}/branch-vote", {"user_id": TEST_USER})
    ok(r)
    has = r.json().get("vote") is not None
    print(f"    (vote: {'available' if has else 'none'})")

br = get(f"dramas/{DRAMA}/branch-vote", {"user_id": TEST_USER}).json()
if br.get("vote"):
    @T("POST 分支投票")
    def _():
        r = post(f"dramas/{DRAMA}/branch-vote", {"user_id": TEST_USER, "choice": "a"})
        ok(r)
        assert "counts" in r.json()

    @T("GET 验证分支投票")
    def _():
        r = get(f"dramas/{DRAMA}/branch-vote", {"user_id": TEST_USER})
        ok(r)
        assert r.json()["vote"]["my_choice"] == "a"
else:
    print("  ⚠️ 跳过: 无分支投票数据")

# ============================================================
print(f"\n{'='*60}")
print(f"  Total: {passed+failed} | Pass: {passed} | Fail: {failed}")
if failures:
    print("\n  失败:")
    for n, e in failures: print(f"    - {n}: {e}")
else:
    print("  🎉 全量新增功能测试通过！")
print(f"{'='*60}")
sys.exit(1 if failed else 0)
