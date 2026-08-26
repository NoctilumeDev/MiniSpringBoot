package com.minispring.jdbc.transaction;

/**
 * 数据库事务的权威终局。
 *
 * <p>{@link #UNKNOWN} 不是“失败”的同义词：commit/rollback 调用失败时，数据库端可能已经
 * 接受操作，也可能没有接受。调用方不得把 UNKNOWN 当成可安全重试的回滚结果。
 */
public enum TransactionOutcome {
    COMMITTED,
    ROLLED_BACK,
    UNKNOWN
}
