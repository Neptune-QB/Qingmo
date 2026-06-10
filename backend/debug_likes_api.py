"""直接调试 /user/likes 接口，打印完整异常栈"""
import sys
sys.path.insert(0, r'C:\Users\12730\Desktop\Qingmo\backend')

from fastapi.testclient import TestClient
from main import app

client = TestClient(app)
print('[DEBUG] 正在请求 /api/v1/user/likes?user_id=1')
try:
    resp = client.get('/api/v1/user/likes?user_id=1')
    print(f'Status: {resp.status_code}')
    print(f'Response: {resp.text}')
except Exception as e:
    import traceback
    print('[EXC] 完整异常栈：')
    traceback.print_exc()
