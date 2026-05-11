import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { writeUser } from '@/hooks/useAuth'

const AUTH_METHODS = [
  { id: 'google',    label: 'Continue with Google',    icon: 'chrome' },
  { id: 'github',    label: 'Continue with GitHub',    icon: 'github' },
  { id: 'microsoft', label: 'Continue with Microsoft', icon: 'square' },
  { id: 'apple',     label: 'Continue with Apple',     icon: 'apple' },
  { id: 'sso',       label: 'Continue with SAML SSO',  icon: 'building-2' },
]

function AuthShell({ children, side }: { children: React.ReactNode; side: React.ReactNode }) {
  return (
    <div className="min-h-[calc(100vh-3.5rem)] grid lg:grid-cols-[1fr_520px]">
      <div className="hidden lg:flex relative overflow-hidden bg-ink-950 text-white">
        <div className="absolute inset-0 grid-bg opacity-30" />
        <div className="absolute inset-0" style={{ background: 'radial-gradient(ellipse 60% 50% at 30% 30%, rgba(13,143,99,0.25), transparent 60%)' }} />
        <div className="relative p-12 flex flex-col w-full">
          <a href="/" className="inline-flex items-center gap-2.5 text-white">
            <svg viewBox="0 0 28 28" className="w-6 h-6" fill="none">
              <circle cx="6" cy="7" r="2.4" fill="currentColor" />
              <circle cx="22" cy="7" r="2.4" fill="currentColor" />
              <circle cx="14" cy="14" r="2.4" stroke="currentColor" strokeWidth="1.6" />
              <circle cx="6" cy="21" r="2.4" stroke="currentColor" strokeWidth="1.6" />
              <circle cx="22" cy="21" r="2.4" stroke="currentColor" strokeWidth="1.6" />
              <path d="M6 9.4 L13 12.4 M22 9.4 L15 12.4 M13 15.6 L6 18.6 M15 15.6 L22 18.6" stroke="currentColor" strokeWidth="1.4" />
            </svg>
            <span className="font-medium tracking-tight text-[15px]">TraceGraph</span>
          </a>
          <div className="flex-1 flex flex-col justify-center max-w-md">{side}</div>
          <div className="mono text-[11px] text-white/40 flex items-center gap-4">
            <span>SOC 2 Type II</span><span>·</span><span>ISO 27001</span><span>·</span><span>GDPR</span>
          </div>
        </div>
      </div>
      <div className="flex items-center justify-center px-6 py-14 bg-white dark:bg-ink-950">
        <div className="w-full max-w-[400px]">{children}</div>
      </div>
    </div>
  )
}

function Field({
  label, type = 'text', value, onChange, hint, error, right, autoComplete,
}: {
  label: string; type?: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void
  hint?: string | null; error?: string | null; right?: React.ReactNode; autoComplete?: string
}) {
  return (
    <label className="block mb-3.5">
      <div className="flex items-center justify-between mb-1.5">
        <span className="text-[12.5px] font-medium text-ink-700 dark:text-ink-300">{label}</span>
        {right}
      </div>
      <input
        type={type} value={value} onChange={onChange} autoComplete={autoComplete}
        className={`w-full h-11 px-3.5 rounded-lg border bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none transition-colors ${
          error ? 'border-rose-400 dark:border-rose-700' : 'border-ink-200 dark:border-ink-800 focus:border-ink-950 dark:focus:border-white'
        }`}
      />
      {(hint || error) && (
        <div className={`mt-1.5 text-[11.5px] ${error ? 'text-rose-600 dark:text-rose-400' : 'text-ink-500'}`}>{error ?? hint}</div>
      )}
    </label>
  )
}

function Divider({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3 my-5">
      <div className="flex-1 h-px bg-ink-200 dark:bg-ink-800" />
      <span className="mono text-[10.5px] uppercase tracking-[0.16em] text-ink-500">{children}</span>
      <div className="flex-1 h-px bg-ink-200 dark:bg-ink-800" />
    </div>
  )
}

export function SignIn() {
  const navigate = useNavigate()
  const [method, setMethod] = useState<'password' | 'magic' | 'passkey'>('password')
  const [email, setEmail] = useState('')
  const [pw, setPw] = useState('')
  const [remember, setRemember] = useState(true)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const doSignIn = (e?: React.FormEvent) => {
    e?.preventDefault()
    setErr(null)
    if (!email.includes('@')) return setErr('Enter a valid email')
    if (method === 'password' && pw.length < 8) return setErr('Password must be at least 8 characters')
    setBusy(true)
    setTimeout(() => {
      writeUser({
        email,
        name: email.split('@')[0].replace(/\./g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()),
        plan: 'Pro',
        joined: '2026-02-14',
        provider: method,
        mfa: true,
        avatarColor: '#0d8f63',
      })
      navigate('/profile')
    }, 600)
  }

  return (
    <AuthShell
      side={
        <>
          <h2 className="font-display text-[44px] font-medium tracking-tight mb-4 leading-[1.05]">Sign in to TraceGraph.</h2>
          <p className="text-white/70 text-[15px] leading-relaxed">Resume any saved execution, fork from any step, and inspect token usage across every node.</p>
          <ul className="mt-8 space-y-2 text-[13.5px] text-white/70">
            {[
              'Trace storage encrypted at rest (AES-256-GCM)',
              'Sessions bound to IP & device fingerprint',
              'Hardware-key MFA & WebAuthn passkeys',
              'Audit log for every privileged action',
            ].map((t) => (
              <li key={t} className="flex items-center gap-2">
                <Icon name="check" size={14} className="text-emerald-400 shrink-0" />
                {t}
              </li>
            ))}
          </ul>
        </>
      }
    >
      <h1 className="text-[24px] font-medium tracking-tight text-ink-950 dark:text-white">Welcome back</h1>
      <p className="mt-1.5 text-[13.5px] text-ink-500">New here? <a className="text-ink-950 dark:text-white underline underline-offset-2" href="/signup">Create an account</a></p>

      <div className="mt-7 space-y-2.5">
        {AUTH_METHODS.map((m) => (
          <button key={m.id} onClick={() => doSignIn()}
            className="w-full h-11 rounded-lg border hairline bg-white dark:bg-ink-950 hover:bg-ink-50 dark:hover:bg-ink-900 flex items-center justify-center gap-3 text-[14px] text-ink-950 dark:text-white transition-colors">
            <Icon name={m.icon} size={16} />
            <span>{m.label}</span>
          </button>
        ))}
      </div>

      <Divider>or sign in with</Divider>

      <div className="flex gap-1.5 mb-5 p-0.5 bg-ink-100 dark:bg-ink-900 rounded-lg">
        {([['password', 'Password', 'lock'], ['magic', 'Magic link', 'mail'], ['passkey', 'Passkey', 'fingerprint']] as const).map(([id, label, icon]) => (
          <button key={id} onClick={() => setMethod(id)}
            className={`flex-1 h-8 rounded-md text-[12.5px] inline-flex items-center justify-center gap-1.5 transition-colors ${
              method === id ? 'bg-white dark:bg-ink-950 text-ink-950 dark:text-white shadow-sm' : 'text-ink-600 dark:text-ink-400'
            }`}>
            <Icon name={icon} size={13} />{label}
          </button>
        ))}
      </div>

      <form onSubmit={doSignIn}>
        <Field label="Email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email"
          hint={method === 'magic' ? 'We will send a one-time link valid for 10 minutes.' : null} />
        {method === 'password' && (
          <Field label="Password" type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="current-password" error={err}
            right={<a href="/forgot" className="text-[11.5px] text-ink-500 hover:text-ink-950 dark:hover:text-white">Forgot?</a>} />
        )}
        {method === 'passkey' && (
          <div className="mb-4 p-3 rounded-lg border hairline bg-ink-50 dark:bg-ink-900/60 text-[12.5px] text-ink-600 dark:text-ink-400">
            <Icon name="fingerprint" size={14} className="inline mr-1.5 align-[-2px]" />
            Your browser will prompt for Touch ID, Face ID, or a security key.
          </div>
        )}
        {err && method !== 'password' && <div className="mb-3 text-[12px] text-rose-600 dark:text-rose-400">{err}</div>}
        {method === 'password' && (
          <label className="flex items-center gap-2 mb-5 text-[12.5px] text-ink-600 dark:text-ink-400">
            <input type="checkbox" checked={remember} onChange={(e) => setRemember(e.target.checked)} className="rounded" />
            Keep me signed in for 14 days
          </label>
        )}
        <Button as="button" size="lg" variant="primary" className="w-full justify-center">
          {busy ? 'Signing in…' : method === 'magic' ? 'Send magic link' : method === 'passkey' ? 'Use passkey' : 'Sign in'}
        </Button>
      </form>

      <p className="mt-6 text-center text-[11.5px] text-ink-500">
        Protected by reCAPTCHA Enterprise ·{' '}
        <a href="#" className="underline">Privacy</a> ·{' '}
        <a href="#" className="underline">Terms</a>
      </p>
    </AuthShell>
  )
}
