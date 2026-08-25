package com.minispring.jdbc.transaction;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionManagerTest {

    @Test
    void closeIsStillAttemptedWhenRestoringAutoCommitFails() {
        AtomicBoolean restoreAttempted = new AtomicBoolean();
        AtomicBoolean closeAttempted = new AtomicBoolean();

        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> {
                            if (Boolean.TRUE.equals(args[0])) {
                                restoreAttempted.set(true);
                                throw new SQLException("simulated reset failure");
                            }
                            return null;
                        }
                        case "commit" -> {
                            return null;
                        }
                        case "close" -> {
                            closeAttempted.set(true);
                            return null;
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                });
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        return connection;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

        String result = new TransactionManager(dataSource).execute(() -> "committed");

        assertEquals("committed", result);
        assertTrue(restoreAttempted.get(), "事务结束时必须尝试恢复 auto-commit");
        assertTrue(closeAttempted.get(), "恢复 auto-commit 失败后仍必须尝试关闭连接");
    }
}
