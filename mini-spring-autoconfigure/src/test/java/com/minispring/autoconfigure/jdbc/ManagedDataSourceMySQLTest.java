package com.minispring.autoconfigure.jdbc;

import com.minispring.jdbc.transaction.ConnectionDiscardingDataSource;
import com.minispring.jdbc.transaction.ManagedDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL + Hikari 所有权验证：驱逐后必须更换物理连接，未提交写入必须由数据库回滚。
 */
class ManagedDataSourceMySQLTest {

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

        HikariDataSource pool = new HikariDataSource(config);
        try (ManagedDataSource dataSource = new ManagedDataSource(pool, pool::evictConnection)) {
            assertInstanceOf(ConnectionDiscardingDataSource.class, dataSource);
            assertTrue(dataSource.isWrapperFor(HikariDataSource.class));

            String proofEmail = "discard+" + UUID.randomUUID() + "@hikari-test.invalid";
            Connection connection = dataSource.getConnection();
            long evictedConnectionId = connectionId(connection);
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO users(name, email) VALUES (?, ?)")) {
                statement.setString(1, "待驱逐事务");
                statement.setString(2, proofEmail);
                statement.executeUpdate();
            }
            dataSource.discard(connection);

            try (Connection replacement = dataSource.getConnection()) {
                assertNotEquals(evictedConnectionId, connectionId(replacement),
                        "驱逐后不得把同一物理连接重新借出");
                assertEquals(0, rowCount(replacement, proofEmail),
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
