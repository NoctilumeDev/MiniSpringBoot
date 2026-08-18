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
            } catch (Exception e) {
                throw (e instanceof RuntimeException) ? (RuntimeException) e
                        : new DataAccessException("事务执行失败（受检异常上抛）: " + e.getMessage(), e);
            }
        }
        Connection connection = null;
        boolean committed = false;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            TransactionContext.bind(connection);
            T result = action.doInTransaction();
            connection.commit();
            committed = true;
            return result;
        } catch (Exception e) {
            throw (e instanceof RuntimeException) ? (RuntimeException) e
                    : new DataAccessException("事务执行失败（受检异常触发回滚）: " + e.getMessage(), e);
        } finally {
            // 回滚收敛到唯一位置：任何未到达 commit 的路径（Exception、Error 乃至其他
            // Throwable——catch(Exception) 拦不住 Error）都必须先终结半开事务再归还连接。
            // 否则 closeQuietly 的 setAutoCommit(true) 按 JDBC 规范构成<b>隐式提交</b>
            // （「事务进行中切换 auto-commit 会先提交」），扣款半途的转账会被静默提交
            // 而非回滚——审查修复（M9 复审 I2），与 Spring 对 Error 同样回滚的语义对齐。
            if (!committed) {
                rollbackQuietly(connection);
            }
            // 线程池复用纪律：clear 必须在 finally（见 TransactionContext 的 javadoc）
            TransactionContext.clear();
            closeQuietly(connection);
        }
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
            connection.close();
        } catch (SQLException e) {
            System.err.println("归还事务连接失败（可能已由池兜底）: " + e);
        }
    }
}
