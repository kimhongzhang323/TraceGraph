import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { writeUser } from '@/hooks/useAuth'

const OAUTH = [
  { id: 'google',    label: 'Google',    icon: 'chrome' },
  { id: 'github',    label: 'GitHub',    icon: 'github' },
  { id: 'microsoft', label: 'Microsoft', icon: 'square' },
  { id: 'apple',     label: 'Apple',     icon: 'apple' },
]

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

function Field({
  label, type = 'text', value, onChange, hint, error, autoComplete,
}: {
  label: string; type?: string; value: string; onChange: (e: React.ChangeEvent<HTMLInputElement>) => void
  hint?: string; error?: string | null; autoComplete?: string
}) {
  return (
    <label className="block mb-3.5">
      <div className="mb-1.5">
        <span className="text-[12.5px] font-medium text-ink-700 dark:text-ink-300">{label}</span>
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

type FormData = { name: string; email: string; pw: string; org: string; role: string; tos: boolean; marketing: boolean }

export function SignUp() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [data, setData] = useState<FormData>({ name: '', email: '', pw: '', org: '', role: 'engineer', tos: false, marketing: false })
  const [err, setErr] = useState<string | null>(null)
  const strength = pwStrength(data.pw)

  function update<K extends keyof FormData>(k: K) {
    return (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
      const val = e.target.type === 'checkbox' ? (e.target as HTMLInputElement).checked : e.target.value
      setData((d) => ({ ...d, [k]: val }))
    }
  }

  const finish = () => {
    writeUser({
      email: data.email || 'user@example.com',
      name: data.name || 'New User',
      org: data.org || undefined,
      role: data.role,
      plan: 'Free',
      joined: new Date().toISOString().slice(0, 10),
      provider: 'password',
      mfa: false,
      avatarColor: '#0d8f63',
    })
    navigate('/profile')
  }

  const goStep2 = (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    if (!data.name.trim()) return setErr('Name is required')
    if (!data.email.includes('@')) return setErr('Enter a valid email')
    if (strength.score < 3) return setErr('Choose a stronger password (min. score: good)')
    if (!data.tos) return setErr('You must accept the Terms')
    setStep(2)
  }

  return (
    <div className="min-h-[calc(100vh-3.5rem)] grid lg:grid-cols-[1fr_520px]">
      {/* Left panel */}
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
            <h2 className="font-display text-[44px] font-medium tracking-tight mb-4 leading-[1.05]">Start tracing in 60 seconds.</h2>
            <p className="text-white/70 text-[15px] leading-relaxed">Free tier includes 50k trace events / month, JDBC-backed storage, and unlimited replay forks.</p>
            <div className="mt-10 p-5 rounded-xl border border-white/10 bg-white/[0.03]">
              <div className="mono text-[10.5px] uppercase tracking-wider text-white/50 mb-3">trusted by</div>
              <div className="grid grid-cols-3 gap-3 text-white/40 mono text-[12px]">
                <span>ATLAS·AI</span><span>NORTHWAVE</span><span>OCTANE</span>
                <span>QUARTERMILE</span><span>HEXOS</span><span>VANTA·LABS</span>
              </div>
            </div>
          </div>
          <div className="mono text-[11px] text-white/40 flex items-center gap-4">
            <span>SOC 2 Type II</span><span>·</span><span>ISO 27001</span><span>·</span><span>GDPR</span>
          </div>
        </div>
      </div>

      {/* Right panel */}
      <div className="flex items-center justify-center px-6 py-14 bg-white dark:bg-ink-950">
        <div className="w-full max-w-[400px]">
          <div className="flex items-center justify-between mb-7">
            <div>
              <h1 className="text-[24px] font-medium tracking-tight text-ink-950 dark:text-white">Create your account</h1>
              <p className="mt-1.5 text-[13.5px] text-ink-500">Have one? <a className="text-ink-950 dark:text-white underline underline-offset-2" href="/signin">Sign in</a></p>
            </div>
            <div className="flex items-center gap-1.5 mono text-[11px] text-ink-500">
              <span className={step >= 1 ? 'text-ink-950 dark:text-white' : ''}>01</span>
              <span className="w-6 h-px bg-ink-300 dark:bg-ink-700" />
              <span className={step >= 2 ? 'text-ink-950 dark:text-white' : ''}>02</span>
            </div>
          </div>

          {step === 1 && (
            <>
              <div className="grid grid-cols-2 gap-2.5 mb-5">
                {OAUTH.map((m) => (
                  <button key={m.id} onClick={finish}
                    className="h-11 rounded-lg border hairline bg-white dark:bg-ink-950 hover:bg-ink-50 dark:hover:bg-ink-900 flex items-center justify-center gap-2 text-[12.5px] text-ink-950 dark:text-white transition-colors">
                    <Icon name={m.icon} size={14} />{m.label}
                  </button>
                ))}
              </div>
              <Divider>or with email</Divider>
              <form onSubmit={goStep2}>
                <Field label="Full name" value={data.name} onChange={update('name')} autoComplete="name" />
                <Field label="Work email" type="email" value={data.email} onChange={update('email')} autoComplete="email" />
                <Field label="Password" type="password" value={data.pw} onChange={update('pw')} autoComplete="new-password"
                  hint="Min 8 chars · 1 uppercase · 1 number · 1 symbol" />
                {data.pw && (
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
                <label className="flex items-start gap-2.5 mb-3 text-[12.5px] text-ink-600 dark:text-ink-400">
                  <input type="checkbox" checked={data.tos} onChange={update('tos')} className="mt-0.5 rounded" />
                  <span>I agree to the <a href="#" className="underline">Terms of Service</a> and <a href="#" className="underline">Privacy Policy</a>.</span>
                </label>
                <label className="flex items-start gap-2.5 mb-5 text-[12.5px] text-ink-600 dark:text-ink-400">
                  <input type="checkbox" checked={data.marketing} onChange={update('marketing')} className="mt-0.5 rounded" />
                  <span>Send me product updates (you can unsubscribe at any time).</span>
                </label>
                {err && <div className="mb-3 text-[12px] text-rose-600 dark:text-rose-400">{err}</div>}
                <Button as="button" size="lg" variant="primary" className="w-full justify-center" iconRight="arrow-right">Continue</Button>
              </form>
            </>
          )}

          {step === 2 && (
            <>
              <div className="mb-6 p-4 rounded-xl border border-emerald-200 dark:border-emerald-700/40 bg-emerald-50/60 dark:bg-emerald-700/10 flex items-start gap-3">
                <Icon name="shield-check" size={16} className="text-emerald-700 dark:text-emerald-400 mt-0.5 shrink-0" />
                <div className="text-[12.5px] text-ink-700 dark:text-ink-300">
                  <strong>Step 2 of 2 · Secure your account.</strong> We strongly recommend enabling MFA. You can change this any time in profile settings.
                </div>
              </div>
              <div className="mb-4">
                <label className="block mb-1.5 text-[12.5px] font-medium text-ink-700 dark:text-ink-300">Organization (optional)</label>
                <input value={data.org} onChange={update('org')}
                  className="w-full h-11 px-3.5 rounded-lg border border-ink-200 dark:border-ink-800 bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none focus:border-ink-950 dark:focus:border-white transition-colors" />
                <div className="mt-1.5 text-[11.5px] text-ink-500">Used for billing & SSO routing.</div>
              </div>
              <label className="block mb-5">
                <span className="text-[12.5px] font-medium text-ink-700 dark:text-ink-300 mb-1.5 block">Your role</span>
                <select value={data.role} onChange={update('role')}
                  className="w-full h-11 px-3 rounded-lg border hairline bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none">
                  <option value="engineer">Software engineer</option>
                  <option value="ml">ML / AI engineer</option>
                  <option value="sre">SRE / platform</option>
                  <option value="founder">Founder / CTO</option>
                  <option value="researcher">Researcher</option>
                  <option value="other">Other</option>
                </select>
              </label>
              <div className="mb-5 rounded-xl border hairline overflow-hidden">
                {([
                  ['Authenticator app (recommended)', 'smartphone', 'TOTP · works offline'],
                  ['Hardware security key', 'key', 'WebAuthn · FIDO2'],
                  ['Skip for now', 'x', 'Reduces your account security'],
                ] as const).map(([t, ic, sub], i) => (
                  <button key={i} onClick={finish}
                    className={`w-full text-left flex items-center gap-3 px-4 py-3 hover:bg-ink-50 dark:hover:bg-ink-900 transition-colors ${i > 0 ? 'border-t hairline' : ''}`}>
                    <span className="w-8 h-8 rounded-md bg-ink-100 dark:bg-ink-900 flex items-center justify-center text-ink-700 dark:text-ink-300 shrink-0">
                      <Icon name={ic} size={14} />
                    </span>
                    <div className="flex-1">
                      <div className="text-[13.5px] text-ink-950 dark:text-white">{t}</div>
                      <div className="text-[11.5px] text-ink-500">{sub}</div>
                    </div>
                    <Icon name="arrow-right" size={14} className="text-ink-500" />
                  </button>
                ))}
              </div>
              <Button as="button" size="md" variant="ghost" onClick={() => setStep(1)} icon="arrow-left">Back</Button>
            </>
          )}

          <p className="mt-6 text-center text-[11.5px] text-ink-500">
            Protected by reCAPTCHA Enterprise ·{' '}
            <a href="#" className="underline">Privacy</a> ·{' '}
            <a href="#" className="underline">Terms</a>
          </p>
        </div>
      </div>
    </div>
  )
}
