package com.minispring.jdbc.transaction;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionManagerTest {

    @Test
    void closeIsStillAttemptedWhenRestoringAutoCommitFails() {
        JdbcProbe probe = new JdbcProbe(true);

        String result = new TransactionManager(probe.dataSource).execute(() -> "committed");

        assertEquals("committed", result);
        assertEquals(1, probe.restoreAttempts.get(), "事务结束时必须尝试恢复 auto-commit");
        assertEquals(1, probe.closeAttempts.get(), "恢复 auto-commit 失败后仍必须尝试关闭连接");
    }

    @Test
    void nestedRequiredSuccessReusesConnectionAndCommitsOnce() {
        JdbcProbe probe = new JdbcProbe(false);
        TransactionManager manager = new TransactionManager(probe.dataSource);

        String result = manager.execute(() -> manager.execute(() -> "nested-result"));

        assertEquals("nested-result", result);
        assertEquals(1, probe.connectionRequests.get());
        assertEquals(1, probe.commitAttempts.get());
        assertEquals(0, probe.rollbackAttempts.get());
        assertEquals(1, probe.closeAttempts.get());
        assertNull(TransactionContext.current());
    }

    @Test
    void caughtNestedFailureMarksSharedTransactionRollbackOnly() {
        JdbcProbe probe = new JdbcProbe(false);
        TransactionManager manager = new TransactionManager(probe.dataSource);
        IllegalStateException nestedFailure = new IllegalStateException("nested failure");

        UnexpectedRollbackException thrown = assertThrows(UnexpectedRollbackException.class,
                () -> manager.execute(() -> {
                    IllegalStateException propagated = assertThrows(IllegalStateException.class,
                            () -> manager.execute(() -> {
                                throw nestedFailure;
                            }));
                    assertSame(nestedFailure, propagated);
                    return "must not commit";
                }));

        assertSame(nestedFailure, thrown.getCause(), "应保留首个内层失败作为回滚原因");
        assertEquals(0, probe.commitAttempts.get());
        assertEquals(1, probe.rollbackAttempts.get());
        assertEquals(1, probe.closeAttempts.get());
        assertNull(TransactionContext.current());
    }

    @Test
    void caughtNestedErrorAlsoMarksSharedTransactionRollbackOnly() {
        JdbcProbe probe = new JdbcProbe(false);
        TransactionManager manager = new TransactionManager(probe.dataSource);
        AssertionError nestedFailure = new AssertionError("nested error");

        UnexpectedRollbackException thrown = assertThrows(UnexpectedRollbackException.class,
                () -> manager.execute(() -> {
                    AssertionError propagated = assertThrows(AssertionError.class,
                            () -> manager.execute(() -> {
                                throw nestedFailure;
                            }));
                    assertSame(nestedFailure, propagated);
                    return null;
                }));

        assertSame(nestedFailure, thrown.getCause());
        assertEquals(0, probe.commitAttempts.get());
        assertEquals(1, probe.rollbackAttempts.get());
        assertNull(TransactionContext.current());
    }

    @Test
    void uncaughtNestedFailurePreservesOriginalExceptionAndRollsBackOnce() {
        JdbcProbe probe = new JdbcProbe(false);
        TransactionManager manager = new TransactionManager(probe.dataSource);
        IllegalArgumentException nestedFailure = new IllegalArgumentException("uncaught nested failure");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> manager.execute(() -> manager.execute(() -> {
                    throw nestedFailure;
                })));

        assertSame(nestedFailure, thrown);
        assertEquals(0, probe.commitAttempts.get());
        assertEquals(1, probe.rollbackAttempts.get());
        assertEquals(1, probe.closeAttempts.get());
        assertNull(TransactionContext.current());
    }

    @Test
    void topLevelErrorRollsBackAndClearsContext() {
        JdbcProbe probe = new JdbcProbe(false);
        TransactionManager manager = new TransactionManager(probe.dataSource);
        AssertionError failure = new AssertionError("fatal callback failure");

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> manager.execute(() -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(0, probe.commitAttempts.get());
        assertEquals(1, probe.rollbackAttempts.get());
        assertEquals(1, probe.closeAttempts.get());
        assertNull(TransactionContext.current());
    }

    private static final class JdbcProbe {
        private final AtomicInteger connectionRequests = new AtomicInteger();
        private final AtomicInteger commitAttempts = new AtomicInteger();
        private final AtomicInteger rollbackAttempts = new AtomicInteger();
        private final AtomicInteger restoreAttempts = new AtomicInteger();
        private final AtomicInteger closeAttempts = new AtomicInteger();
        private final boolean failOnRestore;
        private final Connection connection;
        private final DataSource dataSource;

        private JdbcProbe(boolean failOnRestore) {
            this.failOnRestore = failOnRestore;
            this.connection = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this::invokeConnection);
            this.dataSource = (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return invokeObjectMethod(proxy, method, args);
                        }
                        if (method.getName().equals("getConnection")) {
                            connectionRequests.incrementAndGet();
                            return connection;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private Object invokeConnection(Object proxy, Method method, Object[] args) throws SQLException {
            if (method.getDeclaringClass() == Object.class) {
                return invokeObjectMethod(proxy, method, args);
            }
            return switch (method.getName()) {
                case "setAutoCommit" -> {
                    if (Boolean.TRUE.equals(args[0])) {
                        restoreAttempts.incrementAndGet();
                        if (failOnRestore) {
                            throw new SQLException("simulated reset failure");
                        }
                    }
                    yield null;
                }
                case "commit" -> {
                    commitAttempts.incrementAndGet();
                    yield null;
                }
                case "rollback" -> {
                    rollbackAttempts.incrementAndGet();
                    yield null;
                }
                case "close" -> {
                    closeAttempts.incrementAndGet();
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private static Object invokeObjectMethod(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "JdbcProbe(" + proxy.getClass().getInterfaces()[0].getSimpleName() + ")";
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }
    }
}
