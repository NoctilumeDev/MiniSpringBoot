import React, { useEffect, useState } from 'react';
import {
  ArrowRight,
  ArrowRightLeft,
  CircleDollarSign,
  Database,
  RefreshCw,
  ShieldCheck,
  Undo2,
  WalletCards,
} from 'lucide-react';
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
        // 发送校验过的数值（L6）：校验 Number(amount) 但发送原始字符串时，
        // " 10 " 这类输入能过校验、到后端 new BigDecimal(" 10 ") 抛 NFE 变 500
        const r = await api.transfer(from, to, value);
        onNotice(`转账提交成功：#${r.from} → ${r.fromBalance}，#${r.to} → ${r.toBalance}`);
      } else {
        // 预期 500：服务端在扣款后刻意抛异常 → 事务回滚 → 余额不变
        await api.transferFail(from, to, value);
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

  const account = (id) => (
    <div className="balance-account" key={id}>
      <span className="account-id"><WalletCards size={17} aria-hidden="true" />账户 #{id}</span>
      <output className="amount" aria-label={`账户 ${id} 当前余额`}>
        {balances[id] !== null ? balances[id].balance : '…'}
      </output>
      <button type="button" className="quiet-action" onClick={() => reloadBalances([id])}>
        <RefreshCw size={15} aria-hidden="true" />刷新
      </button>
    </div>
  );

  return (
    <section className="workspace-view transfer-view" aria-labelledby="transfer-title">
      <header className="view-heading">
        <div>
          <p className="eyebrow">TRANSACTION CONTROL · ACCOUNT DOMAIN</p>
          <h2 id="transfer-title">转账 <span>@Transactional</span></h2>
          <p>同一条真实链路观察事务提交与异常回滚，余额结果以 MySQL 为准。</p>
        </div>
        <div className="view-counter" aria-label="事务控制台">
          <ArrowRightLeft size={20} aria-hidden="true" />
          <span><small>模式</small><strong>TX</strong></span>
        </div>
      </header>

      <div className="transfer-layout">
        <div className="balance-ledger">
          <div className="section-heading">
            <Database size={18} aria-hidden="true" />
            <div><span>账户余额</span><small>SELECT · accounts</small></div>
          </div>
          <div className="balance-row">
            {account(1)}
            {account(2)}
          </div>
        </div>

        <div className="transfer-command">
          <div className="section-heading">
            <CircleDollarSign size={18} aria-hidden="true" />
            <div><span>事务指令</span><small>COMMIT / ROLLBACK</small></div>
          </div>

          <form className="transaction-form" onSubmit={(e) => e.preventDefault()}>
            <div className="field-group">
              <label htmlFor="transfer-from">付款账户</label>
              <input
                id="transfer-from"
                type="number"
                min="1"
                step="1"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
              />
            </div>
            <span className="transfer-direction" aria-hidden="true"><ArrowRight size={20} /></span>
            <div className="field-group">
              <label htmlFor="transfer-to">收款账户</label>
              <input
                id="transfer-to"
                type="number"
                min="1"
                step="1"
                value={to}
                onChange={(e) => setTo(e.target.value)}
              />
            </div>
            <div className="field-group amount-field">
              <label htmlFor="transfer-amount">转账金额</label>
              <input
                id="transfer-amount"
                type="number"
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div className="transaction-actions">
              <button type="button" className="primary-action" disabled={busy} onClick={() => run('ok')}>
                <ShieldCheck size={17} aria-hidden="true" />正常转账 · 提交
              </button>
              <button type="button" className="danger" disabled={busy} onClick={() => run('fail')}>
                <Undo2 size={17} aria-hidden="true" />中途失败 · 回滚
              </button>
            </div>
          </form>
        </div>
      </div>

      <p className="hint">
        “中途失败”会在服务端扣款后主动抛出异常；事务整体回滚，界面余额与 MySQL 的 <code>accounts</code> 表均应保持不变。
      </p>
    </section>
  );
}
