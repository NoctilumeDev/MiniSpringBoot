package com.minispring.jdbc.transaction;

import com.minispring.jdbc.DataAccessException;

/**
 * 最外层事务边界本想提交，但共享的 REQUIRED 事务已被内层参与者标记为
 * rollback-only。该异常明确区分「业务回调直接失败」与「业务表面正常返回但事务不得提交」。
 */
public class UnexpectedRollbackException extends DataAccessException {

    public UnexpectedRollbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
