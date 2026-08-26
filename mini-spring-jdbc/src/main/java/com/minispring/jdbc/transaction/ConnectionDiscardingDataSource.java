package com.minispring.jdbc.transaction;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 能从自己的池/资源域中真正淘汰连接的 DataSource。
 *
 * <p>它把“获得连接”和“未知状态连接如何销毁”放回同一个资源所有者；普通 DataSource
 * 仍可依赖 JDBC {@code Connection.abort()} 的默认策略。
 */
public interface ConnectionDiscardingDataSource extends DataSource {

    void discard(Connection connection) throws SQLException;
}
