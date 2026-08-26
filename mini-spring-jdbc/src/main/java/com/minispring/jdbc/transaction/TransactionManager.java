package com.minispring.jdbc.transaction;

import com.minispring.jdbc.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * 编程式事务管理器（等价 Spring 的 {@code TransactionTemplate} 背后的执行逻辑，教学简化版）。
 *
 * <p>语义子集（教学项目显式约定）：
 * <ul>
 *   <li>传播行为只有 <b>REQUIRED</b>：当前线程已有事务时复用同一连接；</li>
 *   <li>内层参与者失败会把共享事务标记为 rollback-only；</li>
 *   <li>回调抛出任何 {@link Throwable} 都触发回滚；</li>
 *   <li>commit/rollback 调用失败时，事务结果为 {@link TransactionOutcome#UNKNOWN}，
 *       连接必须被丢弃，调用方不得把它当成可安全重试的失败。</li>
 * </ul>
 *
 * <p>这里刻意把“事务终局”和“连接清理”分开：已知提交/回滚后才允许恢复
 * auto-commit 并归还连接；结果未知或连接状态无法恢复时，只能由资源所有者丢弃。
 */
public class TransactionManager {

    private final DataSource dataSource;
    private final ConnectionDiscarder connectionDiscarder;

    public TransactionManager(DataSource dataSource) {
        this(dataSource, discarderFor(dataSource));
    }

    TransactionManager(DataSource dataSource, ConnectionDiscarder connectionDiscarder) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.connectionDiscarder = Objects.requireNonNull(connectionDiscarder, "connectionDiscarder");
    }

    /** 在事务中执行回调：提交或回滚后返回结果 / 重抛异常。 */
    public <T> T execute(TransactionCallback<T> action) {
        Objects.requireNonNull(action, "action");

        // REQUIRED：已有活动事务（本线程）则直接加入，不开新连接、不动提交边界。
        if (TransactionContext.current() != null) {
            try {
                return action.doInTransaction();
            } catch (Throwable failure) {
                TransactionContext.markRollbackOnly(failure);
                throw propagate(failure, "事务执行失败（受检异常上抛）");
            }
        }

        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (Throwable acquisitionFailure) {
            throw propagate(acquisitionFailure, "获取数据库连接失败");
        }

        BoundaryState state = BoundaryState.ACQUIRED;
        try {
            try {
                connection.setAutoCommit(false);
                state = BoundaryState.ACTIVE;
            } catch (Throwable beginFailure) {
                BoundaryState failedAt = state;
                state = BoundaryState.UNKNOWN;
                TransactionSystemException failure = boundaryFailure(
                        "开启事务失败（" + failedAt + " -> " + state + "），事务结果未知",
                        TransactionOutcome.UNKNOWN,
                        beginFailure);
                discard(connection, failure);
                throw failure;
            }

            TransactionContext.bind(connection);

            T result;
            try {
                result = action.doInTransaction();
                if (TransactionContext.isRollbackOnly()) {
                    throw new UnexpectedRollbackException(
                            "共享的 REQUIRED 事务已被内层参与者标记为 rollback-only",
                            TransactionContext.rollbackCause());
                }
            } catch (Throwable businessFailure) {
                state = rollback(connection, state, businessFailure);
                TransactionContext.clear();
                attachCleanupFailure(businessFailure, releaseKnown(connection,
                        TransactionOutcome.ROLLED_BACK, state));
                throw propagate(businessFailure, "事务执行失败（受检异常触发回滚）");
            }

            state = BoundaryState.COMMITTING;
            try {
                connection.commit();
                state = BoundaryState.COMMITTED;
            } catch (Throwable commitFailure) {
                state = BoundaryState.UNKNOWN;
                TransactionContext.clear();
                TransactionSystemException failure = boundaryFailure(
                        "commit 调用失败，数据库是否提交未知；不得盲目重试",
                        TransactionOutcome.UNKNOWN,
                        commitFailure);
                discard(connection, failure);
                throw failure;
            }

            TransactionContext.clear();
            TransactionSystemException cleanupFailure =
                    releaseKnown(connection, TransactionOutcome.COMMITTED, state);
            if (cleanupFailure != null) {
                throw cleanupFailure;
            }
            return result;
        } finally {
            // 先于任何可能抛错的清理动作执行；线程池复用时绝不能泄漏半开事务上下文。
            TransactionContext.clear();
        }
    }

    private BoundaryState rollback(Connection connection,
                                   BoundaryState state,
                                   Throwable businessFailure) {
        if (state != BoundaryState.ACTIVE) {
            throw new IllegalStateException("只能从 ACTIVE 状态回滚，当前状态: " + state);
        }
        state = BoundaryState.ROLLING_BACK;
        try {
            connection.rollback();
            return BoundaryState.ROLLED_BACK;
        } catch (Throwable rollbackFailure) {
            state = BoundaryState.UNKNOWN;
            TransactionContext.clear();
            TransactionSystemException boundaryFailure = boundaryFailure(
                    "rollback 调用失败，数据库是否回滚未知；保留原始业务异常",
                    TransactionOutcome.UNKNOWN,
                    rollbackFailure);
            discard(connection, boundaryFailure);
            attachCleanupFailure(businessFailure, boundaryFailure);
            throw propagate(businessFailure, "事务执行失败且回滚结果未知");
        }
    }

    /**
     * 释放已知终局的连接。恢复/关闭失败不会改变数据库终局，但连接已不再适合复用，
     * 所以仍需丢弃，并把已知终局带给调用方。
     */
    private TransactionSystemException releaseKnown(Connection connection,
                                                     TransactionOutcome outcome,
                                                     BoundaryState state) {
        try {
            connection.setAutoCommit(true);
        } catch (Throwable resetFailure) {
            TransactionSystemException failure = boundaryFailure(
                    "事务已" + outcomeText(outcome) + "，但恢复 auto-commit 失败；连接已丢弃",
                    outcome,
                    resetFailure);
            discard(connection, failure);
            return failure;
        }

        try {
            connection.close();
            return null;
        } catch (Throwable closeFailure) {
            TransactionSystemException failure = boundaryFailure(
                    "事务已" + outcomeText(outcome) + "，但归还连接失败；连接已丢弃（状态 " + state + "）",
                    outcome,
                    closeFailure);
            discard(connection, failure);
            return failure;
        }
    }

    private void discard(Connection connection, TransactionSystemException primaryFailure) {
        try {
            connectionDiscarder.discard(connection);
        } catch (Throwable discardFailure) {
            primaryFailure.addSuppressed(boundaryFailure(
                    "丢弃不可复用连接失败，需要检查连接池/数据库健康状态",
                    primaryFailure.outcome(),
                    discardFailure));
        }
    }

    private void attachCleanupFailure(Throwable primary, TransactionSystemException cleanupFailure) {
        if (cleanupFailure != null) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private TransactionSystemException boundaryFailure(String message,
                                                       TransactionOutcome outcome,
                                                       Throwable cause) {
        return new TransactionSystemException(message, outcome, cause);
    }

    private RuntimeException propagate(Throwable failure, String checkedExceptionMessage) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new DataAccessException(checkedExceptionMessage + ": " + failure.getMessage(), failure);
    }

    private static ConnectionDiscarder discarderFor(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        if (dataSource instanceof ConnectionDiscardingDataSource owner) {
            return owner::discard;
        }
        return ConnectionDiscarder.aborting();
    }

    private static String outcomeText(TransactionOutcome outcome) {
        return switch (outcome) {
            case COMMITTED -> "提交";
            case ROLLED_BACK -> "回滚";
            case UNKNOWN -> "处于未知状态";
        };
    }

    private enum BoundaryState {
        ACQUIRED,
        ACTIVE,
        COMMITTING,
        COMMITTED,
        ROLLING_BACK,
        ROLLED_BACK,
        UNKNOWN
    }
}
