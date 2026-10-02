import assert from 'node:assert/strict';
import test from 'node:test';
import { clearOwnedError, createFeedbackState, withError, withNotice } from './feedback.js';

test('a successful balance refresh clears its own stale failure', () => {
  const failed = withError(createFeedbackState(), 'transfer', 'HTTP 502', 'balance-read');
  const recovered = clearOwnedError(failed, 'transfer', 'balance-read');
  assert.equal(recovered.transfer.error, null);
  assert.equal(recovered.transfer.errorSource, null);
});

test('a balance refresh cannot erase a transfer outcome owned by another operation', () => {
  const failedTransfer = withError(createFeedbackState(), 'transfer', '事务已回滚', 'transfer');
  const refreshed = clearOwnedError(failedTransfer, 'transfer', 'balance-read');
  assert.strictEqual(refreshed, failedTransfer);
  assert.equal(refreshed.transfer.error, '事务已回滚');
});

test('a background balance read failure cannot replace a completed transfer result', () => {
  const committed = withNotice(createFeedbackState(), 'transfer', '转账提交成功');
  const afterReadFailure = withError(
    committed,
    'transfer',
    '读取余额失败',
    'balance-read',
    false,
  );
  assert.strictEqual(afterReadFailure, committed);
  assert.equal(afterReadFailure.transfer.notice, '转账提交成功');
});
