package com.minispring.jdbc.transaction;

import java.sql.Connection;

/**
 * 事务上下文：把「当前线程绑定的活动事务连接」挂在 ThreadLocal 上——
 * 等价 Spring 的 TransactionSynchronizationManager 的连接绑定（教学简化版）。
 *
 * <p>三个角色通过它协作：{@link TransactionManager} 开事务时 bind、提交回滚后 clear；
 * {@code JdbcTemplate} 每次操作先看这里——有活动事务连接就复用（不关、不归还），
 * 没有才从 DataSource 自取自还。
 *
 * <p><b>线程池安全纪律</b>：bind/clear 必须严格成对（TransactionManager 用 try-finally 保证）。
 * SunHttpServer 是 cached 线程池，线程会被复用——一旦 clear 失误，下一个请求会「继承」
 * 上一个事务的半开连接，那就是脏数据跨请求泄漏。
 */
public final class TransactionContext {

    private static final ThreadLocal<Connection> CURRENT = new ThreadLocal<>();

    private TransactionContext() {
    }

    /** 绑定活动事务连接（事务开始时调用；调用方负责在 finally 中 clear）。 */
    public static void bind(Connection connection) {
        CURRENT.set(connection);
    }

    /** 当前线程的活动事务连接；不在事务中返回 {@code null}。 */
    public static Connection current() {
        return CURRENT.get();
    }

    /** 清除绑定（事务结束后必须调用，防线程复用串事务）。 */
    public static void clear() {
        CURRENT.remove();
    }
}
