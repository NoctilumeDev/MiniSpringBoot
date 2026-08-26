package com.minispring.autoconfigure.jdbc;

import com.minispring.jdbc.transaction.ConnectionDiscardingDataSource;
import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL + Hikari 所有权验证：被驱逐的代理必须关闭，下一次借用必须落到新的物理连接。
 */
class ManagedHikariDataSourceMySQLTest {

    @Test
    void discardEvictsTheBorrowedPhysicalConnection() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:13306/minispring_demo"
                + "?useSSL=false&allowPublicKeyRetrieval=true&connectTimeout=3000");
        config.setUsername("minispring");
        config.setPassword("minispring_123");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3_000);
        config.setPoolName("minispring-discard-proof");

        try (ManagedHikariDataSource dataSource = new ManagedHikariDataSource(config)) {
            assertInstanceOf(ConnectionDiscardingDataSource.class, dataSource);

            Connection connection = dataSource.getConnection();
            long evictedConnectionId = connectionId(connection);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM users WHERE email = 'discard@hikari-test'");
            }

            // TransactionManager 只在事务边界失败时驱逐；复现其真实借用状态，
            // 并留下未提交写入，以数据库事实判断物理连接是否真的被终止。
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO users(name, email)"
                        + " VALUES ('待驱逐事务', 'discard@hikari-test')");
            }
            dataSource.discard(connection);

            try (Connection replacement = dataSource.getConnection()) {
                assertNotEquals(evictedConnectionId, connectionId(replacement),
                        "驱逐后不得把同一物理连接重新借出");
                assertEquals(0, rowCount(replacement, "discard@hikari-test"),
                        "物理连接终止必须让未提交事务由数据库回滚");
            }
        }
    }

    private long connectionId(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT CONNECTION_ID()")) {
            assertTrue(resultSet.next());
            return resultSet.getLong(1);
        }
    }

    private long rowCount(Connection connection, String email) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE email = ?")) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }
}
