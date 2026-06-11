import sqlite3
from pathlib import Path

DB_PATH = Path(__file__).parent / "ju_flash.db"

# P1 完全无业务依赖废弃表清单，扫描确认代码零引用后可安全删除
P1_DEPRECATED_TABLES = [
    "users",
    "highlight_votes",
    "highlight_vote_records",
    "branch_votes",
    "branch_vote_records",
]

def safe_p1_cleanup():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    print("[P1 Safe Cleanup] 执行无业务依赖废弃表安全删除...")

    deleted_count = 0
    for table in P1_DEPRECATED_TABLES:
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?;", (table,))
        if cur.fetchone():
            cur.execute(f"DROP TABLE IF EXISTS {table};")
            print(f"✅ 已删除废弃表 {table}")
            deleted_count += 1
        else:
            print(f"ℹ️  表 {table} 不存在，跳过")

    # 清理 sqlite_sequence 中不存在表的残留自增记录
    cur.execute("SELECT name FROM sqlite_master WHERE type='table';")
    all_existing_tables = {row[0] for row in cur.fetchall()}
    cur.execute("SELECT name FROM sqlite_sequence;")
    seq_rows = cur.fetchall()
    cleaned_seq = 0
    for (table_name,) in seq_rows:
        if table_name not in all_existing_tables:
            cur.execute("DELETE FROM sqlite_sequence WHERE name=?;", (table_name,))
            print(f"✅ 清理 sqlite_sequence 残留记录 {table_name}")
            cleaned_seq += 1

    conn.commit()

    print(f"\n🎉 P1 精简完成，本次共删除 {deleted_count} 张废弃表，清理 {cleaned_seq} 条残留序列记录")
    print("\n👉 当前最终正式生产数据表清单：")
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;")
    for (t,) in cur.fetchall():
        print(f"   - {t}")

    conn.close()

if __name__ == "__main__":
    safe_p1_cleanup()
