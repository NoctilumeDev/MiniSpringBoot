package com.minispring.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 「一行结果 → 一个对象」的映射规则（等价 Spring 的 {@code RowMapper}）。
 * 教学意义：把 JDBC 的行级反序列化从模板代码里拆出来，模板类只管流程。
 */
@FunctionalInterface
public interface RowMapper<T> {

    /** 把当前行映射为对象（rs 已定位到该行，不要调用 next）。 */
    T map(ResultSet rs) throws SQLException;
}
