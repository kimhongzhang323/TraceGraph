import { useEffect, useState, useCallback } from 'react'
import { api, tokenStore, ApiError } from '@/lib/api'

export interface AuthUser {
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

export interface SignInResult {
  ok: boolean
  error?: string
  magicLinkSent?: boolean
}

export interface SignUpResult {
  ok: boolean
  error?: string
}

const USER_KEY = 'tg-user'

// Persist user profile locally so the UI is instant on reload
function readLocal(): AuthUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

function writeLocal(u: AuthUser | null): void {
  if (u) localStorage.setItem(USER_KEY, JSON.stringify(u))
  else localStorage.removeItem(USER_KEY)
}

function avatarColor(email: string): string {
  const colors = ['#0d8f63', '#3276ff', '#6d4aff', '#ff7a59', '#e11d48']
  let h = 0
  for (let i = 0; i < email.length; i++) h = (h * 31 + email.charCodeAt(i)) & 0xffffffff
  return colors[Math.abs(h) % colors.length]
}

export function writeUser(u: AuthUser | null): void {
  writeLocal(u)
  window.dispatchEvent(new Event('tg-auth-changed'))
}

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(readLocal)
  const [loading, setLoading] = useState(false)

  // Rehydrate from backend if we have a token but want fresh profile data
  useEffect(() => {
    const token = tokenStore.getAccess()
    if (!token) return
    api.auth.me()
      .then((me) => {
        const u: AuthUser = { ...me, avatarColor: avatarColor(me.email) }
        writeLocal(u)
        setUser(u)
      })
      .catch((err: unknown) => {
        // Hard 401 with no refresh token = dead session; clear it so ProtectedRoute
        // kicks the user to /signin instead of letting them through with broken state.
        if (err instanceof ApiError && err.status === 401 && !tokenStore.getRefresh()) {
          tokenStore.clear()
          writeLocal(null)
          setUser(null)
          window.dispatchEvent(new Event('tg-auth-changed'))
        }
        // Network errors or other transient failures: keep the cached user (demo mode
        // or backend temporarily down) — don't log the user out.
      })
  }, [])

  // Cross-tab sync
  useEffect(() => {
    const sync = () => setUser(readLocal())
    window.addEventListener('tg-auth-changed', sync)
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener('tg-auth-changed', sync)
      window.removeEventListener('storage', sync)
    }
  }, [])

  const signIn = useCallback(async (
    method: 'password' | 'magic' | 'passkey' | 'oauth',
    credentials: { email: string; password?: string; provider?: string },
  ): Promise<SignInResult> => {
    setLoading(true)
    try {
      if (method === 'magic') {
        await api.auth.magicLink(credentials.email)
        return { ok: true, magicLinkSent: true }
      }

      let result: Awaited<ReturnType<typeof api.auth.signIn>>

      if (method === 'oauth' && credentials.provider) {
        result = await api.auth.signInOAuth(credentials.provider)
      } else if (method === 'passkey') {
        result = await api.auth.passkey(credentials.email)
      } else {
        result = await api.auth.signIn(credentials.email, credentials.password ?? '')
      }

      tokenStore.set(result.accessToken, result.refreshToken)
      const u: AuthUser = { ...result.user, avatarColor: avatarColor(result.user.email) }
      writeUser(u)
      setUser(u)
      return { ok: true }
    } catch (err) {
      // Fall back to demo mode when backend is not reachable
      if (err instanceof ApiError && err.status === 0 || !(err instanceof ApiError)) {
        const demoUser: AuthUser = {
          email: credentials.email,
          name: credentials.email.split('@')[0].replace(/\./g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
          plan: 'Pro',
          joined: '2026-02-14',
          provider: method,
          mfa: true,
          avatarColor: avatarColor(credentials.email),
        }
        writeUser(demoUser)
        setUser(demoUser)
        return { ok: true }
      }
      const msg = err instanceof ApiError
        ? (err.status === 401 ? 'Incorrect email or password.' : err.message)
        : 'Something went wrong. Try again.'
      return { ok: false, error: msg }
    } finally {
      setLoading(false)
    }
  }, [])

  const signUp = useCallback(async (payload: {
    name: string; email: string; password: string; org?: string; role?: string; marketing?: boolean
  }): Promise<SignUpResult> => {
    setLoading(true)
    try {
      const result = await api.auth.signUp(payload)
      tokenStore.set(result.accessToken, result.refreshToken)
      const u: AuthUser = { ...result.user, avatarColor: avatarColor(result.user.email) }
      writeUser(u)
      setUser(u)
      return { ok: true }
    } catch (err) {
      // Demo fallback
      if (!(err instanceof ApiError) || err.status === 0) {
        const demoUser: AuthUser = {
          email: payload.email,
          name: payload.name,
          org: payload.org,
          role: payload.role,
          plan: 'Free',
          joined: new Date().toISOString().slice(0, 10),
          provider: 'password',
          mfa: false,
          avatarColor: avatarColor(payload.email),
        }
        writeUser(demoUser)
        setUser(demoUser)
        return { ok: true }
      }
      const msg = err instanceof ApiError
        ? (err.status === 409 ? 'An account with that email already exists.' : err.message)
        : 'Something went wrong. Try again.'
      return { ok: false, error: msg }
    } finally {
      setLoading(false)
    }
  }, [])

  const signOut = useCallback(async () => {
    try {
      await api.auth.signOut()
    } catch {
      // Best-effort — always clear local state
      tokenStore.clear()
      writeUser(null)
    }
    setUser(null)
  }, [])

  return { user, loading, signIn, signUp, signOut }
}
