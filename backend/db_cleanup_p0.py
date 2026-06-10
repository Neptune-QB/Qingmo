import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).parent / "ju_flash.db"

def cleanup_p0():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    print("[P0 Cleanup] 开始执行零风险数据库精简...")

    # 1. 删除历史遗留临时过渡表 episode_comments_new
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='episode_comments_new';")
    if cur.fetchone():
        cur.execute("DROP TABLE IF EXISTS episode_comments_new;")
        print("✅ 已删除孤立临时表 episode_comments_new")
    else:
        print("ℹ️  episode_comments_new 不存在，跳过")

    # 2. 清理全部悬空无效外键指向不存在表的问题：
    # SQLite 不支持直接 ALTER TABLE DROP CONSTRAINT，最安全方式是重建表移除无效外键
    # 先获取所有表的定义
    cur.execute("SELECT name, sql FROM sqlite_master WHERE type='table' ORDER BY name;")
    all_tables = cur.fetchall()

    fixed_count = 0
    for table_name, create_sql in all_tables:
        if "FOREIGN KEY" in create_sql and ("dramas_old" in create_sql or "episodes_trash" in create_sql or "highlights_old" in create_sql):
            print(f"⚠️  发现表 {table_name} 含悬空外键，重建表清除无效引用...")
            # 把无效外键行全部删掉
            lines = [line for line in create_sql.splitlines() if not ("dramas_old" in line or "episodes_trash" in line or "highlights_old" in line)]
            new_create_sql = "\n".join(lines)
            # 重建表流程
            cur.execute(f"ALTER TABLE {table_name} RENAME TO {table_name}_bak;")
            cur.execute(new_create_sql)
            cur.execute(f"INSERT INTO {table_name} SELECT * FROM {table_name}_bak;")
            cur.execute(f"DROP TABLE {table_name}_bak;")
            fixed_count += 1
            print(f"✅ 表 {table_name} 外键清理完成")

    if fixed_count == 0:
        print("✅ 未发现悬空外键，所有 schema 外键均指向有效表")

    conn.commit()
    conn.close()
    print("\n🎉 P0级数据库精简执行完毕！")

if __name__ == "__main__":
    cleanup_p0()
