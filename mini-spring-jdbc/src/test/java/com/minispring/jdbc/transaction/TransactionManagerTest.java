package com.minispring.jdbc.transaction;

import com.minispring.jdbc.DataAccessException;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionManagerTest {

    @Test
    void successfulTransactionHasOneOrderedLifecycle() {
        JdbcProbe probe = new JdbcProbe();

        String result = probe.manager().execute(() -> "committed");

        assertEquals("committed", result);
        probe.assertEvents("getConnection", "setAutoCommit(false)", "commit",
                "setAutoCommit(true)", "close");
        assertNull(TransactionContext.current());
    }

    @Test
    void businessFailureRollsBackAndPreservesOriginalException() {
        JdbcProbe probe = new JdbcProbe();
        IllegalArgumentException businessFailure = new IllegalArgumentException("business failure");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> probe.manager().execute(() -> {
                    throw businessFailure;
                }));

        assertSame(businessFailure, thrown);
        probe.assertEvents("getConnection", "setAutoCommit(false)", "rollback",
                "setAutoCommit(true)", "close");
    }

    @Test
    void rollbackFailureMakesOutcomeUnknownAndNeverRestoresOrClosesConnection() {
        JdbcProbe probe = new JdbcProbe(Failure.ROLLBACK);
        IllegalStateException businessFailure = new IllegalStateException("business failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> probe.manager().execute(() -> {
                    throw businessFailure;
                }));

        assertSame(businessFailure, thrown);
        TransactionSystemException boundaryFailure = onlyBoundaryFailure(thrown);
        assertEquals(TransactionOutcome.UNKNOWN, boundaryFailure.outcome());
        assertEquals("rollback failure", boundaryFailure.getCause().getMessage());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "rollback", "discard");
        assertFalse(probe.events.contains("setAutoCommit(true)"));
        assertFalse(probe.events.contains("close"));
        assertNull(TransactionContext.current());
    }

    @Test
    void commitFailureMakesOutcomeUnknownAndDoesNotAttemptRollbackOrReset() {
        JdbcProbe probe = new JdbcProbe(Failure.COMMIT);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> probe.manager().execute(() -> "result"));

        assertEquals(TransactionOutcome.UNKNOWN, thrown.outcome());
        assertEquals("commit failure", thrown.getCause().getMessage());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "commit", "discard");
        assertFalse(probe.events.contains("rollback"));
        assertFalse(probe.events.contains("setAutoCommit(true)"));
        assertFalse(probe.events.contains("close"));
    }

    @Test
    void rollbackAndDiscardFailuresRemainOrderedUnderBusinessFailure() {
        JdbcProbe probe = new JdbcProbe(Failure.ROLLBACK, Failure.DISCARD);
        IllegalStateException businessFailure = new IllegalStateException("business failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> probe.manager().execute(() -> {
                    throw businessFailure;
                }));

        assertSame(businessFailure, thrown);
        TransactionSystemException rollbackFailure = onlyBoundaryFailure(thrown);
        assertEquals(TransactionOutcome.UNKNOWN, rollbackFailure.outcome());
        TransactionSystemException discardFailure = onlyBoundaryFailure(rollbackFailure);
        assertEquals(TransactionOutcome.UNKNOWN, discardFailure.outcome());
        assertEquals("discard failure", discardFailure.getCause().getMessage());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "rollback", "discard");
    }

    @Test
    void beginFailureDiscardsConnectionWithoutGuessingTransactionState() {
        JdbcProbe probe = new JdbcProbe(Failure.BEGIN);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> probe.manager().execute(() -> "never called"));

        assertEquals(TransactionOutcome.UNKNOWN, thrown.outcome());
        assertEquals("begin failure", thrown.getCause().getMessage());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "discard");
    }

    @Test
    void committedTransactionWithResetFailureReportsCommittedAndDiscardsConnection() {
        JdbcProbe probe = new JdbcProbe(Failure.RESET);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> probe.manager().execute(() -> "already committed"));

        assertEquals(TransactionOutcome.COMMITTED, thrown.outcome());
        assertEquals("reset failure", thrown.getCause().getMessage());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "commit",
                "setAutoCommit(true)", "discard");
        assertFalse(probe.events.contains("close"));
    }

    @Test
    void committedTransactionWithCloseFailureReportsCommittedAndDiscardsConnection() {
        JdbcProbe probe = new JdbcProbe(Failure.CLOSE);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> probe.manager().execute(() -> "already committed"));

        assertEquals(TransactionOutcome.COMMITTED, thrown.outcome());
        assertEquals("close failure", thrown.getCause().getMessage());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "commit",
                "setAutoCommit(true)", "close", "discard");
    }

    @Test
    void rolledBackTransactionWithResetFailureKeepsBusinessFailurePrimary() {
        JdbcProbe probe = new JdbcProbe(Failure.RESET);
        IllegalArgumentException businessFailure = new IllegalArgumentException("business failure");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> probe.manager().execute(() -> {
                    throw businessFailure;
                }));

        assertSame(businessFailure, thrown);
        TransactionSystemException cleanupFailure = onlyBoundaryFailure(thrown);
        assertEquals(TransactionOutcome.ROLLED_BACK, cleanupFailure.outcome());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "rollback",
                "setAutoCommit(true)", "discard");
    }

    @Test
    void defaultDiscarderUsesJdbcAbortInsteadOfClose() {
        JdbcProbe probe = new JdbcProbe(Failure.COMMIT);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> new TransactionManager(probe.dataSource).execute(() -> "result"));

        assertEquals(TransactionOutcome.UNKNOWN, thrown.outcome());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "commit", "abort");
        assertFalse(probe.events.contains("close"));
    }

    @Test
    void dataSourceOwnerProvidesTheDiscardPath() {
        JdbcProbe probe = new JdbcProbe(Failure.COMMIT);

        TransactionSystemException thrown = assertThrows(TransactionSystemException.class,
                () -> new TransactionManager(probe.ownerDataSource).execute(() -> "result"));

        assertEquals(TransactionOutcome.UNKNOWN, thrown.outcome());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "commit", "ownerDiscard");
        assertFalse(probe.events.contains("abort"));
    }

    @Test
    void acquisitionFailureDoesNotInventATransactionOutcome() {
        JdbcProbe probe = new JdbcProbe(Failure.ACQUIRE);

        DataAccessException thrown = assertThrows(DataAccessException.class,
                () -> probe.manager().execute(() -> "never called"));

        assertFalse(thrown instanceof TransactionSystemException,
                "未获得连接时没有事务终局，不应伪造 UNKNOWN");
        assertEquals("acquire failure", thrown.getCause().getMessage());
        probe.assertEvents("getConnection");
    }

    @Test
    void nestedRequiredSuccessReusesConnectionAndCommitsOnce() {
        JdbcProbe probe = new JdbcProbe();
        TransactionManager manager = probe.manager();

        String result = manager.execute(() -> manager.execute(() -> "nested-result"));

        assertEquals("nested-result", result);
        probe.assertEvents("getConnection", "setAutoCommit(false)", "commit",
                "setAutoCommit(true)", "close");
        assertNull(TransactionContext.current());
    }

    @Test
    void caughtNestedFailureMarksSharedTransactionRollbackOnly() {
        JdbcProbe probe = new JdbcProbe();
        TransactionManager manager = probe.manager();
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

        assertSame(nestedFailure, thrown.getCause());
        probe.assertEvents("getConnection", "setAutoCommit(false)", "rollback",
                "setAutoCommit(true)", "close");
        assertNull(TransactionContext.current());
    }

    @Test
    void caughtNestedErrorAlsoMarksSharedTransactionRollbackOnly() {
        JdbcProbe probe = new JdbcProbe();
        TransactionManager manager = probe.manager();
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
        assertFalse(probe.events.contains("commit"));
        assertTrue(probe.events.contains("rollback"));
        assertNull(TransactionContext.current());
    }

    @Test
    void uncaughtNestedFailurePreservesOriginalExceptionAndRollsBackOnce() {
        JdbcProbe probe = new JdbcProbe();
        TransactionManager manager = probe.manager();
        IllegalArgumentException nestedFailure = new IllegalArgumentException("uncaught nested failure");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> manager.execute(() -> manager.execute(() -> {
                    throw nestedFailure;
                })));

        assertSame(nestedFailure, thrown);
        assertEquals(1, probe.events.stream().filter("rollback"::equals).count());
        assertFalse(probe.events.contains("commit"));
        assertNull(TransactionContext.current());
    }

    @Test
    void topLevelErrorRollsBackAndClearsContext() {
        JdbcProbe probe = new JdbcProbe();
        AssertionError failure = new AssertionError("fatal callback failure");

        AssertionError thrown = assertThrows(AssertionError.class,
                () -> probe.manager().execute(() -> {
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertTrue(probe.events.contains("rollback"));
        assertFalse(probe.events.contains("commit"));
        assertNull(TransactionContext.current());
    }

    private static TransactionSystemException onlyBoundaryFailure(Throwable failure) {
        assertEquals(1, failure.getSuppressed().length,
                "边界/清理失败必须作为唯一、可读的 suppressed exception 保留");
        return assertInstanceOf(TransactionSystemException.class, failure.getSuppressed()[0]);
    }

    private enum Failure {
        ACQUIRE,
        BEGIN,
        COMMIT,
        ROLLBACK,
        RESET,
        CLOSE,
        DISCARD
    }

    private static final class JdbcProbe {
        private final EnumSet<Failure> failures = EnumSet.noneOf(Failure.class);
        private final List<String> events = new ArrayList<>();
        private final Connection connection;
        private final DataSource dataSource;
        private final DataSource ownerDataSource;

        private JdbcProbe(Failure... configuredFailures) {
            failures.addAll(List.of(configuredFailures));
            connection = (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    this::invokeConnection);
            dataSource = dataSourceProxy(false);
            ownerDataSource = dataSourceProxy(true);
        }

        private TransactionManager manager() {
            return new TransactionManager(dataSource, connectionToDiscard -> {
                events.add("discard");
                failIf(Failure.DISCARD, "discard failure");
            });
        }

        private DataSource dataSourceProxy(boolean owner) {
            Class<?>[] interfaces = owner
                    ? new Class<?>[]{ConnectionDiscardingDataSource.class}
                    : new Class<?>[]{DataSource.class};
            return (DataSource) Proxy.newProxyInstance(
                    ConnectionDiscardingDataSource.class.getClassLoader(),
                    interfaces,
                    (proxy, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return invokeObjectMethod(proxy, method, args);
                        }
                        if (method.getName().equals("getConnection")) {
                            events.add("getConnection");
                            failIf(Failure.ACQUIRE, "acquire failure");
                            return connection;
                        }
                        if (method.getName().equals("discard")) {
                            events.add("ownerDiscard");
                            failIf(Failure.DISCARD, "discard failure");
                            return null;
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
                    boolean autoCommit = Boolean.TRUE.equals(args[0]);
                    events.add("setAutoCommit(" + autoCommit + ")");
                    failIf(autoCommit ? Failure.RESET : Failure.BEGIN,
                            autoCommit ? "reset failure" : "begin failure");
                    yield null;
                }
                case "commit" -> {
                    events.add("commit");
                    failIf(Failure.COMMIT, "commit failure");
                    yield null;
                }
                case "rollback" -> {
                    events.add("rollback");
                    failIf(Failure.ROLLBACK, "rollback failure");
                    yield null;
                }
                case "close" -> {
                    events.add("close");
                    failIf(Failure.CLOSE, "close failure");
                    yield null;
                }
                case "abort" -> {
                    events.add("abort");
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        }

        private void failIf(Failure point, String message) throws SQLException {
            if (failures.contains(point)) {
                throw new SQLException(message);
            }
        }

        private void assertEvents(String... expected) {
            assertEquals(List.of(expected), events);
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
