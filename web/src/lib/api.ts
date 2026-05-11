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

const REQUEST_TIMEOUT_MS = 10_000

const TOKEN_KEY = 'tg-token'
const REFRESH_KEY = 'tg-refresh'

export const tokenStore = {
  getAccess: (): string | null => localStorage.getItem(TOKEN_KEY),
  getRefresh: (): string | null => localStorage.getItem(REFRESH_KEY),
  set: (access: string, refresh: string) => {
    localStorage.setItem(TOKEN_KEY, access)
    localStorage.setItem(REFRESH_KEY, refresh)
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
  },
}

// Deduplicates in-flight GET requests by URL — prevents concurrent re-renders
// from fanning out identical fetches.
const inflight = new Map<string, Promise<unknown>>()

let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) return refreshPromise
  refreshPromise = (async () => {
    const refresh = tokenStore.getRefresh()
    if (!refresh) throw new ApiError(401, 'No refresh token')
    const res = await fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: refresh }),
    })
    if (!res.ok) {
      tokenStore.clear()
      window.dispatchEvent(new Event('tg-auth-changed'))
      throw new ApiError(401, 'Session expired')
    }
    const data = await res.json() as { accessToken: string; refreshToken: string }
    tokenStore.set(data.accessToken, data.refreshToken)
    return data.accessToken
  })().finally(() => { refreshPromise = null })
  return refreshPromise
}

async function request<T>(path: string, init?: RequestInit, retry = true): Promise<T> {
  const url = `${BASE}${path}`
  const method = (init?.method ?? 'GET').toUpperCase()

  if (method === 'GET' && inflight.has(url)) {
    return inflight.get(url) as Promise<T>
  }

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  const token = tokenStore.getAccess()
  const authHeader: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}

  const promise = fetch(url, {
    headers: { 'Content-Type': 'application/json', ...authHeader, ...init?.headers },
    signal: controller.signal,
    ...init,
  })
    .then(async (res) => {
      // Transparent token refresh on 401
      if (res.status === 401 && retry) {
        clearTimeout(timer)
        if (method === 'GET') inflight.delete(url)
        const newToken = await refreshAccessToken()
        return request<T>(path, {
          ...init,
          headers: { ...init?.headers, Authorization: `Bearer ${newToken}` },
        }, false)
      }
      if (!res.ok) {
        const text = await res.text().catch(() => res.statusText)
        throw new ApiError(res.status, text)
      }
      const ct = res.headers.get('content-type') ?? ''
      if (ct.includes('application/json')) return res.json() as T
      return res.text() as unknown as T
    })
    .finally(() => {
      clearTimeout(timer)
      if (method === 'GET') inflight.delete(url)
    })

  if (method === 'GET') inflight.set(url, promise)
  return promise
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

export interface AuthTokens {
  accessToken: string
  refreshToken: string
}

export interface MeResponse {
  email: string
  name: string
  org?: string
  role?: string
  plan: 'Free' | 'Pro' | 'Enterprise'
  joined: string
  provider: string
  mfa: boolean
  avatarColor: string
}

export interface UsageStats {
  totalRuns: number
  replayForks: number
  failureRate: number   // 0–100
  tokensUsed: number
  runsWeekDelta: number
  forksWeekDelta: number
  failureRateDelta: number
  p50TokensPerCall: number
}

export interface AuditEntry {
  id: string
  what: string
  ip: string
  when: string         // ISO timestamp
  whenRelative: string // "12m ago"
}

export interface ApiKey {
  id: string
  name: string
  prefix: string
  scope: string
  createdAt: string
  lastUsedAt: string | null
  lastUsedRelative: string | null
}

export interface Session {
  id: string
  device: string
  browser: string
  location: string
  ip: string
  lastActiveAt: string
  lastActiveRelative: string
  current: boolean
}

export interface BillingInfo {
  plan: 'Free' | 'Pro' | 'Enterprise'
  traceEventsUsed: number
  traceEventsLimit: number
  replayForksUnlimited: boolean
  nextBillingDate: string | null
  amount: number | null
}

export interface Prefs {
  emailDigest: boolean
  failureAlerts: boolean
  betaFeatures: boolean
}

export const api = {
  profile: {
    stats:        () => request<UsageStats>('/auth/stats'),
    audit:        () => request<AuditEntry[]>('/auth/audit'),
    updateMe:     (patch: Partial<Pick<MeResponse, 'name' | 'org' | 'role'>>) =>
                    request<MeResponse>('/auth/me', { method: 'PATCH', body: JSON.stringify(patch) }),
    keys:         () => request<ApiKey[]>('/auth/keys'),
    createKey:    (name: string, scope: string) =>
                    request<ApiKey & { secret: string }>('/auth/keys', { method: 'POST', body: JSON.stringify({ name, scope }) }),
    revokeKey:    (id: string) => request<void>(`/auth/keys/${id}`, { method: 'DELETE' }),
    sessions:     () => request<Session[]>('/auth/sessions'),
    revokeSession:(id: string) => request<void>(`/auth/sessions/${id}`, { method: 'DELETE' }),
    revokeOthers: () => request<void>('/auth/sessions/others', { method: 'DELETE' }),
    billing:      () => request<BillingInfo>('/auth/billing'),
    prefs:        () => request<Prefs>('/auth/prefs'),
    savePrefs:    (prefs: Prefs) =>
                    request<Prefs>('/auth/prefs', { method: 'PUT', body: JSON.stringify(prefs) }),
    changePassword:(current: string, next: string) =>
                    request<void>('/auth/password', { method: 'POST', body: JSON.stringify({ current, next }) }),
    deleteAccount: () => request<void>('/auth/account', { method: 'DELETE' }),
  },
  auth: {
    signIn: (email: string, password: string) =>
      request<AuthTokens & { user: MeResponse }>('/auth/signin', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      }),
    signInOAuth: (provider: string) =>
      request<AuthTokens & { user: MeResponse }>('/auth/oauth', {
        method: 'POST',
        body: JSON.stringify({ provider }),
      }),
    magicLink: (email: string) =>
      request<void>('/auth/magic-link', {
        method: 'POST',
        body: JSON.stringify({ email }),
      }),
    passkey: (email: string) =>
      request<AuthTokens & { user: MeResponse }>('/auth/passkey', {
        method: 'POST',
        body: JSON.stringify({ email }),
      }),
    signUp: (payload: {
      name: string; email: string; password: string
      org?: string; role?: string; marketing?: boolean
    }) =>
      request<AuthTokens & { user: MeResponse }>('/auth/signup', {
        method: 'POST',
        body: JSON.stringify(payload),
      }),
    forgotPassword: (email: string) =>
      request<void>('/auth/forgot-password', {
        method: 'POST',
        body: JSON.stringify({ email }),
      }),
    resetPassword: (token: string, password: string) =>
      request<void>('/auth/reset-password', {
        method: 'POST',
        body: JSON.stringify({ token, password }),
      }),
    signOut: () =>
      request<void>('/auth/signout', { method: 'POST' }).finally(() => {
        tokenStore.clear()
        window.dispatchEvent(new Event('tg-auth-changed'))
      }),
    me: () => request<MeResponse>('/auth/me'),
  },
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
