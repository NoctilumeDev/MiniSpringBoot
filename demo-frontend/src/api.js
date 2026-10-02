/**
 * 后端 API 封装（唯一出口）：所有请求经 Vite proxy（/api → :9090）。
 * 错误纪律：4xx 保留服务端明确公开的业务消息；5xx 统一成可操作提示，
 * 内部异常、SQL、代理 HTML 与基础设施细节不得进入 UI。
 */
export function formatHttpError(status, responseText) {
  if (status >= 500) {
    return `HTTP ${status}: 服务暂时不可用，请稍后重试`;
  }
  const trimmed = responseText.trim();
  if (!trimmed) return `HTTP ${status}: 请求失败`;
  const withoutRepeatedStatus = trimmed.replace(new RegExp(`^${status}\\s+`), '');
  const withoutReasonPhrase = withoutRepeatedStatus.replace(/^[A-Za-z ]+:\s*/, '');
  return `HTTP ${status}: ${withoutReasonPhrase || '请求失败'}`;
}

async function request(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(formatHttpError(res.status, text));
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
