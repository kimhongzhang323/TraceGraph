/**
 * Environment-aware API client.
 *
 * In dev (Vite proxy): requests go to /tracegraph/* → proxied to localhost:8082.
 * In production on Vercel with a separate backend: set VITE_API_BASE_URL to the
 * deployed Spring Boot origin (e.g. https://api.myapp.com).
 * In production served by Spring Boot itself: leave VITE_API_BASE_URL empty —
 * relative URLs resolve to the same origin.
 */

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = `${BASE}${path}`
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...init?.headers },
    ...init,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new ApiError(res.status, text)
  }
  const ct = res.headers.get('content-type') ?? ''
  if (ct.includes('application/json')) return res.json() as Promise<T>
  return res.text() as unknown as Promise<T>
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface PageResult<T> {
  items: T[]
  total: number
}

export const api = {
  traces: {
    list: (limit = 20, offset = 0) =>
      request<PageResult<string> | string[]>(`/tracegraph/traces?limit=${limit}&offset=${offset}`),
    get: (id: string) =>
      request<unknown>(`/tracegraph/traces/${id}`),
    delete: (id: string) =>
      request<void>(`/tracegraph/traces/${id}`, { method: 'DELETE' }),
    diff: (a: string, b: string) =>
      request<unknown>(`/tracegraph/traces/${a}/diff/${b}`),
    replay: (id: string, step = -1) =>
      request<{ executionId: string }>(`/tracegraph/traces/${id}/replay?step=${step}`, { method: 'POST' }),
    stream: () => new EventSource(`${BASE}/tracegraph/traces/stream`),
  },
  graph: {
    mermaid: () => request<string>('/tracegraph/ui/graph'),
    complexity: () => request<import('@/types').GraphComplexity>('/tracegraph/ui/complexity'),
  },
}
