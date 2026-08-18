package com.minispring.jdbc.transaction;

/**
 * 事务回调：{@link TransactionManager#execute} 的函数式入参。
 */
@FunctionalInterface
public interface TransactionCallback<T> {

    T doInTransaction() throws Exception;
}
