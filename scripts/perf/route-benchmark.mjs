#!/usr/bin/env node
import { performance } from "node:perf_hooks";
import fs from "node:fs";
import path from "node:path";

function parseArgs(argv) {
  const args = {
    baseUrl: "http://localhost:8081",
    path: "/api/route",
    durationSec: 60,
    concurrency: 10,
    targetRps: 0,
    timeoutMs: 8000,
    out: "",
    maxP95Ms: 1500,
    maxErrorRate: 0.02,
    failOnThreshold: false,
    body: {
      startLat: 23.275784,
      startLng: 113.200776,
      endLat: 23.2762,
      endLng: 113.20265,
      mode: "WALK",
    },
  };

  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    const next = argv[i + 1];
    if (key === "--base-url" && next) args.baseUrl = next;
    if (key === "--path" && next) args.path = next;
    if (key === "--duration-sec" && next) args.durationSec = Number(next);
    if (key === "--concurrency" && next) args.concurrency = Number(next);
    if (key === "--target-rps" && next) args.targetRps = Number(next);
    if (key === "--timeout-ms" && next) args.timeoutMs = Number(next);
    if (key === "--out" && next) args.out = next;
    if (key === "--max-p95-ms" && next) args.maxP95Ms = Number(next);
    if (key === "--max-error-rate" && next) args.maxErrorRate = Number(next);
    if (key === "--fail-on-threshold") args.failOnThreshold = true;
  }
  return args;
}

function quantile(values, q) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil(q * sorted.length) - 1));
  return sorted[idx];
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function normalizeBase(baseUrl, path) {
  const left = baseUrl.endsWith("/") ? baseUrl.slice(0, -1) : baseUrl;
  const right = path.startsWith("/") ? path : `/${path}`;
  return `${left}${right}`;
}

async function requestOnce(url, body, timeoutMs) {
  const started = performance.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const resp = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    const elapsed = performance.now() - started;
    return {
      ok: resp.ok,
      status: resp.status,
      elapsedMs: elapsed,
    };
  } catch (err) {
    const elapsed = performance.now() - started;
    return {
      ok: false,
      status: 0,
      elapsedMs: elapsed,
      error: err instanceof Error ? err.message : String(err),
    };
  } finally {
    clearTimeout(timer);
  }
}

async function run() {
  const cfg = parseArgs(process.argv.slice(2));
  const url = normalizeBase(cfg.baseUrl, cfg.path);
  const stopAt = Date.now() + cfg.durationSec * 1000;
  const perWorkerIntervalMs =
    cfg.targetRps > 0 ? Math.max(0, (cfg.concurrency * 1000) / cfg.targetRps) : 0;

  const latencies = [];
  const statusCount = new Map();
  const errors = new Map();
  let total = 0;
  let success = 0;
  let failure = 0;

  async function workerLoop() {
    while (Date.now() < stopAt) {
      const tick = performance.now();
      const result = await requestOnce(url, cfg.body, cfg.timeoutMs);

      total += 1;
      latencies.push(result.elapsedMs);

      const statusKey = String(result.status);
      statusCount.set(statusKey, (statusCount.get(statusKey) || 0) + 1);
      if (result.ok) {
        success += 1;
      } else {
        failure += 1;
        if (result.error) {
          errors.set(result.error, (errors.get(result.error) || 0) + 1);
        }
      }

      if (perWorkerIntervalMs > 0) {
        const spent = performance.now() - tick;
        const rest = perWorkerIntervalMs - spent;
        if (rest > 0) {
          await sleep(rest);
        }
      }
    }
  }

  const startedAtIso = new Date().toISOString();
  const started = performance.now();
  await Promise.all(Array.from({ length: cfg.concurrency }, () => workerLoop()));
  const elapsedSec = Math.max((performance.now() - started) / 1000, 0.001);
  const finishedAtIso = new Date().toISOString();

  const p50 = quantile(latencies, 0.5);
  const p95 = quantile(latencies, 0.95);
  const p99 = quantile(latencies, 0.99);
  const errorRate = total > 0 ? failure / total : 0;
  const rps = total / elapsedSec;

  const report = {
    startedAt: startedAtIso,
    finishedAt: finishedAtIso,
    config: cfg,
    totals: {
      requests: total,
      success,
      failure,
      errorRate,
      throughputRps: rps,
      durationSec: elapsedSec,
    },
    latencyMs: {
      p50,
      p95,
      p99,
      max: latencies.length ? Math.max(...latencies) : 0,
      min: latencies.length ? Math.min(...latencies) : 0,
    },
    statusCount: Object.fromEntries(statusCount.entries()),
    topErrors: Object.fromEntries(
      [...errors.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10),
    ),
  };

  if (cfg.out) {
    fs.mkdirSync(path.dirname(cfg.out), { recursive: true });
    fs.writeFileSync(cfg.out, JSON.stringify(report, null, 2), "utf8");
  }

  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);

  if (cfg.failOnThreshold) {
    const badP95 = p95 > cfg.maxP95Ms;
    const badErr = errorRate > cfg.maxErrorRate;
    if (badP95 || badErr) {
      process.stderr.write(
        `[route-benchmark] threshold failed: p95=${p95.toFixed(2)}ms (max=${cfg.maxP95Ms}), errorRate=${errorRate.toFixed(4)} (max=${cfg.maxErrorRate})\n`,
      );
      process.exit(2);
    }
  }
}

run().catch((err) => {
  process.stderr.write(`[route-benchmark] fatal: ${err instanceof Error ? err.stack : String(err)}\n`);
  process.exit(1);
});
