package com.minispring.jdbc;

import com.minispring.jdbc.transaction.TransactionContext;
import com.minispring.jdbc.transaction.TransactionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jdbc 模块单测——<b>真连 MySQL</b>（deploy/mysql 容器，DriverManager 直连 13306，不引 H2/Hikari）。
 * 教学纪律：单测只作基线，但这些用例同时是 M8 机制的行为锚点（事务连接复用 / 回滚 / 异常翻译）。
 *
 * <p>连接复用的强断言用 MySQL 的 {@code CONNECTION_ID()}：同一事务内的两次查询必须返回
 * 相同连接 id；非事务的两次操作各自新取连接（DriverManager 无池，必然不同）。
 */
class JdbcTemplateMySQLTest {

    private static final String URL = "jdbc:mysql://localhost:13306/minispring_demo"
            + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000";
    private static final String USER = "minispring";
    private static final String PASSWORD = "minispring_123";

    private static JdbcTemplate jdbc;
    private static TransactionManager txManager;

    private static final RowMapper<Long> CONNECTION_ID = rs -> rs.getLong(1);

    @BeforeAll
    static void setUp() {
        // 用驱动自带的 MysqlDataSource（无池）——池行为是 V6 的验收（demo 层 Hikari），这里测模板与事务本身
        com.mysql.cj.jdbc.MysqlDataSource dataSource = new com.mysql.cj.jdbc.MysqlDataSource();
        dataSource.setUrl(URL);
        dataSource.setUser(USER);
        dataSource.setPassword(PASSWORD);
        jdbc = new JdbcTemplate(dataSource);
        txManager = new TransactionManager(dataSource);
    }

    @BeforeEach
    void cleanSlate() {
        jdbc.update("DELETE FROM users WHERE email LIKE '%@jdbc-test'");
    }

    @Test
    void crudRoundTripWithGeneratedKey() {
        long id = jdbc.insertAndReturnKey(
                "INSERT INTO users(name, email) VALUES (?, ?)", "测试甲", "a@jdbc-test");
        assertTrue(id > 0, "自增主键必须真实回填（V2 语义），实际 " + id);

        UserRow row = jdbc.queryOne("SELECT id, name, email FROM users WHERE id = ?",
                r -> new UserRow(r.getLong("id"), r.getString("name"), r.getString("email")), id);
        assertEquals("测试甲", row.name);
        assertEquals("a@jdbc-test", row.email);

        assertEquals(1, jdbc.update("UPDATE users SET name = ? WHERE id = ?", "测试乙", id));
        assertEquals(1, jdbc.update("DELETE FROM users WHERE id = ?", id));
        assertNull(jdbc.queryOne("SELECT id FROM users WHERE id = ?", CONNECTION_ID, id));
    }

    @Test
    void transactionCommitsOnSuccess() {
        Boolean committed = txManager.execute(() ->
                jdbc.insertAndReturnKey("INSERT INTO users(name, email) VALUES (?, ?)", "提交者", "commit@jdbc-test") > 0);
        assertTrue(committed, "事务回调结果透传");
        Long inDb = jdbc.queryOne("SELECT id FROM users WHERE email = ?", CONNECTION_ID, "commit@jdbc-test");
        assertTrue(inDb != null && inDb > 0, "正常返回的事务必须提交落库");
    }

    @Test
    void transactionRollsBackOnException() {
        assertThrows(IllegalStateException.class, () -> txManager.execute(() -> {
            jdbc.update("INSERT INTO users(name, email) VALUES (?, ?)", "回滚者", "rollback@jdbc-test");
            throw new IllegalStateException("boom-rollback");
        }));
        assertNull(jdbc.queryOne("SELECT id FROM users WHERE email = ?", CONNECTION_ID, "rollback@jdbc-test"),
                "抛异常的事务必须回滚，数据不得落库");
    }

    /**
     * 审查修复（M9 复审 I2）的约束用例：Error 不被 catch(Exception) 拦截，回滚只能靠
     * finally 的统一兜底——修复前该路径经 closeQuietly 的 setAutoCommit(true) 构成
     * <b>隐式提交</b>（JDBC 规范），本测试会因查到脏数据而失败。
     */
    @Test
    void transactionRollsBackOnError() {
        assertThrows(AssertionError.class, () -> txManager.execute(() -> {
            jdbc.update("INSERT INTO users(name, email) VALUES (?, ?)", "错误者", "error@jdbc-test");
            throw new AssertionError("boom-error");
        }));
        assertNull(jdbc.queryOne("SELECT id FROM users WHERE email = ?", CONNECTION_ID, "error@jdbc-test"),
                "Error 路径同样必须回滚，不得隐式提交");
    }

    @Test
    void statementsInsideOneTransactionShareConnection() {
        // 同一事务内两次查询的 CONNECTION_ID() 必须一致（事务连接复用的唯一事实证据）
        Long txResult = txManager.execute(() -> {
            Long first = jdbc.queryOne("SELECT CONNECTION_ID()", CONNECTION_ID);
            Long second = jdbc.queryOne("SELECT CONNECTION_ID()", CONNECTION_ID);
            return first.equals(second) ? first : -1L;
        });
        assertNotEquals(Long.valueOf(-1L), txResult, "同一事务内的 SQL 必须共用同一物理连接");

        // 非事务场景：DriverManager 无池，两次操作必然各开新连接
        Long a = jdbc.queryOne("SELECT CONNECTION_ID()", CONNECTION_ID);
        Long b = jdbc.queryOne("SELECT CONNECTION_ID()", CONNECTION_ID);
        assertNotEquals(a, b, "非事务操作各自取还连接（对照组，验证对照确实存在差异）");

        // 事务结束后线程上下文必须清空（线程池复用纪律）
        assertNull(TransactionContext.current(), "事务结束后 ThreadLocal 必须清除");
    }

    @Test
    void constraintViolationTranslatesToDuplicateKeyException() {
        jdbc.update("INSERT INTO users(name, email) VALUES (?, ?)", "占用者", "dup@jdbc-test");
        DuplicateKeyException ex = assertThrows(DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO users(name, email) VALUES (?, ?)", "重复者", "dup@jdbc-test"));
        assertTrue(ex.getMessage().contains("users"), "异常信息应带上出错的 SQL 片段");
    }

    @Test
    void queryListMapsEveryRow() {
        jdbc.update("INSERT INTO users(name, email) VALUES (?, ?)", "列表甲", "l1@jdbc-test");
        jdbc.update("INSERT INTO users(name, email) VALUES (?, ?)", "列表乙", "l2@jdbc-test");
        List<Long> ids = jdbc.query("SELECT id FROM users WHERE email LIKE '%@jdbc-test' ORDER BY id",
                CONNECTION_ID);
        assertEquals(2, ids.size(), "多行查询必须逐行映射");
    }

    record UserRow(long id, String name, String email) {
    }
}
