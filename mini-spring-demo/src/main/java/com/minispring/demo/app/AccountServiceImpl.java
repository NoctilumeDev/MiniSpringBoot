package com.minispring.demo.app;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Service;
import com.minispring.jdbc.JdbcTemplate;
import com.minispring.jdbc.transaction.Transactional;

import java.math.BigDecimal;

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
    public void transfer(long fromId, long toId, BigDecimal amount) {
        debit(fromId, amount);
        credit(toId, amount);
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
        ensureAffordable(fromId, amount);
        int updated = jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ?", amount, fromId);
        if (updated != 1) {
            throw new IllegalStateException("扣款失败，账户 " + fromId + " 不存在");
        }
    }

    private void credit(long toId, BigDecimal amount) {
        int updated = jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, toId);
        if (updated != 1) {
            throw new IllegalStateException("入款失败，账户 " + toId + " 不存在");
        }
    }

    private void ensureAffordable(long fromId, BigDecimal amount) {
        if (balance(fromId).compareTo(amount) < 0) {
            throw new IllegalStateException("余额不足: 账户 " + fromId);
        }
    }
}
