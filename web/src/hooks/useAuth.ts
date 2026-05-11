import { useEffect, useState } from 'react'

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

const STORAGE_KEY = 'tg-user'

function readUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthUser) : null
  } catch {
    return null
  }
}

export function writeUser(u: AuthUser | null): void {
  if (u) localStorage.setItem(STORAGE_KEY, JSON.stringify(u))
  else localStorage.removeItem(STORAGE_KEY)
  window.dispatchEvent(new Event('tg-auth-changed'))
}

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(readUser)

  useEffect(() => {
    const sync = () => setUser(readUser())
    window.addEventListener('tg-auth-changed', sync)
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener('tg-auth-changed', sync)
      window.removeEventListener('storage', sync)
    }
  }, [])

  const signOut = () => {
    writeUser(null)
  }

  return { user, signOut }
}
