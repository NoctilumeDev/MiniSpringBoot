package com.minispring.jdbc;

/**
 * 数据访问异常基类（unchecked，等价 Spring 的 DataAccessException）：把受检的
 * {@link java.sql.SQLException} 的「错误码森林」收敛为一个统一类型，保留 cause 便于排查。
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
