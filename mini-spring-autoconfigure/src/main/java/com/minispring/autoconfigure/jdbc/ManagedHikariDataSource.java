package com.minispring.autoconfigure.jdbc;

import com.minispring.jdbc.transaction.ConnectionDiscardingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;

/**
 * Hikari 连接池适配：UNKNOWN 事务连接由仍持有该连接的池所有者立即驱逐，
 * 不经过普通 close/recycle 路径。
 */
final class ManagedHikariDataSource extends HikariDataSource implements ConnectionDiscardingDataSource {

    ManagedHikariDataSource(HikariConfig configuration) {
        super(configuration);
    }

    @Override
    public void discard(Connection connection) {
        evictConnection(connection);
    }
}
