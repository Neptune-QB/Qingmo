"""
扩展全链路测试：弹幕/评论/鉴权/Agent/会话/RAG
"""
import json
import urllib.request
import urllib.error
import random
import string

BASE = "http://127.0.0.1:8000/api/v1"
PASS = 0
FAIL = 0

def test(name, method, path, body=None, expected_status=200, check=None, headers=None):
    global PASS, FAIL
    url = f"{BASE}{path}"
    try:
        data = json.dumps(body).encode() if body else None
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", "application/json")
        if headers:
            for k, v in headers.items():
                req.add_header(k, v)
        resp = urllib.request.urlopen(req, timeout=15)
        result = json.loads(resp.read())
        if check:
            ok, msg = check(result)
            if not ok:
                print(f"  FAIL [{name}] {msg}")
                FAIL += 1
                return
        print(f"  ✓   {name}")
        PASS += 1
        return result
    except urllib.error.HTTPError as e:
        if e.code == expected_status:
            print(f"  ✓   {name} (expected {e.code})")
            PASS += 1
            # 尝试读取409响应体获取user_id（如注册重复用户）
            try:
                return json.loads(e.read())
            except:
                pass
        else:
            body_preview = ""
            try:
                body_preview = e.read().decode("utf-8", errors="replace")[:200]
            except:
                pass
            print(f"  FAIL [{name}] expected {expected_status}, got {e.code}: {e.reason} | {body_preview}")
            FAIL += 1
        return None
    except Exception as e:
        print(f"  FAIL [{name}] {e}")
        FAIL += 1
        return None

def check_ok(data):
    if data.get("ok"):
        return True, ""
    return False, f"expected ok=true, got {data}"

def check_list(min_len=0):
    def check(data):
        if not isinstance(data, list):
            return False, "not a list"
        if len(data) < min_len:
            return False, f"list too short: {len(data)}"
        return True, ""
    return check

# ==================== 0. 注册测试用户 ====================
print("=== [0] 注册测试用户 ===")
# 用随机后缀避免用户名冲突
suffix = ''.join(random.choices(string.ascii_lowercase, k=6))
TEST_USERNAME = f"autotest_{suffix}"
TEST_PASSWORD = "123456"

res = test("注册测试用户", "POST", "/auth/register",
     body={"username": TEST_USERNAME, "password": TEST_PASSWORD, "nickname": "测试侠"})
if not res:
    # 可能用户已存在，尝试登录
    print("  注册失败，尝试注册已存在用户...")
    res = test("注册已存在用户409", "POST", "/auth/register",
         body={"username": TEST_USERNAME, "password": TEST_PASSWORD, "nickname": "测试侠"},
         expected_status=409)
    if not res:
        # 最后一次：登录或注册另一个随机用户
        res2 = test("登录已存在用户", "POST", "/auth/login",
              body={"username": TEST_USERNAME, "password": TEST_PASSWORD})
        if res2 and res2.get("user_id"):
            res = res2
        else:
            # 用不同用户名再注册
            suffix2 = ''.join(random.choices(string.ascii_lowercase, k=6))
            TEST_USERNAME2 = f"autotest_{suffix2}"
            res = test("重新注册", "POST", "/auth/register",
                 body={"username": TEST_USERNAME2, "password": TEST_PASSWORD, "nickname": "测试侠"})

USER_ID = None
TOKEN = None
if res:
    USER_ID = res.get("user_id")
    TOKEN = res.get("token")
    print(f"  ✓   测试用户 ID={USER_ID}")
else:
    print("  ERROR: 无法获取测试用户！后续测试将全部失败")
    USER_ID = "0"

DANMAKU_EP = 2

# ==================== 弹幕全链路 ====================
print("\n=== [1] 弹幕全链路 ===")
test("弹幕-发送", "POST", "/danmaku",
     body={"user_id": str(USER_ID), "episode_id": DANMAKU_EP, "text": "测试弹幕666", "time_sec": 1.5},
     check=check_ok)
test("弹幕-空内容400", "POST", "/danmaku",
     body={"user_id": str(USER_ID), "episode_id": DANMAKU_EP, "text": "  "},
     expected_status=400)
test("弹幕-超长400", "POST", "/danmaku",
     body={"user_id": str(USER_ID), "episode_id": DANMAKU_EP, "text": "x" * 81},
     expected_status=400)
res = test("弹幕-获取列表", "GET", f"/danmaku/{DANMAKU_EP}?limit=10", check=check_list())
if res:
    has = any("测试弹幕" in str(d.get("text","")) for d in res)
    if has:
        print("  ✓   弹幕-数据一致性")
        PASS += 1
    else:
        print("  FAIL 弹幕-数据一致性: 找不到已发送弹幕")
        FAIL += 1

# ==================== 评论全链路 ====================
print("\n=== [2] 评论全链路 ===")
test("评论-发送", "POST", f"/episodes/{DANMAKU_EP}/comments",
     body={"user_id": str(USER_ID), "episode_id": DANMAKU_EP, "text": "这集太精彩了！"},
     check=check_ok)
test("评论-超长400", "POST", f"/episodes/{DANMAKU_EP}/comments",
     body={"user_id": str(USER_ID), "episode_id": DANMAKU_EP, "text": "x" * 201},
     expected_status=400)
res = test("评论-获取列表", "GET", f"/episodes/{DANMAKU_EP}/comments?limit=10", check=check_list())

# ==================== 点赞/收藏 ====================
print("\n=== [3] 点赞/收藏 ===")
r1 = test("点赞-toggle", "POST", f"/episodes/{DANMAKU_EP}/like",
          body={"user_id": str(USER_ID)}, check=check_ok)
test("点赞-查询", "GET", f"/episodes/{DANMAKU_EP}/likes?user_id={USER_ID}",
     check=lambda d: (d.get("liked") in (True, False), "liked field required"))
test("收藏-toggle", "POST", "/dramas/2/favorite",
     body={"user_id": str(USER_ID)}, check=check_ok)
test("收藏-查询", "GET", f"/user/favorites?user_id={USER_ID}", check=check_list())

# ==================== 鉴权 ====================
print("\n=== [4] 鉴权 ===")
# 用一个新的随机用户测试完整的注册→登录→token→密码错误→/me完整链路
suffix3 = ''.join(random.choices(string.ascii_lowercase, k=6))
AUTH_USER = f"authuser_{suffix3}"
res = test("注册", "POST", "/auth/register",
     body={"username": AUTH_USER, "password": "123456", "nickname": "鉴权侠"},
     check=check_ok)
if not res:
    # 409 → 先登录一次
    res = test("注册-409推断", "POST", "/auth/register",
         body={"username": AUTH_USER, "password": "123456", "nickname": "鉴权侠"},
         expected_status=409)
    # 登录获取token
    if res:
        res = test("登录", "POST", "/auth/login",
              body={"username": AUTH_USER, "password": "123456"},
              check=check_ok)

token = None
if res:
    token = res.get("token")
    if token:
        print("  ✓   鉴权-token返回")
        PASS += 1
    else:
        print("  FAIL 鉴权-token返回: 无token")
        FAIL += 1

test("登录-密码错误401", "POST", "/auth/login",
     body={"username": AUTH_USER, "password": "wrong"},
     expected_status=401)

if token:
    req = urllib.request.Request(f"{BASE}/auth/me")
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", "application/json")
    try:
        resp = urllib.request.urlopen(req, timeout=10)
        data = json.loads(resp.read())
        if data.get("username") == AUTH_USER:
            print(f"  ✓   鉴权-/me验证")
            PASS += 1
        else:
            print(f"  FAIL 鉴权-/me验证: {data}")
            FAIL += 1
    except Exception as e:
        print(f"  FAIL 鉴权-/me验证: {e}")
        FAIL += 1

# ==================== Agent/小墨 ====================
print("\n=== [5] Agent / 小墨对话 ===")
try:
    data = json.dumps({"user_id": str(USER_ID), "message": "推荐几部甜宠剧"}).encode()
    req = urllib.request.Request(f"{BASE}/agent/chat", data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req, timeout=30)
    raw = resp.read().decode("utf-8")
    if len(raw) > 0:
        print("  ✓   Agent-搜索意图 (流式返回非空)")
        PASS += 1
    else:
        print("  FAIL Agent-搜索意图: empty response")
        FAIL += 1
except Exception as e:
    print(f"  FAIL Agent-搜索意图: {e}")
    FAIL += 1

try:
    data = json.dumps({"user_id": str(USER_ID), "message": "我看过什么剧"}).encode()
    req = urllib.request.Request(f"{BASE}/agent/chat", data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req, timeout=30)
    raw = resp.read().decode("utf-8")
    if len(raw) > 0:
        print("  ✓   Agent-画像意图 (流式返回非空)")
        PASS += 1
    else:
        print("  FAIL Agent-画像意图: empty response")
        FAIL += 1
except Exception as e:
    print(f"  FAIL Agent-画像意图: {e}")
    FAIL += 1

# ==================== 会话持久化 ====================
print("\n=== [6] 小墨会话持久化 ===")
res = test("会话-创建", "POST", "/agent/sessions",
          body={"user_id": str(USER_ID), "drama_id": 2, "title": "自动测试会话"},
          check=lambda d: ("id" in d, "need id field"))
session_id = res.get("id") if res else None

if session_id:
    test("会话-列出来", "GET", f"/agent/sessions?user_id={USER_ID}", check=check_list(1))
    test("消息-追加", "POST", "/agent/sessions/messages/append",
         body={"session_id": session_id, "role": "user", "content": "你好小墨"},
         check=check_ok)
    test("消息-追加assistant", "POST", "/agent/sessions/messages/append",
         body={"session_id": session_id, "role": "assistant", "content": "你好呀~我在!"},
         check=check_ok)
    res = test("消息-拉取", "GET", f"/agent/sessions/{session_id}/messages", check=check_list(2))
    test("会话-删除", "DELETE", f"/agent/sessions/{session_id}", check=check_ok)

# ==================== RAG 检索 ====================
print("\n=== [7] RAG 剧情检索 ===")
try:
    data = json.dumps({"user_id": str(USER_ID), "message": "男主是谁", "context": {"drama_id": 2, "episode_num": 3}}).encode()
    req = urllib.request.Request(f"{BASE}/agent/chat", data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    resp = urllib.request.urlopen(req, timeout=30)
    raw = resp.read().decode("utf-8")
    if len(raw) > 0:
        print("  ✓   Agent-RAG剧情问答 (流式返回非空)")
        PASS += 1
    else:
        print("  FAIL Agent-RAG剧情问答: empty response")
        FAIL += 1
except Exception as e:
    print(f"  FAIL Agent-RAG剧情问答: {e}")
    FAIL += 1

# ==================== 结果 ====================
print(f"\n{'='*30}")
print(f"Total: {PASS+FAIL} | Pass: {PASS} | Fail: {FAIL}")
if FAIL == 0:
    print("扩展全链路测试全部通过！")
else:
    print(f"{FAIL} 项测试失败！推入CI阻断")
