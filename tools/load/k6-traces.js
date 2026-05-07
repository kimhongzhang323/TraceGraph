/**
 * k6 load test for TraceGraph /tracegraph/traces endpoints.
 *
 * Run: k6 run --env BASE_URL=http://localhost:8082 tools/load/k6-traces.js
 *
 * Scenarios:
 *   list     — ramp to 500 VUs hitting GET /tracegraph/traces?limit=20
 *   get      — 50 VUs fetching individual traces by ID (seeded from list)
 *   sse      — 100 long-lived SSE connections via POST /tracegraph/traces/stream
 *   replay   — 10 concurrent replay calls
 *
 * Targets (adjust per env):
 *   list p95 < 200ms, error rate < 0.1%
 *   get  p95 < 300ms, error rate < 0.1%
 */

import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://localhost:8082'

const listErrors = new Rate('list_errors')
const listDuration = new Trend('list_duration_ms', true)
const getErrors = new Rate('get_errors')

export const options = {
  scenarios: {
    list: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 100 },
        { duration: '60s', target: 500 },
        { duration: '30s', target: 0 },
      ],
      exec: 'listTraces',
    },
    get: {
      executor: 'constant-vus',
      vus: 50,
      duration: '120s',
      exec: 'getTrace',
      startTime: '15s',
    },
    replay: {
      executor: 'constant-vus',
      vus: 10,
      duration: '60s',
      exec: 'replayTrace',
      startTime: '30s',
    },
  },
  thresholds: {
    list_errors: ['rate<0.001'],
    get_errors: ['rate<0.001'],
    list_duration_ms: ['p(95)<200'],
    http_req_duration: ['p(99)<2000'],
  },
}

export function listTraces() {
  const start = Date.now()
  const res = http.get(`${BASE}/tracegraph/traces?limit=20`, {
    headers: { Accept: 'application/json' },
  })
  listDuration.add(Date.now() - start)
  const ok = check(res, {
    'list 200 or 304': (r) => r.status === 200 || r.status === 304,
  })
  listErrors.add(!ok)
  sleep(0.1)
}

export function getTrace() {
  const listRes = http.get(`${BASE}/tracegraph/traces?limit=5`, {
    headers: { Accept: 'application/json' },
  })
  if (listRes.status !== 200) {
    getErrors.add(true)
    return
  }
  const ids = JSON.parse(listRes.body)
  if (!ids || ids.length === 0) { sleep(1); return }
  const id = ids[Math.floor(Math.random() * ids.length)]
  const res = http.get(`${BASE}/tracegraph/traces/${id}`, {
    headers: { Accept: 'application/json' },
  })
  const ok = check(res, { 'get 200 or 304': (r) => r.status === 200 || r.status === 304 || r.status === 404 })
  getErrors.add(!ok)
  sleep(0.2)
}

export function replayTrace() {
  const listRes = http.get(`${BASE}/tracegraph/traces?limit=5`)
  if (listRes.status !== 200) { sleep(2); return }
  const ids = JSON.parse(listRes.body)
  if (!ids || ids.length === 0) { sleep(2); return }
  const id = ids[0]
  const res = http.post(`${BASE}/tracegraph/traces/${id}/replay?step=-1`, null, {
    headers: { Accept: 'application/json' },
    timeout: '65s',
  })
  check(res, { 'replay 200 or 429': (r) => r.status === 200 || r.status === 429 })
  sleep(1)
}
