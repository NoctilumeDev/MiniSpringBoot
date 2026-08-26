package com.minispring.jdbc.transaction;

import java.sql.Connection;

/**
 * 事务上下文：把「当前线程绑定的活动事务状态」挂在 ThreadLocal 上——
 * 等价 Spring 的 TransactionSynchronizationManager 的连接绑定（教学简化版）。
 *
 * <p>三个角色通过它协作：{@link TransactionManager} 开事务时 bind、提交回滚后 clear；
 * {@code JdbcTemplate} 每次操作先看这里——有活动事务连接就复用（不关、不归还），
 * 没有才从 DataSource 自取自还。状态还保留 rollback-only 标记：参与同一
 * REQUIRED 事务的内层调用失败后，即使外层业务捕获了异常，最外层事务也不得提交。
 *
 * <p><b>线程池安全纪律</b>：bind/clear 必须严格成对（TransactionManager 用 try-finally 保证）。
 * SunHttpServer 的固定工作线程会被复用——一旦 clear 失误，下一个请求会「继承」
 * 上一个事务的半开连接，那就是脏数据跨请求泄漏。
 */
public final class TransactionContext {

    private static final ThreadLocal<TransactionState> CURRENT = new ThreadLocal<>();

    private TransactionContext() {
    }

    /** 绑定活动事务连接（事务开始时调用；调用方负责在 finally 中 clear）。 */
    public static void bind(Connection connection) {
        CURRENT.set(new TransactionState(connection));
    }

    /** 当前线程的活动事务连接；不在事务中返回 {@code null}。 */
    public static Connection current() {
        TransactionState state = CURRENT.get();
        return state == null ? null : state.connection;
    }

    /** 将当前事务标记为只能回滚，并保留第一个触发原因便于定位。 */
    static void markRollbackOnly(Throwable cause) {
        TransactionState state = CURRENT.get();
        if (state == null) {
            throw new IllegalStateException("当前线程没有活动事务");
        }
        state.rollbackOnly = true;
        if (state.rollbackCause == null) {
            state.rollbackCause = cause;
        }
    }

    static boolean isRollbackOnly() {
        TransactionState state = CURRENT.get();
        return state != null && state.rollbackOnly;
    }

    static Throwable rollbackCause() {
        TransactionState state = CURRENT.get();
        return state == null ? null : state.rollbackCause;
    }

    /** 清除绑定（事务结束后必须调用，防线程复用串事务）。 */
    public static void clear() {
        CURRENT.remove();
    }

    private static final class TransactionState {
        private final Connection connection;
        private boolean rollbackOnly;
        private Throwable rollbackCause;

        private TransactionState(Connection connection) {
            this.connection = connection;
        }
    }
}
