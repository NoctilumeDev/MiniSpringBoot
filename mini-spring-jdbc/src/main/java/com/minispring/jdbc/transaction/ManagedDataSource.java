package com.minispring.jdbc.transaction;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * 把连接获取、未知状态连接丢弃和资源关闭收拢到同一个 DataSource 所有权边界。
 *
 * <p>本类只依赖 JDBC；具体连接池通过组合注入自己的驱逐动作，避免可选连接池类型
 * 穿透到框架的稳定模块/classpath 边界。
 */
public final class ManagedDataSource implements ConnectionDiscardingDataSource, AutoCloseable {

    private final DataSource delegate;
    private final ConnectionDiscarder connectionDiscarder;

    public ManagedDataSource(DataSource delegate, ConnectionDiscarder connectionDiscarder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.connectionDiscarder = Objects.requireNonNull(connectionDiscarder, "connectionDiscarder");
    }

    @Override
    public Connection getConnection() throws SQLException {
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return delegate.getConnection(username, password);
    }

    @Override
    public void discard(Connection connection) throws SQLException {
        connectionDiscarder.discard(connection);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return delegate.getLogWriter();
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        delegate.setLogWriter(out);
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        delegate.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return delegate.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        if (iface.isInstance(delegate)) {
            return iface.cast(delegate);
        }
        return delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || iface.isInstance(delegate) || delegate.isWrapperFor(iface);
    }

    @Override
    public void close() {
        if (delegate instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (RuntimeException runtimeFailure) {
                throw runtimeFailure;
            } catch (Exception checkedFailure) {
                throw new IllegalStateException("关闭底层 DataSource 失败", checkedFailure);
            }
        }
    }
}
