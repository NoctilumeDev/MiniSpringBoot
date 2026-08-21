package com.minispring.demo.app;

import com.minispring.context.annotation.Autowired;
import com.minispring.context.annotation.Service;
import com.minispring.jdbc.JdbcTemplate;
import com.minispring.jdbc.transaction.Transactional;
import com.minispring.web.servlet.ResponseStatusException;

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
        // 在事务内使用同一连接与快照读取双方余额，使返回值与本次提交保持一致。
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
            // 账户不存在返回 404。
            throw new ResponseStatusException(404, "账户不存在: " + id);
        }
        return value;
    }

    private void debit(long fromId, BigDecimal amount) {
        // API 层独立校验正数金额，避免负值反转扣款语义；参数错误映射为 400。
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("转账金额必须为正数: " + amount);
        }
        // 扣款使用带余额条件的单条原子 UPDATE，由 MySQL 行锁保证并发裁决；
        // 账户不存在或余额不足时影响行数为 0。
        int updated = jdbc.update("UPDATE accounts SET balance = balance - ? WHERE id = ? AND balance >= ?",
                amount, fromId, amount);
        if (updated != 1) {
            // 账户不存在或余额不足属于请求侧问题，返回 400。
            throw new ResponseStatusException(400, "扣款失败：账户 " + fromId + " 不存在或余额不足");
        }
    }

    private void credit(long toId, BigDecimal amount) {
        int updated = jdbc.update("UPDATE accounts SET balance = balance + ? WHERE id = ?", amount, toId);
        if (updated != 1) {
            throw new ResponseStatusException(404, "入款失败，账户 " + toId + " 不存在");
        }
    }
}
