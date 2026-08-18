import React, { useEffect, useState } from 'react';
import { api } from './api.js';

/**
 * 转账演示页：[正常转账] 验证事务提交（余额精确变动）、
 * [中途失败转账] 验证事务回滚（扣款已执行但异常回滚，余额不变）。
 * 两个余额卡操作后刷新，页面读数即 MySQL 读数。
 */
export default function TransferPage({ onError, onNotice }) {
  const [from, setFrom] = useState('1');
  const [to, setTo] = useState('2');
  const [amount, setAmount] = useState('10');
  const [balances, setBalances] = useState({ 1: null, 2: null });
  const [busy, setBusy] = useState(false);

  const reloadBalances = async (ids = [1, 2]) => {
    try {
      const next = { ...balances };
      for (const id of ids) {
        next[id] = await api.balance(id);
      }
      setBalances(next);
    } catch (e) {
      onError(`读取余额失败 — ${e.message}`);
    }
  };

  useEffect(() => {
    reloadBalances();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const run = async (kind) => {
    // 审查修复（M9 复审 I3）：Number.isFinite 先行——NaN<=0 在 JS 中为 false，
    // 此前非数字 amount（如 'abc'）会绕过校验原样进 URL
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      onError('amount 必须为正数');
      return;
    }
    setBusy(true);
    try {
      if (kind === 'ok') {
        const r = await api.transfer(from, to, amount);
        onNotice(`转账提交成功：#${r.from} → ${r.fromBalance}，#${r.to} → ${r.toBalance}`);
      } else {
        // 预期 500：服务端在扣款后刻意抛异常 → 事务回滚 → 余额不变
        await api.transferFail(from, to, amount);
        onNotice('不应到达：transfer-fail 预期抛异常');
      }
    } catch (err) {
      if (kind === 'fail') {
        onError(`[中途失败] 后端已回滚 — ${err.message}（两账户余额应保持不变）`);
      } else {
        onError(`转账失败 — ${err.message}`);
      }
    } finally {
      setBusy(false);
      await reloadBalances([from, to].map(Number).filter((n) => n === 1 || n === 2));
    }
  };

  const card = (id) => (
    <div className="balance-card" key={id}>
      <h3>账户 #{id}</h3>
      <p className="amount">{balances[id] ? `${balances[id].balance}` : '…'}</p>
      <button onClick={() => reloadBalances([id])}>刷新</button>
    </div>
  );

  return (
    <section>
      <h2>转账（accounts 表，@Transactional）</h2>

      <div className="balance-row">
        {card(1)}
        {card(2)}
      </div>

      <form className="row-form" onSubmit={(e) => e.preventDefault()}>
        <label>
          from
          <input value={from} onChange={(e) => setFrom(e.target.value)} size="4" />
        </label>
        <label>
          to
          <input value={to} onChange={(e) => setTo(e.target.value)} size="4" />
        </label>
        <label>
          amount
          <input value={amount} onChange={(e) => setAmount(e.target.value)} size="6" />
        </label>
        <button disabled={busy} onClick={() => run('ok')}>正常转账（提交）</button>
        <button className="danger" disabled={busy} onClick={() => run('fail')}>中途失败转账（回滚）</button>
      </form>

      <p className="hint">
        「中途失败」：服务端先扣款再抛异常，事务整体回滚——余额卡与 MySQL 均应不变；
        可用 <code>docker exec</code> 直查 <code>accounts</code> 表复核。
      </p>
    </section>
  );
}
