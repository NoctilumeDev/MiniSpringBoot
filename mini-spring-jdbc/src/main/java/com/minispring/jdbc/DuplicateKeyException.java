package com.minispring.jdbc;

import java.sql.SQLException;

/**
 * 唯一键 / 约束冲突（MySQL SQLState 23xxx 或 error 1062/1452 等）。V8 负例验收的判别依据。
 */
public class DuplicateKeyException extends DataAccessException {

    public DuplicateKeyException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 按 SQLState / 错误码判断是否唯一键或约束冲突。 */
    static boolean isConstraintViolation(SQLException e) {
        String state = e.getSQLState();
        return state != null && state.startsWith("23");
    }
}
