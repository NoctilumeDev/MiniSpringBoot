package com.minispring.jdbc.transaction;

import com.minispring.jdbc.DataAccessException;

/**
 * 事务边界本身失败，而不是业务回调失败。
 *
 * <p>{@link #outcome()} 明确告诉上层：数据已经提交，还是结果未知。尤其是
 * {@link TransactionOutcome#COMMITTED} 的清理失败，不能被上层误当成“可以重试”。
 */
public class TransactionSystemException extends DataAccessException {

    private final TransactionOutcome outcome;

    public TransactionSystemException(String message, TransactionOutcome outcome) {
        super(message);
        this.outcome = outcome;
    }

    public TransactionSystemException(String message, TransactionOutcome outcome, Throwable cause) {
        super(message, cause);
        this.outcome = outcome;
    }

    public TransactionOutcome outcome() {
        return outcome;
    }
}
