package com.minispring.jdbc.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 丢弃事务结果未知、或无法恢复到可复用状态的连接。
 *
 * <p>UNKNOWN 连接不能通过普通 {@link Connection#close()} 盲目归还连接池；默认策略使用
 * JDBC 4.1 的 {@link Connection#abort(java.util.concurrent.Executor)} 终止物理连接。
 * 连接池可提供更了解所有权的实现（例如 Hikari 的持有者驱逐）。
 */
@FunctionalInterface
public interface ConnectionDiscarder {

    void discard(Connection connection) throws SQLException;

    static ConnectionDiscarder aborting() {
        return connection -> connection.abort(Runnable::run);
    }
}
