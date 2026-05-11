import { useState } from 'react'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'

export function Forgot() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)

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
            <h2 className="font-display text-[44px] font-medium tracking-tight mb-4 leading-[1.05]">Reset password.</h2>
            <p className="text-white/70 text-[15px] leading-relaxed">We will send a single-use link to your email. The link expires in 30 minutes.</p>
          </div>
          <div className="mono text-[11px] text-white/40 flex items-center gap-4">
            <span>SOC 2 Type II</span><span>·</span><span>ISO 27001</span><span>·</span><span>GDPR</span>
          </div>
        </div>
      </div>

      <div className="flex items-center justify-center px-6 py-14 bg-white dark:bg-ink-950">
        <div className="w-full max-w-[400px]">
          <h1 className="text-[24px] font-medium tracking-tight text-ink-950 dark:text-white">Forgot your password?</h1>
          <p className="mt-1.5 text-[13.5px] text-ink-500">No worries — enter your email below.</p>
          {!sent ? (
            <form onSubmit={(e) => { e.preventDefault(); setSent(true) }} className="mt-7">
              <label className="block mb-3.5">
                <div className="mb-1.5">
                  <span className="text-[12.5px] font-medium text-ink-700 dark:text-ink-300">Email</span>
                </div>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email"
                  className="w-full h-11 px-3.5 rounded-lg border border-ink-200 dark:border-ink-800 bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none focus:border-ink-950 dark:focus:border-white transition-colors" />
              </label>
              <Button as="button" size="lg" variant="primary" className="w-full justify-center mt-2" iconRight="arrow-right">
                Send reset link
              </Button>
            </form>
          ) : (
            <div className="mt-7 p-4 rounded-xl border border-emerald-200 dark:border-emerald-700/40 bg-emerald-50/60 dark:bg-emerald-700/10">
              <Icon name="mail-check" size={18} className="text-emerald-700 dark:text-emerald-400 mb-2" />
              <h3 className="text-[15px] font-medium text-ink-950 dark:text-white">Check your inbox</h3>
              <p className="mt-1 text-[12.5px] text-ink-600 dark:text-ink-400">
                If an account exists for <strong>{email}</strong>, a reset link is on the way.
              </p>
            </div>
          )}
          <p className="mt-6 text-[12.5px] text-ink-500">
            <a href="/signin" className="underline">← Back to sign in</a>
          </p>
        </div>
      </div>
    </div>
  )
}
