package com.minispring.jdbc.transaction;

import com.minispring.jdbc.DataAccessException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 编程式事务管理器（等价 Spring 的 {@code TransactionTemplate} 背后的执行逻辑，教学简化版）。
 *
 * <p>语义子集（教学项目显式约定）：
 * <ul>
 *   <li>传播行为只有 <b>REQUIRED</b> 一种——当前线程已在事务中则加入（复用同一连接）；</li>
 *   <li>内层参与者失败会把共享事务标记为 rollback-only；即使异常被外层业务捕获，
 *       最外层边界也会回滚并抛出 {@link UnexpectedRollbackException}；</li>
 *   <li>回滚规则：回调抛任何异常（RuntimeException、受检异常乃至 Error——未到达 commit
 *       的路径在 finally 统一回滚）都回滚；</li>
 *   <li>隔离级别用数据源默认（MySQL RR），不做定制。</li>
 * </ul>
 */
public class TransactionManager {

    private final DataSource dataSource;

    public TransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 在事务中执行回调：提交或回滚后返回结果 / 重抛异常。 */
    public <T> T execute(TransactionCallback<T> action) {
        // REQUIRED：已有活动事务（本线程）则直接加入，不开新连接、不动提交边界
        if (TransactionContext.current() != null) {
            try {
                return action.doInTransaction();
            } catch (Throwable failure) {
                TransactionContext.markRollbackOnly(failure);
                throw propagate(failure, "事务执行失败（受检异常上抛）");
            }
        }
        Connection connection = null;
        boolean committed = false;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            TransactionContext.bind(connection);
            T result = action.doInTransaction();
            if (TransactionContext.isRollbackOnly()) {
                throw new UnexpectedRollbackException(
                        "共享的 REQUIRED 事务已被内层参与者标记为 rollback-only",
                        TransactionContext.rollbackCause());
            }
            connection.commit();
            committed = true;
            return result;
        } catch (Throwable failure) {
            throw propagate(failure, "事务执行失败（受检异常触发回滚）");
        } finally {
            // 回滚收敛到唯一位置：任何未到达 commit 的路径（Exception、Error 乃至其他
            // Throwable——catch(Exception) 拦不住 Error）都必须先终结半开事务再归还连接。
            // 未提交事务必须先回滚，再恢复 auto-commit；JDBC 在事务中切换 auto-commit
            // 会构成隐式提交。该路径也保证 Error 与 Exception 采用相同的回滚语义。
            if (!committed) {
                rollbackQuietly(connection);
            }
            // 线程池复用纪律：clear 必须在 finally（见 TransactionContext 的 javadoc）
            TransactionContext.clear();
            closeQuietly(connection);
        }
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

    private void rollbackQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            // 回滚失败是严重问题但不能再抛（会覆盖业务异常），打出来留证据
            System.err.println("事务回滚失败（连接将由池回收）: " + rollbackFailure);
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.setAutoCommit(true);
        } catch (SQLException resetFailure) {
            // 恢复连接状态失败不得阻断 close；否则无池连接会直接泄漏，
            // 连接池也失去唯一的归还机会。
            System.err.println("恢复事务连接 auto-commit 失败（仍将尝试关闭）: " + resetFailure);
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            System.err.println("关闭事务连接失败（可能已由池兜底）: " + closeFailure);
        }
    }
}
