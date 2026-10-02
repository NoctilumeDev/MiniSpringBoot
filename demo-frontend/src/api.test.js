import assert from 'node:assert/strict';
import test from 'node:test';
import { formatHttpError } from './api.js';

test('5xx details and proxy HTML never reach the user-facing message', () => {
  assert.equal(
    formatHttpError(500, '500 Internal Server Error: SELECT * FROM users — uk_users_email'),
    'HTTP 500: 服务暂时不可用，请稍后重试',
  );
  assert.equal(
    formatHttpError(502, '<html><body>proxy internals</body></html>'),
    'HTTP 502: 服务暂时不可用，请稍后重试',
  );
});

test('safe 4xx business messages keep one HTTP status prefix', () => {
  assert.equal(formatHttpError(409, '409 Conflict: 该邮箱已存在'), 'HTTP 409: 该邮箱已存在');
  assert.equal(formatHttpError(404, '404 Not Found: 用户不存在: 99'), 'HTTP 404: 用户不存在: 99');
  assert.equal(formatHttpError(400, ''), 'HTTP 400: 请求失败');
});
