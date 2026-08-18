package com.minispring.demo.app;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Service;
import com.minispring.jdbc.JdbcTemplate;
import com.minispring.jdbc.transaction.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 转账服务实现：两条 UPDATE 在同一事务里（{@code @Transactional} 切面驱动，
 * 事务内 SQL 经 TransactionContext 复用同一物理连接——单测 CONNECTION_ID 已锚定该机制）。
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final JdbcTemplate jdbc;

    @Autowired
    public AccountServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Map<String, Object> transfer(long fromId, long toId, BigDecimal amount) {
        debit(fromId, amount);
        credit(toId, amount);
        // P0-6：事务内读取双方余额（复用同一连接、同一快照，与“未读到的提交一致”），
        // 而非提交后再读——那样在并发下可能返回不含本次转账的旧值。
        return Map.of(
                "from", fromId, "to", toId,
                "fromBalance", balance(fromId),
                "toBalance", balance(toId));
    }

    @Override
    @Transactional
    public void transferFailInMiddle(long fromId, long toId, BigDecimal amount) {
        debit(fromId, amount);
        // 扣款已执行（同一事务连接内），此处抛异常 → 整个事务回滚，扣款不得落库
        throw new IllegalStateException("transfer-fail-in-middle（V3 回滚验收的刻意异常）");
    }

    @Override
    public BigDecimal balance(long id) {
        BigDecimal value = jdbc.queryOne("SELECT balance FROM accounts WHERE id = ?",
                rs -> rs.getBigDecimal("balance"), id);
        if (value == null) {
            throw new IllegalArgumentException("账户不存在: " + id);
        }
        return value;
    }

    private void debit(long fromId, BigDecimal amount) {
        // 29-31（负数转账修复）：负数金额会让 WHERE balance >= ? 恒真（余额 >= 负数），
        // 变成「反向转账 + 余额可被打负」——前端拦截形同虚设，API 层必须自校验。
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("转账金额必须为正数: " + amount);
        }
        // P0-4（并发竞态修复）：不再「先 SELECT 余额再 UPDATE」的 check-then-act——
        // RR 隔离下两个并发扣款可同时通过余额检查导致透支。改为单条原子 UPDATE
        // 带 WHERE balance >= ? 约束：余额不足时影响行数为 0，由 MySQL 行锁保证原子性。
        int updated = jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?",
                amount, fromId, amount);
        if (updated != 1) {
            // 可能因余额不足（条件不命中）或账户不存在而失败，统一给可读错误
            throw new IllegalStateException("扣款失败：账户 " + fromId + " 不存在或余额不足");
        }
    }

    private void credit(long toId, BigDecimal amount) {
        int updated = jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, toId);
        if (updated != 1) {
            throw new IllegalStateException("入款失败，账户 " + toId + " 不存在");
        }
    }
}
