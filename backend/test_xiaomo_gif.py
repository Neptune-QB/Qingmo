"""
小墨 GIF 动效表测试
验证建表、唯一约束、默认值、基础查询功能
"""
import os
import sys
import sqlite3

sys.path.insert(0, os.path.dirname(__file__))
from app.database import init_db, get_connection

TEST_DB = os.path.join(os.path.dirname(__file__), "_test_xiaomo_gif.db")


def setup_test_db():
    """创建独立测试数据库，不影响正式库"""
    if os.path.exists(TEST_DB):
        os.remove(TEST_DB)
    # 临时替换 DB_PATH
    import app.database as db
    original = db.DB_PATH
    db.DB_PATH = TEST_DB
    try:
        init_db()
    finally:
        db.DB_PATH = original


def get_test_conn():
    conn = sqlite3.connect(TEST_DB, check_same_thread=False)
    conn.execute("PRAGMA foreign_keys = ON")
    conn.row_factory = sqlite3.Row
    return conn


def cleanup():
    if os.path.exists(TEST_DB):
        os.remove(TEST_DB)


def test_table_created():
    """验证建表成功"""
    conn = get_test_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='xiaomo_gif'")
    assert cursor.fetchone() is not None, "xiaomo_gif 表未创建"
    conn.close()
    print("  ✓ 建表成功")


def test_columns():
    """验证所有字段存在且类型正确"""
    conn = get_test_conn()
    cursor = conn.cursor()
    cursor.execute("PRAGMA table_info(xiaomo_gif)")
    cols = {r["name"]: r for r in cursor.fetchall()}
    expected = ["id", "code", "name", "gif_url", "highlight_type", "description", "status", "created_at", "updated_at"]
    for col in expected:
        assert col in cols, f"缺少字段: {col}"
    # NOT NULL 约束
    assert cols["code"]["notnull"] == 1, "code 应为 NOT NULL"
    assert cols["name"]["notnull"] == 1, "name 应为 NOT NULL"
    assert cols["gif_url"]["notnull"] == 1, "gif_url 应为 NOT NULL"
    assert cols["status"]["notnull"] == 1, "status 应为 NOT NULL"
    # 默认值
    assert cols["status"]["dflt_value"] == "'published'", f"status 默认值应为 'published'，实际: {cols['status']['dflt_value']}"
    conn.close()
    print("  ✓ 字段结构正确")


def test_unique_code():
    """验证 code 唯一约束"""
    conn = get_test_conn()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO xiaomo_gif (code, name, gif_url) VALUES (?, ?, ?)",
        ("test_code", "测试GIF", "/test/test.gif"),
    )
    conn.commit()
    try:
        cursor.execute(
            "INSERT INTO xiaomo_gif (code, name, gif_url) VALUES (?, ?, ?)",
            ("test_code", "重复GIF", "/test/dup.gif"),
        )
        conn.commit()
        assert False, "code 唯一约束未生效，重复插入成功"
    except sqlite3.IntegrityError:
        pass  # 预期行为
    conn.close()
    print("  ✓ code 唯一约束正常")


def test_status_default():
    """验证 status 默认值为 published"""
    conn = get_test_conn()
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO xiaomo_gif (code, name, gif_url) VALUES (?, ?, ?)",
        ("default_status_test", "默认状态测试", "/test/default.gif"),
    )
    conn.commit()
    cursor.execute("SELECT status FROM xiaomo_gif WHERE code = ?", ("default_status_test",))
    row = cursor.fetchone()
    assert row["status"] == "published", f"status 默认值应为 published，实际: {row['status']}"
    conn.close()
    print("  ✓ status 默认值为 published")


def test_query_by_code():
    """验证根据 code 查询"""
    conn = get_test_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM xiaomo_gif WHERE code = ?", ("test_code",))
    row = cursor.fetchone()
    assert row is not None, "根据 code 查询失败"
    assert row["name"] == "测试GIF"
    assert row["gif_url"] == "/test/test.gif"
    conn.close()
    print("  ✓ 根据 code 查询正常")


def test_query_by_highlight_type():
    """验证根据 highlight_type + status 查询"""
    conn = get_test_conn()
    cursor = conn.cursor()
    # 插入一条带 highlight_type 的记录
    cursor.execute(
        "INSERT INTO xiaomo_gif (code, name, gif_url, highlight_type) VALUES (?, ?, ?, ?)",
        ("cliffhanger", "悬念钩子", "/test/cliffhanger.gif", "cliffhanger"),
    )
    conn.commit()
    cursor.execute(
        "SELECT * FROM xiaomo_gif WHERE highlight_type = ? AND status = ?",
        ("cliffhanger", "published"),
    )
    rows = cursor.fetchall()
    assert len(rows) == 1, f"根据 highlight_type 查询应返回 1 条，实际: {len(rows)}"
    assert rows[0]["code"] == "cliffhanger"
    conn.close()
    print("  ✓ 根据 highlight_type + status 查询正常")


def test_query_all_published():
    """验证查询所有 published 状态的 GIF"""
    conn = get_test_conn()
    cursor = conn.cursor()
    # 插入一条 disabled 记录
    cursor.execute(
        "INSERT INTO xiaomo_gif (code, name, gif_url, status) VALUES (?, ?, ?, ?)",
        ("disabled_gif", "禁用GIF", "/test/disabled.gif", "disabled"),
    )
    conn.commit()
    cursor.execute("SELECT * FROM xiaomo_gif WHERE status = 'published'")
    rows = cursor.fetchall()
    # 应该不包含 disabled 的记录
    codes = [r["code"] for r in rows]
    assert "disabled_gif" not in codes, "published 查询不应包含 disabled 记录"
    assert len(rows) >= 3, f"published 记录数应至少 3 条，实际: {len(rows)}"
    conn.close()
    print("  ✓ 查询所有 published 状态正常")


def test_indices():
    """验证索引存在"""
    conn = get_test_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='xiaomo_gif'")
    indices = [r["name"] for r in cursor.fetchall()]
    assert "idx_xiaomo_gif_highlight_type" in indices, "缺少 highlight_type 索引"
    assert "idx_xiaomo_gif_status" in indices, "缺少 status 索引"
    # SQLite 自动为 UNIQUE 列创建索引 sqlite_autoindex_xiaomo_gif_1
    conn.close()
    print("  ✓ 索引创建正常")


def test_404_for_missing_code():
    """验证查询不存在的 code 返回 None"""
    conn = get_test_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM xiaomo_gif WHERE code = ?", ("nonexistent",))
    row = cursor.fetchone()
    assert row is None, "查询不存在的 code 应返回 None"
    conn.close()
    print("  ✓ 查询不存在 code 返回空")


def run_all():
    setup_test_db()
    passed = 0
    failed = 0
    tests = [
        test_table_created,
        test_columns,
        test_unique_code,
        test_status_default,
        test_query_by_code,
        test_query_by_highlight_type,
        test_query_all_published,
        test_indices,
        test_404_for_missing_code,
    ]
    for test in tests:
        try:
            test()
            passed += 1
        except Exception as e:
            print(f"  ✗ {test.__name__} 失败: {e}")
            failed += 1
    cleanup()
    print(f"\n测试完成: {passed} 通过 / {failed} 失败 / 共 {len(tests)} 项")
    return failed == 0


if __name__ == "__main__":
    success = run_all()
    sys.exit(0 if success else 1)
