import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import https from 'node:https';
import { mkdir, writeFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';

const GIB = 1024 ** 3;
const MAX_LATENCY_SAMPLES = 200_000;
const DEFAULT_ENDPOINTS = ['/health/live', '/api/users', '/api/accounts/1', '/api/accounts/2'];
const httpAgent = new http.Agent({ keepAlive: true, maxSockets: 128, maxFreeSockets: 128, scheduling: 'lifo' });
const httpsAgent = new https.Agent({ keepAlive: true, maxSockets: 128, maxFreeSockets: 128, scheduling: 'lifo' });

function parseNumber(name, value, { min = 0, integer = false } = {}) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed < min || (integer && !Number.isInteger(parsed))) {
    throw new Error(`--${name} 必须是${integer ? '整数' : '数字'}且 >= ${min}，实际为 ${value}`);
  }
  return parsed;
}

function parseOptions(argv) {
  const values = new Map();
  for (const argument of argv) {
    if (!argument.startsWith('--') || !argument.includes('=')) {
      throw new Error(`参数必须使用 --key=value：${argument}`);
    }
    const [key, ...rest] = argument.slice(2).split('=');
    values.set(key, rest.join('='));
  }

  const stages = (values.get('stages') ?? '1,8,24,48,72')
    .split(',')
    .map((value) => parseNumber('stages', value, { min: 1, integer: true }));
  if (stages.length === 0) {
    throw new Error('--stages 至少包含一个并发值');
  }

  const endpoints = (values.get('endpoints') ?? DEFAULT_ENDPOINTS.join(','))
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  if (endpoints.length === 0 || endpoints.some((value) => !value.startsWith('/'))) {
    throw new Error('--endpoints 必须是以 / 开头的逗号分隔路径');
  }

  return {
    target: (values.get('target') ?? 'http://127.0.0.1:9080').replace(/\/$/, ''),
    stages,
    endpoints,
    warmupSeconds: parseNumber('warmup', values.get('warmup') ?? '30', { min: 0 }),
    durationSeconds: parseNumber('duration', values.get('duration') ?? '60', { min: 1 }),
    restSeconds: parseNumber('rest', values.get('rest') ?? '5', { min: 0 }),
    requestTimeoutMs: parseNumber('timeout-ms', values.get('timeout-ms') ?? '5000', { min: 100, integer: true }),
    minFreeMemoryGiB: parseNumber('min-free-gib', values.get('min-free-gib') ?? '2', { min: 0.25 }),
    maxCpuPercent: parseNumber('max-cpu', values.get('max-cpu') ?? '85', { min: 1 }),
    cpuBreachSeconds: parseNumber('cpu-breach-seconds', values.get('cpu-breach-seconds') ?? '10', { min: 1, integer: true }),
    maxErrorRate: parseNumber('max-error-rate', values.get('max-error-rate') ?? '0.01', { min: 0 }),
    label: (values.get('label') ?? 'capacity').replace(/[^a-zA-Z0-9_-]+/g, '-'),
    outputDirectory: path.resolve(values.get('output-dir') ?? 'deploy/m10/evidence'),
  };
}

function cpuTimes() {
  return os.cpus().reduce(
    (aggregate, cpu) => {
      const total = Object.values(cpu.times).reduce((sum, value) => sum + value, 0);
      aggregate.idle += cpu.times.idle;
      aggregate.total += total;
      return aggregate;
    },
    { idle: 0, total: 0 },
  );
}

function cpuPercent(previous, current) {
  const total = current.total - previous.total;
  const idle = current.idle - previous.idle;
  return total <= 0 ? 0 : ((total - idle) / total) * 100;
}

function createMetrics() {
  return {
    total: 0,
    ok: 0,
    unexpected: 0,
    retryChains: 0,
    latencySamples: [],
    status: Object.create(null),
    endpoints: Object.create(null),
    upstreams: Object.create(null),
    errors: Object.create(null),
    endpointErrors: Object.create(null),
  };
}

function increment(record, key) {
  record[key] = (record[key] ?? 0) + 1;
}

function sampleLatency(metrics, latencyMs) {
  if (metrics.latencySamples.length < MAX_LATENCY_SAMPLES) {
    metrics.latencySamples.push(latencyMs);
    return;
  }
  const replacement = Math.floor(Math.random() * metrics.total);
  if (replacement < MAX_LATENCY_SAMPLES) {
    metrics.latencySamples[replacement] = latencyMs;
  }
}

function percentile(sorted, fraction) {
  if (sorted.length === 0) return null;
  return sorted[Math.min(sorted.length - 1, Math.ceil(sorted.length * fraction) - 1)];
}

function round(value, digits = 2) {
  if (value == null) return null;
  const scale = 10 ** digits;
  return Math.round(value * scale) / scale;
}

function performRequest(uri, timeoutMs) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(uri);
    const transport = parsed.protocol === 'https:' ? https : http;
    const agent = parsed.protocol === 'https:' ? httpsAgent : httpAgent;
    const request = transport.request(parsed, {
      method: 'GET',
      agent,
      headers: { Accept: 'application/json', Connection: 'keep-alive' },
    }, (response) => {
      response.on('data', () => {});
      response.on('end', () => resolve({ status: response.statusCode ?? 0, headers: response.headers }));
      response.on('error', reject);
    });
    request.setTimeout(timeoutMs, () => {
      const error = new Error(`request timed out after ${timeoutMs}ms`);
      error.code = 'REQUEST_TIMEOUT';
      request.destroy(error);
    });
    request.on('error', reject);
    request.end();
  });
}

async function requestOnce(options, endpoint, metrics, shouldRecord) {
  const started = performance.now();
  try {
    const response = await performRequest(`${options.target}${endpoint}`, options.requestTimeoutMs);
    if (!shouldRecord) return;

    const elapsed = performance.now() - started;
    metrics.total += 1;
    sampleLatency(metrics, elapsed);
    increment(metrics.status, String(response.status));
    increment(metrics.endpoints, endpoint);

    const upstreamHeader = response.headers['x-minispring-upstream'];
    const upstreamChain = Array.isArray(upstreamHeader) ? upstreamHeader.join(',') : (upstreamHeader ?? 'missing');
    const upstreams = upstreamChain.split(',').map((value) => value.trim()).filter(Boolean);
    const finalUpstream = upstreams.at(-1) ?? 'missing';
    increment(metrics.upstreams, finalUpstream);
    if (upstreams.length > 1) metrics.retryChains += 1;

    if (response.status >= 200 && response.status < 300) {
      metrics.ok += 1;
    } else {
      metrics.unexpected += 1;
      increment(metrics.errors, `HTTP ${response.status}`);
    }
  } catch (error) {
    if (!shouldRecord) return;
    const elapsed = performance.now() - started;
    metrics.total += 1;
    metrics.unexpected += 1;
    sampleLatency(metrics, elapsed);
    increment(metrics.status, 'NETWORK');
    increment(metrics.endpoints, endpoint);
    const directCode = error?.code ? `:${error.code}` : '';
    const causeCode = error?.cause?.code ? `:${error.cause.code}` : '';
    const causeMessage = error?.cause?.message ? `:${error.cause.message}` : '';
    const message = `${String(error?.message ?? error)}${directCode}${causeCode}${causeMessage}`;
    increment(metrics.errors, message);
    increment(metrics.endpointErrors, `${endpoint} -> ${message}`);
  }
}

async function runWorkers(options, concurrency, seconds, metrics, shouldRecord, abortState) {
  const deadline = performance.now() + seconds * 1000;
  let sequence = 0;
  const workers = Array.from({ length: concurrency }, async () => {
    while (performance.now() < deadline && abortState.reason == null) {
      const endpoint = options.endpoints[sequence % options.endpoints.length];
      sequence += 1;
      await requestOnce(options, endpoint, metrics, shouldRecord);
    }
  });
  await Promise.all(workers);
}

function startResourceMonitor(options, metrics, abortState) {
  const samples = [];
  let previous = cpuTimes();
  let consecutiveHighCpu = 0;
  const capture = () => {
    const current = cpuTimes();
    const systemCpuPercent = cpuPercent(previous, current);
    previous = current;
    const freeMemoryGiB = os.freemem() / GIB;
    const sample = {
      at: new Date().toISOString(),
      cpuPercent: round(systemCpuPercent, 1),
      freeMemoryGiB: round(freeMemoryGiB, 3),
      requestTotal: metrics.total,
      unexpected: metrics.unexpected,
    };
    samples.push(sample);

    consecutiveHighCpu = systemCpuPercent >= options.maxCpuPercent ? consecutiveHighCpu + 1 : 0;
    if (freeMemoryGiB < options.minFreeMemoryGiB) {
      abortState.reason = `可用内存 ${round(freeMemoryGiB, 2)} GiB 低于 ${options.minFreeMemoryGiB} GiB`;
    } else if (consecutiveHighCpu >= options.cpuBreachSeconds) {
      abortState.reason = `CPU >= ${options.maxCpuPercent}% 已持续 ${options.cpuBreachSeconds}s`;
    } else if (metrics.total >= 100 && metrics.unexpected / metrics.total > options.maxErrorRate) {
      abortState.reason = `非预期错误率 ${round((metrics.unexpected / metrics.total) * 100, 3)}% 超过 ${options.maxErrorRate * 100}%`;
    }
  };
  capture();
  const timer = setInterval(capture, 1000);
  timer.unref();
  return {
    samples,
    stop() {
      clearInterval(timer);
      capture();
    },
  };
}

function finalizeStage(concurrency, options, metrics, resourceSamples, startedAt, elapsedSeconds, abortReason) {
  const sorted = [...metrics.latencySamples].sort((a, b) => a - b);
  const cpuValues = resourceSamples.map((sample) => sample.cpuPercent);
  const memoryValues = resourceSamples.map((sample) => sample.freeMemoryGiB);
  return {
    concurrency,
    startedAt,
    finishedAt: new Date().toISOString(),
    requestedSteadySeconds: options.durationSeconds,
    actualSteadySeconds: round(elapsedSeconds, 3),
    aborted: abortReason != null,
    abortReason,
    requests: metrics.total,
    ok: metrics.ok,
    unexpected: metrics.unexpected,
    errorRatePercent: metrics.total === 0 ? 0 : round((metrics.unexpected / metrics.total) * 100, 4),
    throughputRps: round(metrics.total / Math.max(elapsedSeconds, 0.001), 2),
    latencyMs: {
      sampleCount: sorted.length,
      sampling: metrics.total > MAX_LATENCY_SAMPLES ? `reservoir-${MAX_LATENCY_SAMPLES}` : 'exact',
      p50: round(percentile(sorted, 0.50)),
      p95: round(percentile(sorted, 0.95)),
      p99: round(percentile(sorted, 0.99)),
      max: round(sorted.at(-1) ?? null),
    },
    status: metrics.status,
    endpoints: metrics.endpoints,
    upstreams: metrics.upstreams,
    retryChains: metrics.retryChains,
    errors: metrics.errors,
    endpointErrors: metrics.endpointErrors,
    resources: {
      sampleCount: resourceSamples.length,
      cpuAveragePercent: cpuValues.length ? round(cpuValues.reduce((sum, value) => sum + value, 0) / cpuValues.length, 1) : null,
      cpuMaxPercent: cpuValues.length ? Math.max(...cpuValues) : null,
      freeMemoryMinGiB: memoryValues.length ? Math.min(...memoryValues) : null,
      samples: resourceSamples,
    },
  };
}

function markdownReport(report) {
  const lines = [
    `# M10 有界容量报告 · ${report.label}`,
    '',
    `- 开始：${report.startedAt}`,
    `- 目标：${report.options.target}`,
    `- 预热 / 稳态：${report.options.warmupSeconds}s / ${report.options.durationSeconds}s`,
    `- 止损线：可用内存 ≥ ${report.options.minFreeMemoryGiB} GiB；CPU < ${report.options.maxCpuPercent}%（连续 ${report.options.cpuBreachSeconds}s）；错误率 ≤ ${report.options.maxErrorRate * 100}%`,
    '',
    '| 并发 | 请求 | RPS | p50 | p95 | p99 | 错误率 | 最低空闲内存 | 峰值 CPU | 结论 |',
    '| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | :--- |',
  ];
  for (const stage of report.stages) {
    lines.push(`| ${stage.concurrency} | ${stage.requests} | ${stage.throughputRps} | ${stage.latencyMs.p50 ?? '-'} ms | ${stage.latencyMs.p95 ?? '-'} ms | ${stage.latencyMs.p99 ?? '-'} ms | ${stage.errorRatePercent}% | ${stage.resources.freeMemoryMinGiB ?? '-'} GiB | ${stage.resources.cpuMaxPercent ?? '-'}% | ${stage.aborted ? `停止：${stage.abortReason}` : '通过'} |`);
  }
  lines.push('', '## 实例命中与重试', '');
  for (const stage of report.stages) {
    lines.push(`- 并发 ${stage.concurrency}：${JSON.stringify(stage.upstreams)}；重试链 ${stage.retryChains}`);
  }
  lines.push('', '> 本报告只统计只读端点。事务提交与刻意回滚由独立一致性演练取证。', '');
  return lines.join('\n');
}

async function main() {
  const options = parseOptions(process.argv.slice(2));
  const { outputDirectory: _privateOutputDirectory, ...publicOptions } = options;
  const report = {
    schemaVersion: 1,
    label: options.label,
    startedAt: new Date().toISOString(),
    host: { platform: os.platform(), logicalCpu: os.cpus().length, totalMemoryGiB: round(os.totalmem() / GIB, 2) },
    options: publicOptions,
    stages: [],
  };

  console.log(`[M10] target=${options.target} stages=${options.stages.join('→')} guard=${options.minFreeMemoryGiB}GiB/${options.maxCpuPercent}%CPU/${options.maxErrorRate * 100}%errors`);
  for (let index = 0; index < options.stages.length; index += 1) {
    const concurrency = options.stages[index];
    const metrics = createMetrics();
    const abortState = { reason: null };
    const monitor = startResourceMonitor(options, metrics, abortState);
    console.log(`[M10] C=${concurrency} warmup ${options.warmupSeconds}s`);
    await runWorkers(options, concurrency, options.warmupSeconds, metrics, false, abortState);

    const startedAt = new Date().toISOString();
    const steadyStarted = performance.now();
    if (abortState.reason == null) {
      console.log(`[M10] C=${concurrency} steady ${options.durationSeconds}s`);
      await runWorkers(options, concurrency, options.durationSeconds, metrics, true, abortState);
    }
    const elapsedSeconds = (performance.now() - steadyStarted) / 1000;
    monitor.stop();

    const stage = finalizeStage(concurrency, options, metrics, monitor.samples, startedAt, elapsedSeconds, abortState.reason);
    report.stages.push(stage);
    console.log(`[M10] C=${concurrency} requests=${stage.requests} rps=${stage.throughputRps} p95=${stage.latencyMs.p95}ms errors=${stage.errorRatePercent}% freeMin=${stage.resources.freeMemoryMinGiB}GiB cpuMax=${stage.resources.cpuMaxPercent}%${stage.aborted ? ` ABORT=${stage.abortReason}` : ''}`);
    if (stage.aborted || stage.errorRatePercent > options.maxErrorRate * 100) break;
    if (index < options.stages.length - 1 && options.restSeconds > 0) {
      await new Promise((resolve) => setTimeout(resolve, options.restSeconds * 1000));
    }
  }

  report.finishedAt = new Date().toISOString();
  report.completedAllStages = report.stages.length === options.stages.length && report.stages.every((stage) => !stage.aborted);
  await mkdir(options.outputDirectory, { recursive: true });
  const stamp = report.startedAt.replace(/[:.]/g, '-');
  const base = path.join(options.outputDirectory, `${stamp}-${options.label}`);
  await writeFile(`${base}.json`, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  await writeFile(`${base}.md`, markdownReport(report), 'utf8');
  console.log(`[M10] evidence=${path.relative(process.cwd(), base)}.{json,md}`);
  if (!report.completedAllStages) process.exitCode = 2;
}

main()
  .catch((error) => {
    console.error(`[M10] fatal: ${error.stack ?? error}`);
    process.exitCode = 1;
  })
  .finally(() => {
    httpAgent.destroy();
    httpsAgent.destroy();
  });
