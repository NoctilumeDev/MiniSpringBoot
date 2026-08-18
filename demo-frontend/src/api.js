/**
 * 后端 API 封装（唯一出口）：所有请求经 Vite proxy（/api → :9090）。
 * 错误纪律：非 2xx 一律读出响应体文本抛 Error——后端可读错误消息
 * （如 M8 B9 修复后的「SQL 执行失败: …」）必须原样到达 UI，不许吞。
 */
async function request(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`HTTP ${res.status}: ${text}`);
  }
  const text = await res.text();
  return text === '' ? null : JSON.parse(text);
}

export const api = {
  // users（MySQL users 表）
  listUsers: () => request('GET', '/api/users'),
  createUser: (user) => request('POST', '/api/users', user),
  updateUser: (id, user) => request('PUT', `/api/users/${id}`, user),
  deleteUser: (id) => request('DELETE', `/api/users/${id}`),
  // accounts（MySQL accounts 表，转账事务）
  transfer: (from, to, amount) =>
    request('POST', `/api/accounts/transfer?from=${from}&to=${to}&amount=${amount}`),
  transferFail: (from, to, amount) =>
    request('POST', `/api/accounts/transfer-fail?from=${from}&to=${to}&amount=${amount}`),
  balance: (id) => request('GET', `/api/accounts/${id}`),
};
