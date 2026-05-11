import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { api, ApiError } from '@/lib/api'

function pwStrength(pw: string): { score: number; label: string; tone: 'err' | 'warn' | 'ok' } {
  if (!pw) return { score: 0, label: '—', tone: 'err' }
  let s = 0
  if (pw.length >= 8) s++
  if (pw.length >= 12) s++
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++
  if (/\d/.test(pw)) s++
  if (/[^A-Za-z0-9]/.test(pw)) s++
  const labels = ['too short', 'weak', 'fair', 'good', 'strong', 'excellent']
  const tones: Array<'err' | 'err' | 'warn' | 'warn' | 'ok' | 'ok'> = ['err', 'err', 'warn', 'warn', 'ok', 'ok']
  return { score: s, label: labels[s], tone: tones[s] }
}

export function ResetPassword() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''

  const [pw, setPw] = useState('')
  const [confirm, setConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const strength = pwStrength(pw)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    if (!token) { setErr('Invalid or missing reset token. Request a new link.'); return }
    if (strength.score < 3) { setErr('Choose a stronger password (min. score: good)'); return }
    if (pw !== confirm) { setErr('Passwords do not match'); return }
    setLoading(true)
    try {
      await api.auth.resetPassword(token, pw)
      setDone(true)
    } catch (error) {
      const msg = error instanceof ApiError
        ? (error.status === 410 ? 'This link has expired. Request a new one.' : error.message)
        : 'Something went wrong. Try again.'
      setErr(msg)
    } finally {
      setLoading(false)
    }
  }

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
          <div className="flex-1 flex flex-col justify-center max-w-md">
            <h2 className="font-display text-[44px] font-medium tracking-tight mb-4 leading-[1.05]">Choose a new password.</h2>
            <p className="text-white/70 text-[15px] leading-relaxed">Pick something strong. All existing sessions will be invalidated when you save.</p>
          </div>
          <div className="mono text-[11px] text-white/40 flex items-center gap-4">
            <span>SOC 2 Type II</span><span>·</span><span>ISO 27001</span><span>·</span><span>GDPR</span>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-center px-6 py-14 bg-white dark:bg-ink-950">
        <div className="w-full max-w-[400px]">
          {done ? (
            <div className="text-center">
              <div className="w-12 h-12 rounded-2xl bg-emerald-50 dark:bg-emerald-900/30 flex items-center justify-center mx-auto mb-4">
                <Icon name="shield-check" size={22} className="text-emerald-600 dark:text-emerald-400" />
              </div>
              <h1 className="text-[22px] font-medium tracking-tight text-ink-950 dark:text-white">Password updated</h1>
              <p className="mt-2 text-[13.5px] text-ink-500 leading-relaxed">All existing sessions have been signed out. Sign in with your new password.</p>
              <Button as="a" href="/signin" size="lg" variant="primary" className="mt-6 w-full justify-center">
                Sign in
              </Button>
            </div>
          ) : (
            <>
              <h1 className="text-[24px] font-medium tracking-tight text-ink-950 dark:text-white">Set new password</h1>
              <p className="mt-1.5 text-[13.5px] text-ink-500">Must be at least 8 characters with uppercase, number, and symbol.</p>

              {!token && (
                <div className="mt-6 p-3.5 rounded-xl border border-rose-200 dark:border-rose-800 bg-rose-50 dark:bg-rose-900/20 text-[12.5px] text-rose-700 dark:text-rose-300">
                  <Icon name="alert-triangle" size={14} className="inline mr-1.5 align-[-2px]" />
                  No reset token found. <a href="/forgot" className="underline">Request a new link.</a>
                </div>
              )}

              <form onSubmit={submit} className="mt-7">
                <label className="block mb-3.5">
                  <div className="mb-1.5 text-[12.5px] font-medium text-ink-700 dark:text-ink-300">New password</div>
                  <input type="password" value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="new-password"
                    className="w-full h-11 px-3.5 rounded-lg border border-ink-200 dark:border-ink-800 bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none focus:border-ink-950 dark:focus:border-white transition-colors" />
                </label>
                {pw && (
                  <div className="-mt-2.5 mb-4">
                    <div className="flex gap-1 mb-1.5">
                      {[1, 2, 3, 4, 5].map((i) => (
                        <span key={i} className={`flex-1 h-1 rounded-full ${
                          i <= strength.score
                            ? strength.tone === 'err' ? 'bg-rose-500' : strength.tone === 'warn' ? 'bg-amber-500' : 'bg-emerald-500'
                            : 'bg-ink-200 dark:bg-ink-800'
                        }`} />
                      ))}
                    </div>
                    <div className="mono text-[11px] text-ink-500">
                      strength · <span className={
                        strength.tone === 'err' ? 'text-rose-600 dark:text-rose-400' : strength.tone === 'warn' ? 'text-amber-600 dark:text-amber-400' : 'text-emerald-600 dark:text-emerald-400'
                      }>{strength.label}</span>
                    </div>
                  </div>
                )}
                <label className="block mb-5">
                  <div className="mb-1.5 text-[12.5px] font-medium text-ink-700 dark:text-ink-300">Confirm password</div>
                  <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} autoComplete="new-password"
                    className={`w-full h-11 px-3.5 rounded-lg border bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none transition-colors ${
                      confirm && confirm !== pw ? 'border-rose-400 dark:border-rose-700' : 'border-ink-200 dark:border-ink-800 focus:border-ink-950 dark:focus:border-white'
                    }`} />
                </label>
                {err && <div className="mb-3 text-[12px] text-rose-600 dark:text-rose-400">{err}</div>}
                <Button as="button" size="lg" variant="primary" className="w-full justify-center" disabled={loading || !token}>
                  {loading ? 'Saving…' : 'Save new password'}
                </Button>
              </form>
              <p className="mt-6 text-[12.5px] text-ink-500">
                <a href="/signin" className="underline">← Back to sign in</a>
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
