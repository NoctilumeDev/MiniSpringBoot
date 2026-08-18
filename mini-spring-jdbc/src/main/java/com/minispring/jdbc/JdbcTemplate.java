package com.minispring.jdbc;

import com.minispring.jdbc.transaction.TransactionContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JdbcTemplate（Spring 同名类的教学子集）：把「取连接 → 预编译 → 填参 → 执行 → 关资源」
 * 的 JDBC 样板收敛掉，调用方只写 SQL 与行映射。
 *
 * <p>事务感知：每次操作先看 {@link TransactionContext}——线程上有活动事务连接就<b>复用</b>
 * （不关、不归还，归事务边界负责）；没有才从 DataSource 取、finally 里归还。
 * 这正是 Spring「同一事务内多条 SQL 共用同一连接」的机制内核。
 *
 * <p>异常翻译：{@link SQLException} 统一转 {@link DataAccessException}（约束冲突转
 * {@link DuplicateKeyException}），保留 cause。
 */
public class JdbcTemplate {

    private final DataSource dataSource;

    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** 查询多行。 */
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return inConnection(sql, conn -> {
            try (PreparedStatement ps = prepare(conn, sql, args); ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        });
    }

    /** 查询单行；无行返回 {@code null}（多于一行视为数据异常，抛错——与 Spring 语义一致）。 */
    public <T> T queryOne(String sql, RowMapper<T> mapper, Object... args) {
        List<T> results = query(sql, mapper, args);
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            throw new DataAccessException("期望单行结果，实际 " + results.size() + " 行: " + sql);
        }
        return results.get(0);
    }

    /** 增/删/改，返回影响行数。 */
    public int update(String sql, Object... args) {
        return inConnection(sql, conn -> {
            try (PreparedStatement ps = prepare(conn, sql, args)) {
                return ps.executeUpdate();
            }
        });
    }

    /** 插入并回填自增主键（GENERATED_KEYS），返回生成的主键值。 */
    public long insertAndReturnKey(String sql, Object... args) {
        return inConnection(sql, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                fillArgs(ps, args);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new DataAccessException("数据库未返回自增主键: " + sql);
                    }
                    return keys.getLong(1);
                }
            }
        });
    }

    // ---- 样板收敛 ----

    private <T> T inConnection(String sql, SqlWork<T> work) {
        // 事务感知：活动事务连接复用（不关）；否则自取自还
        Connection txConnection = TransactionContext.current();
        if (txConnection != null) {
            try {
                return work.doInConnection(txConnection);
            } catch (SQLException e) {
                throw translate(sql, e);
            }
        }
        try (Connection conn = dataSource.getConnection()) {
            return work.doInConnection(conn);
        } catch (SQLException e) {
            throw translate(sql, e);
        }
    }

    private PreparedStatement prepare(Connection conn, String sql, Object[] args) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        fillArgs(ps, args);
        return ps;
    }

    private void fillArgs(PreparedStatement ps, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

    /**
     * SQLException → DataAccessException 翻译。
     * M9 纪律（错误保真）：包装消息必须携带根因文本（{@code e.getMessage()}）——
     * 只留 SQL 丢根因的话，DB 断连/超时类故障在 HTTP 500 里看不出「为什么失败」
     * （M9 V7 浏览器实测揪出：事务路径只见「事务执行失败」不见连接池根因）。
     */
    private DataAccessException translate(String sql, SQLException e) {
        if (DuplicateKeyException.isConstraintViolation(e)) {
            return new DuplicateKeyException("唯一键或约束冲突: " + sql + " — " + e.getMessage(), e);
        }
        return new DataAccessException("SQL 执行失败: " + sql + " — " + e.getMessage(), e);
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T doInConnection(Connection conn) throws SQLException;
    }
}
