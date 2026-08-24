/**
 * 后端 API 封装（唯一出口）：所有请求经 Vite proxy（/api → :9090）。
 * 错误纪律：非 2xx 一律读取响应体文本并抛出 Error，确保后端的可读错误消息
 * 原样到达 UI。
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
  // accounts（MySQL accounts 表，转账事务）。query 参数统一编码，确保 '&'/'#'
  // 等字符不会改变 URL 结构。
  transfer: (from, to, amount) =>
    request('POST', `/api/accounts/transfer?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&amount=${encodeURIComponent(amount)}`),
  transferFail: (from, to, amount) =>
    request('POST', `/api/accounts/transfer-fail?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&amount=${encodeURIComponent(amount)}`),
  balance: (id) => request('GET', `/api/accounts/${id}`),
};
