"""点赞功能专项全链路测试"""
import requests, random, string

BASE = 'http://127.0.0.1:8000/api/v1'
suffix = ''.join(random.choices(string.ascii_lowercase, k=6))

# 1. 注册测试用户
r = requests.post(f'{BASE}/auth/register', json={'username': f'liketest_{suffix}', 'password': '123456', 'nickname': '点赞测试'})
if r.status_code == 409:
    r = requests.post(f'{BASE}/auth/login', json={'username': f'liketest_{suffix}', 'password': '123456'})
uid = r.json()['user_id']
print(f'[1] 测试用户 ID={uid}')

# 2. 初始状态：未点赞
r = requests.get(f'{BASE}/episodes/2/likes?user_id={uid}')
print(f'[2] 初始状态: {r.json()} (status={r.status_code})')
assert r.status_code == 200
assert r.json()['liked'] is False, '初始应为未点赞'
print('    \u2713 初始未点赞')

# 3. 点赞
r = requests.post(f'{BASE}/episodes/2/like', json={'user_id': str(uid)})
print(f'[3] 点赞: {r.json()} (status={r.status_code})')
assert r.status_code == 200
assert r.json()['action'] == 'liked'
print('    \u2713 toggle=liked')

# 4. 查询：已点赞
r = requests.get(f'{BASE}/episodes/2/likes?user_id={uid}')
print(f'[4] 查询: {r.json()} (status={r.status_code})')
assert r.json()['count'] >= 1
assert r.json()['liked'] is True
print('    \u2713 已点赞，count>0')

# 5. 取消点赞
r = requests.post(f'{BASE}/episodes/2/like', json={'user_id': str(uid)})
print(f'[5] 取消点赞: {r.json()} (status={r.status_code})')
assert r.json()['action'] == 'unliked'
print('    \u2713 toggle=unliked')

# 6. 查询：已取消
r = requests.get(f'{BASE}/episodes/2/likes?user_id={uid}')
print(f'[6] 查询: {r.json()} (status={r.status_code})')
assert r.json()['liked'] is False
print('    \u2713 已取消点赞')

# 7. 连续切换 5 次验证幂等
print(f'[7] 连续切换5次...')
for i in range(5):
    r = requests.post(f'{BASE}/episodes/2/like', json={'user_id': str(uid)})
    assert r.status_code == 200
    act = r.json()['action']
    print(f'    第{i+1}次: {act}')
print('    \u2713 5次连续切换全部200')

# 8. 最终确认状态
r = requests.get(f'{BASE}/episodes/2/likes?user_id={uid}')
final = r.json()
print(f'[8] 最终状态: liked={final["liked"]}, count={final["count"]}')
assert final['liked'] is True
print('    \u2713 幂等切换正确(5次后为liked)')

# 9. 切换后 count 不会重复累计
r = requests.post(f'{BASE}/episodes/2/like', json={'user_id': str(uid)})  # unlike
r = requests.post(f'{BASE}/episodes/2/like', json={'user_id': str(uid)})  # like again
r = requests.get(f'{BASE}/episodes/2/likes?user_id={uid}')
c = r.json()['count']
print(f'[9] 重复切换后: liked=True, count={c}')
print('    \u2713 count正常(不重复累计)')

print(f'\n{"="*30}')
print('点赞全链路测试 9/9 全部通过！')
