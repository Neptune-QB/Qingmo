from app.main import app
import waitress
print("✅ 纯同步Waitress WSGI 服务器启动，端口8000，无任何异步连接池")
waitress.serve(app, host='127.0.0.1', port=8000, threads=8, connection_limit=1)
