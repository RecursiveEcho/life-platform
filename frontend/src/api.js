import JSONbig from 'json-bigint';
const json = JSONbig({ storeAsString: true });
export async function request(path, { method = 'GET', body, token } = {}) {
  let response;
  try {
    response = await fetch(`/api${path}`, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(15000) });
  } catch { throw new Error('暂时连接不上服务，请稍后重试'); }
  const text = await response.text();
  let result;
  try { result = json.parse(text); } catch { throw new Error('服务暂时不可用，请稍后重试'); }
  if (!response.ok || result.code !== 200) {
    const error = new Error(result.message || '操作未完成');
    error.status = response.status;
    throw error;
  }
  return result.data;
}
