package org.csu.sqliteanalyzer.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库整体集成测试：验证「建表 -> 插入 -> 查询 -> 删除 -> 持久化」整条链路。
 */
public class SqliteDatabaseTest {

    private static final String DB = "target/test-db.data";

    @AfterEach
    void cleanup() {
        new File(DB).delete();
    }

    @Test
    void testCreateInsertSelectDelete() {
        SqliteDatabase db = new SqliteDatabase(DB);

        db.execute("CREATE TABLE student(id INT, name VARCHAR, age INT)");
        db.execute("INSERT INTO student(id,name,age) VALUES (1,'Alice',20)");
        db.execute("INSERT INTO student(id,name,age) VALUES (2,'Bob',21)");

        String all = db.execute("SELECT * FROM student");
        assertTrue(all.contains("Alice") && all.contains("Bob"), all);

        // WHERE 过滤：age > 20 只留下 Bob
        String filtered = db.execute("SELECT id,name FROM student WHERE age > 20");
        assertTrue(filtered.contains("Bob") && !filtered.contains("Alice"), filtered);

        // 删除后再查
        db.execute("DELETE FROM student WHERE id=1");
        String afterDelete = db.execute("SELECT * FROM student");
        assertTrue(afterDelete.contains("Bob") && !afterDelete.contains("Alice"), afterDelete);

        db.shutdown();
    }

    @Test
    void testPersistenceAcrossRestart() {
        SqliteDatabase db1 = new SqliteDatabase(DB);
        db1.execute("CREATE TABLE t(id INT, name VARCHAR)");
        db1.execute("INSERT INTO t(id,name) VALUES (1,'persist-me')");
        db1.shutdown();

        // 重新打开，数据应该还在
        SqliteDatabase db2 = new SqliteDatabase(DB);
        String result = db2.execute("SELECT * FROM t");
        assertTrue(result.contains("persist-me"), result);
        db2.shutdown();
    }
}
