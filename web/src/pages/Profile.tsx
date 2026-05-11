import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { Badge } from '@/components/Badge'
import { StatusDot } from '@/components/StatusDot'
import { useAuth } from '@/hooks/useAuth'
import { api } from '@/lib/api'
import type { AuthUser } from '@/hooks/useAuth'
import type { ApiKey, AuditEntry, BillingInfo, Prefs, Session, UsageStats } from '@/lib/api'

// ── Demo fallbacks (shown when backend is unreachable) ─────────────────────
const DEMO_STATS: UsageStats = {
  totalRuns: 1284, replayForks: 312, failureRate: 6.2, tokensUsed: 2400000,
  runsWeekDelta: 38, forksWeekDelta: 5, failureRateDelta: -1.4, p50TokensPerCall: 412,
}
const DEMO_AUDIT: AuditEntry[] = [
  { id: '1', what: 'Forked execution e9c4f1a2 from step 3', ip: '203.116.42.18', when: '', whenRelative: '12m ago' },
  { id: '2', what: 'Created API key prod · ingestion',      ip: '203.116.42.18', when: '', whenRelative: '1h ago' },
  { id: '3', what: 'Enabled hardware-key MFA',              ip: '203.116.42.18', when: '', whenRelative: 'yesterday' },
  { id: '4', what: 'Rotated personal access token',         ip: '203.116.42.18', when: '', whenRelative: '2 days ago' },
  { id: '5', what: 'Updated email preferences',             ip: '49.207.182.4',  when: '', whenRelative: '3 days ago' },
]
const DEMO_KEYS: ApiKey[] = [
  { id: '1', name: 'prod · ingestion',   prefix: 'tg_live_7a4f…b21c', scope: 'read+write', createdAt: '2026-03-12', lastUsedAt: null, lastUsedRelative: '12m ago' },
  { id: '2', name: 'staging · ci',       prefix: 'tg_test_91c2…04ef', scope: 'read+write', createdAt: '2026-04-02', lastUsedAt: null, lastUsedRelative: '2h ago' },
  { id: '3', name: 'local · dev laptop', prefix: 'tg_test_3b80…ff10', scope: 'read',       createdAt: '2026-04-21', lastUsedAt: null, lastUsedRelative: 'yesterday' },
]
const DEMO_SESSIONS: Session[] = [
  { id: 'current', device: 'MacBook Pro 14"',   browser: 'Chrome 124 · macOS 15.4', location: 'Singapore · SG', ip: '203.116.42.18', lastActiveAt: '', lastActiveRelative: 'active now',  current: true },
  { id: 's2',      device: 'iPhone 16 Pro',     browser: 'Safari · iOS 18.4',       location: 'Singapore · SG', ip: '203.116.42.18', lastActiveAt: '', lastActiveRelative: '1h ago',       current: false },
  { id: 's3',      device: 'Linux workstation', browser: 'Firefox 126 · Ubuntu 24', location: 'Bengaluru · IN', ip: '49.207.182.4',  lastActiveAt: '', lastActiveRelative: '3 days ago',   current: false },
]
const DEMO_BILLING: BillingInfo = {
  plan: 'Pro', traceEventsUsed: 1200000, traceEventsLimit: 5000000,
  replayForksUnlimited: true, nextBillingDate: '2026-06-01', amount: 29,
}
const DEMO_PREFS: Prefs = { emailDigest: true, failureAlerts: true, betaFeatures: false }
const DEMO_TRACES = [
  { id: 'e9c4f1a2', graph: 'order-pipeline',  status: 'failed',      dur: 1041, steps: 5,  fork: '-',    when: '12m ago' },
  { id: 'a73d2eb5', graph: 'order-pipeline',  status: 'completed',   dur: 842,  steps: 5,  fork: '-',    when: '2h ago' },
  { id: 'b1f9c022', graph: 'rag-agent',       status: 'completed',   dur: 1812, steps: 8,  fork: '4c7e', when: '5h ago' },
  { id: '4c7e8d11', graph: 'react-agent',     status: 'interrupted', dur: 6240, steps: 12, fork: '-',    when: 'yesterday' },
  { id: 'd8a44b06', graph: 'order-pipeline',  status: 'completed',   dur: 1104, steps: 5,  fork: '-',    when: '2 days ago' },
]

// ── Generic fetch hook ─────────────────────────────────────────────────────
function useRemote<T>(fetcher: () => Promise<T>, fallback: T) {
  const [data, setData]     = useState<T>(fallback)
  const [loading, setLoading] = useState(true)
  const reload = useCallback(() => {
    setLoading(true)
    fetcher()
      .then(setData)
      .catch(() => setData(fallback))
      .finally(() => setLoading(false))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { reload() }, [reload])
  return { data, setData, loading, reload }
}

// ── Shared UI ──────────────────────────────────────────────────────────────
function SectionCard({ title, action, children }: { title: string; action?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border hairline bg-white dark:bg-ink-950 overflow-hidden">
      <div className="flex items-center justify-between px-5 py-3.5 border-b hairline">
        <span className="text-[13.5px] font-medium text-ink-950 dark:text-white">{title}</span>
        {action}
      </div>
      {children}
    </div>
  )
}

function Skeleton({ className = '' }: { className?: string }) {
  return <div className={`animate-pulse rounded bg-ink-100 dark:bg-ink-800 ${className}`} />
}

function statusTone(s: string): 'ok' | 'err' | 'warn' | 'neutral' {
  if (s === 'completed' || s === 'COMPLETED') return 'ok'
  if (s === 'failed'    || s === 'FAILED')    return 'err'
  if (s === 'interrupted'|| s === 'INTERRUPTED') return 'warn'
  return 'neutral'
}

function fmtNum(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000)     return `${(n / 1_000).toFixed(1)}k`
  return String(n)
}

// ── Hero ───────────────────────────────────────────────────────────────────
function ProfileHero({ user, onSignOut }: { user: AuthUser; onSignOut: () => void }) {
  const initials = user.name.split(' ').map((s) => s[0]).slice(0, 2).join('').toUpperCase()
  return (
    <div className="rounded-2xl border hairline bg-white dark:bg-ink-950 p-6 lg:p-8 flex items-center gap-6 flex-wrap">
      <div
        className="w-16 h-16 lg:w-20 lg:h-20 rounded-full flex items-center justify-center text-white text-[24px] lg:text-[28px] font-medium tracking-tight shrink-0"
        style={{ background: `linear-gradient(135deg, ${user.avatarColor} 0%, #0a6447 100%)` }}>
        {initials}
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-3 flex-wrap">
          <h1 className="text-[26px] lg:text-[30px] font-medium tracking-tight text-ink-950 dark:text-white">{user.name}</h1>
          <Badge tone={user.mfa ? 'ok' : 'warn'}><StatusDot tone={user.mfa ? 'ok' : 'warn'} />{user.mfa ? 'MFA on' : 'MFA off'}</Badge>
          <Badge tone="neutral">{user.plan}</Badge>
        </div>
        <div className="mt-1.5 mono text-[12px] text-ink-500 flex items-center gap-3 flex-wrap">
          <span><Icon name="mail" size={11} className="inline mr-1 align-[-1px]" />{user.email}</span>
          <span>·</span><span>{user.org ?? 'Personal'}</span>
          <span>·</span><span>joined {user.joined}</span>
          <span>·</span><span>via {user.provider}</span>
        </div>
      </div>
      <Button size="sm" variant="ghost" icon="log-out" onClick={onSignOut}>Sign out</Button>
    </div>
  )
}

// ── Overview ───────────────────────────────────────────────────────────────
function ProfileOverview({ user }: { user: AuthUser }) {
  const { data: stats, loading: statsLoading } = useRemote(() => api.profile.stats(), DEMO_STATS)
  const { data: audit, loading: auditLoading } = useRemote(() => api.profile.audit(), DEMO_AUDIT)

  return (
    <div className="space-y-6">
      <div className="grid sm:grid-cols-4 gap-3">
        {statsLoading
          ? Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="rounded-xl border hairline bg-white dark:bg-ink-950 p-4 space-y-2">
                <Skeleton className="h-3 w-20" />
                <Skeleton className="h-7 w-16" />
                <Skeleton className="h-2.5 w-24" />
              </div>
            ))
          : ([
              ['Total runs',   fmtNum(stats.totalRuns),   `+${stats.runsWeekDelta} this week`],
              ['Replay forks', fmtNum(stats.replayForks), `+${stats.forksWeekDelta} this week`],
              ['Failed runs',  `${stats.failureRate.toFixed(1)}%`, `${stats.failureRateDelta > 0 ? '+' : ''}${stats.failureRateDelta.toFixed(1)}% vs prior`],
              ['Tokens used',  fmtNum(stats.tokensUsed),  `p50 ${fmtNum(stats.p50TokensPerCall)} / call`],
            ] as const).map(([k, v, sub]) => (
              <div key={k} className="rounded-xl border hairline bg-white dark:bg-ink-950 p-4">
                <div className="mono text-[10.5px] uppercase tracking-wider text-ink-500">{k}</div>
                <div className="text-[26px] font-medium tracking-tight text-ink-950 dark:text-white mt-1.5">{v}</div>
                <div className="mono text-[11px] text-ink-500 mt-1">{sub}</div>
              </div>
            ))}
      </div>

      <SectionCard title="Recent activity">
        {auditLoading
          ? <div className="divide-y hairline">{Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="flex justify-between px-5 py-3 gap-4">
                <Skeleton className="h-3.5 flex-1 max-w-xs" />
                <Skeleton className="h-3 w-28" />
              </div>))}</div>
          : <ul className="divide-y hairline">
              {audit.map((a) => (
                <li key={a.id} className="flex items-start justify-between px-5 py-3 gap-6">
                  <span className="text-[13px] text-ink-700 dark:text-ink-300 flex-1">{a.what}</span>
                  <span className="mono text-[11px] text-ink-500 shrink-0">{a.whenRelative} · {a.ip}</span>
                </li>
              ))}
            </ul>}
      </SectionCard>

      <SectionCard title="Account">
        <div className="divide-y hairline">
          {([
            ['Name',         user.name],
            ['Email',        user.email],
            ['Organization', user.org ?? 'Personal'],
            ['Plan',         user.plan],
            ['Member since', user.joined],
          ] as const).map(([k, v]) => (
            <div key={k} className="flex items-center justify-between px-5 py-3">
              <span className="text-[13px] text-ink-500 w-36">{k}</span>
              <span className="text-[13px] text-ink-950 dark:text-white flex-1">{v}</span>
            </div>
          ))}
        </div>
      </SectionCard>
    </div>
  )
}

// ── Traces ─────────────────────────────────────────────────────────────────
function ProfileTraces() {
  const [traces, setTraces] = useState(DEMO_TRACES)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.traces.list(20)
      .then((data) => {
        const ids: string[] = Array.isArray(data) ? (data as string[]) : ((data as { items: string[] }).items ?? [])
        if (ids.length > 0) {
          setTraces(ids.map((id) => ({ id: id.slice(0, 8), graph: 'graph', status: 'COMPLETED', dur: 0, steps: 0, fork: '-', when: '-' })))
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const handleDelete = (id: string) => {
    if (!confirm(`Delete trace ${id}?`)) return
    api.traces.delete(id)
      .then(() => setTraces((t) => t.filter((r) => r.id !== id)))
      .catch(() => alert('Delete failed'))
  }

  return (
    <SectionCard title="My traces">
      {loading
        ? <div className="divide-y hairline">{Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex gap-4 px-5 py-3">
              {Array.from({ length: 5 }).map((__, j) => <Skeleton key={j} className="h-3.5 w-20" />)}
            </div>))}</div>
        : <div className="overflow-x-auto">
            <table className="w-full text-[12.5px]">
              <thead>
                <tr className="border-b hairline">
                  {['ID', 'Graph', 'Status', 'Duration', 'Steps', 'Fork', 'When', ''].map((h) => (
                    <th key={h} className="text-left px-5 py-2.5 mono text-[10.5px] uppercase tracking-wider text-ink-500 font-normal">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y hairline">
                {traces.map((r) => (
                  <tr key={r.id} className="hover:bg-ink-50 dark:hover:bg-ink-900/50 transition-colors">
                    <td className="px-5 py-3 mono text-ink-950 dark:text-white">{r.id}</td>
                    <td className="px-5 py-3 text-ink-700 dark:text-ink-300">{r.graph}</td>
                    <td className="px-5 py-3"><Badge tone={statusTone(r.status)}><StatusDot tone={statusTone(r.status)} />{r.status.toLowerCase()}</Badge></td>
                    <td className="px-5 py-3 mono text-ink-500">{r.dur ? `${r.dur}ms` : '—'}</td>
                    <td className="px-5 py-3 mono text-ink-500">{r.steps || '—'}</td>
                    <td className="px-5 py-3 mono text-ink-500">{r.fork}</td>
                    <td className="px-5 py-3 mono text-ink-500">{r.when}</td>
                    <td className="px-5 py-3">
                      <button onClick={() => handleDelete(r.id)} className="mono text-[10.5px] text-rose-500 hover:text-rose-700 dark:hover:text-rose-300">delete</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>}
    </SectionCard>
  )
}

// ── Security ───────────────────────────────────────────────────────────────
function ProfileSecurity({ user }: { user: AuthUser }) {
  const { data: audit, loading: auditLoading } = useRemote(() => api.profile.audit(), DEMO_AUDIT)
  const [pwModal, setPwModal] = useState(false)
  const [currentPw, setCurrentPw] = useState('')
  const [nextPw, setNextPw]     = useState('')
  const [pwBusy, setPwBusy]     = useState(false)
  const [pwErr, setPwErr]       = useState<string | null>(null)

  const changePw = async (e: React.FormEvent) => {
    e.preventDefault()
    if (nextPw.length < 8) { setPwErr('Min 8 characters'); return }
    setPwBusy(true); setPwErr(null)
    try {
      await api.profile.changePassword(currentPw, nextPw)
      setPwModal(false); setCurrentPw(''); setNextPw('')
      alert('Password updated. All other sessions have been signed out.')
    } catch {
      setPwErr('Incorrect current password.')
    } finally { setPwBusy(false) }
  }

  const deleteAccount = async () => {
    if (!confirm('Permanently delete your account and all trace data? This cannot be undone.')) return
    if (!confirm('Are you absolutely sure?')) return
    try {
      await api.profile.deleteAccount()
      window.location.href = '/'
    } catch {
      alert('Delete failed. Contact support.')
    }
  }

  return (
    <div className="space-y-4">
      <SectionCard title="Multi-factor authentication">
        <div className="p-5 space-y-3">
          {([
            ['Authenticator app', 'smartphone', user.mfa],
            ['Hardware security key (FIDO2)', 'key', false],
            ['Backup codes', 'shield', user.mfa],
          ] as const).map(([label, icon, enabled]) => (
            <div key={label} className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Icon name={icon} size={15} className="text-ink-500" />
                <span className="text-[13.5px] text-ink-950 dark:text-white">{label}</span>
              </div>
              <Badge tone={enabled ? 'ok' : 'neutral'}>{enabled ? 'Enabled' : 'Not set'}</Badge>
            </div>
          ))}
        </div>
      </SectionCard>

      <SectionCard title="Audit log">
        {auditLoading
          ? <div className="divide-y hairline">{Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="flex justify-between px-5 py-3 gap-4">
                <Skeleton className="h-3.5 flex-1 max-w-xs" /><Skeleton className="h-3 w-20" />
              </div>))}</div>
          : <ul className="divide-y hairline">
              {audit.map((a) => (
                <li key={a.id} className="flex items-start justify-between px-5 py-3 gap-4">
                  <span className="text-[13px] text-ink-700 dark:text-ink-300 flex-1">{a.what}</span>
                  <span className="mono text-[11px] text-ink-500 shrink-0">{a.whenRelative}</span>
                </li>
              ))}
            </ul>}
      </SectionCard>

      <SectionCard title="Danger zone">
        <div className="p-5 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-[13.5px] text-ink-950 dark:text-white">Change password</div>
              <div className="text-[12px] text-ink-500 mt-0.5">
                {user.provider === 'password' ? 'Update your password.' : `Using ${user.provider} — set a password below.`}
              </div>
            </div>
            <Button size="sm" variant="ghost" onClick={() => setPwModal(true)}>Change</Button>
          </div>
          <div className="flex items-center justify-between pt-3 border-t hairline">
            <div>
              <div className="text-[13.5px] text-rose-600 dark:text-rose-400">Delete account</div>
              <div className="text-[12px] text-ink-500 mt-0.5">Permanently deletes all traces and data.</div>
            </div>
            <Button size="sm" variant="ghost" className="!text-rose-600 dark:!text-rose-400 !border-rose-200 dark:!border-rose-800" onClick={deleteAccount}>Delete</Button>
          </div>
        </div>
      </SectionCard>

      {pwModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white dark:bg-ink-950 rounded-2xl border hairline p-6 w-full max-w-sm shadow-xl">
            <h3 className="text-[16px] font-medium text-ink-950 dark:text-white mb-4">Change password</h3>
            <form onSubmit={changePw} className="space-y-3">
              <label className="block">
                <span className="text-[12px] text-ink-600 dark:text-ink-400">Current password</span>
                <input type="password" value={currentPw} onChange={(e) => setCurrentPw(e.target.value)}
                  className="mt-1 w-full h-10 px-3 rounded-lg border hairline bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none focus:border-ink-950 dark:focus:border-white transition-colors" />
              </label>
              <label className="block">
                <span className="text-[12px] text-ink-600 dark:text-ink-400">New password</span>
                <input type="password" value={nextPw} onChange={(e) => setNextPw(e.target.value)}
                  className="mt-1 w-full h-10 px-3 rounded-lg border hairline bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none focus:border-ink-950 dark:focus:border-white transition-colors" />
              </label>
              {pwErr && <div className="text-[12px] text-rose-600 dark:text-rose-400">{pwErr}</div>}
              <div className="flex gap-2 pt-1">
                <Button as="button" size="sm" variant="primary" disabled={pwBusy} className="flex-1 justify-center">
                  {pwBusy ? 'Saving…' : 'Save'}
                </Button>
                <Button as="button" size="sm" variant="ghost" onClick={() => setPwModal(false)} className="flex-1 justify-center">Cancel</Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}

// ── API Keys ───────────────────────────────────────────────────────────────
function ProfileKeys() {
  const { data: keys, setData: setKeys, loading } = useRemote(() => api.profile.keys(), DEMO_KEYS)
  const [newModal, setNewModal] = useState(false)
  const [newName, setNewName]   = useState('')
  const [newScope, setNewScope] = useState('read+write')
  const [creating, setCreating] = useState(false)
  const [revealed, setRevealed] = useState<string | null>(null)

  const createKey = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newName.trim()) return
    setCreating(true)
    try {
      const result = await api.profile.createKey(newName.trim(), newScope)
      setKeys((k) => [result, ...k])
      setRevealed(result.secret)
      setNewModal(false); setNewName(''); setNewScope('read+write')
    } catch {
      // demo fallback
      const demo: ApiKey = { id: Date.now().toString(), name: newName.trim(), prefix: `tg_live_xxxx…xxxx`, scope: newScope, createdAt: new Date().toISOString().slice(0, 10), lastUsedAt: null, lastUsedRelative: 'never' }
      setKeys((k) => [demo, ...k])
      setRevealed('tg_live_DEMO_KEY_NOT_REAL_xxxx')
      setNewModal(false); setNewName(''); setNewScope('read+write')
    } finally { setCreating(false) }
  }

  const revokeKey = (id: string, name: string) => {
    if (!confirm(`Revoke key "${name}"? This cannot be undone.`)) return
    api.profile.revokeKey(id)
      .catch(() => {})
      .finally(() => setKeys((k) => k.filter((x) => x.id !== id)))
  }

  return (
    <>
      <SectionCard title="API keys" action={<Button size="sm" variant="primary" icon="plus" onClick={() => setNewModal(true)}>New key</Button>}>
        {loading
          ? <div className="divide-y hairline">{Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="flex justify-between px-5 py-4 gap-4">
                <div className="space-y-2"><Skeleton className="h-3.5 w-32" /><Skeleton className="h-2.5 w-48" /></div>
                <Skeleton className="h-8 w-16" />
              </div>))}</div>
          : <div className="divide-y hairline">
              {keys.length === 0 && <div className="px-5 py-6 text-[13px] text-ink-500">No API keys yet.</div>}
              {keys.map((k) => (
                <div key={k.id} className="px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
                  <div>
                    <div className="text-[13.5px] font-medium text-ink-950 dark:text-white">{k.name}</div>
                    <div className="mono text-[11.5px] text-ink-500 mt-1">{k.prefix} · {k.scope} · created {k.createdAt}</div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className="mono text-[11px] text-ink-500">last used {k.lastUsedRelative ?? 'never'}</span>
                    <Button size="sm" variant="ghost" icon="trash-2" className="!text-rose-600 dark:!text-rose-400" onClick={() => revokeKey(k.id, k.name)}>Revoke</Button>
                  </div>
                </div>
              ))}
            </div>}
      </SectionCard>

      {newModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white dark:bg-ink-950 rounded-2xl border hairline p-6 w-full max-w-sm shadow-xl">
            <h3 className="text-[16px] font-medium text-ink-950 dark:text-white mb-4">New API key</h3>
            <form onSubmit={createKey} className="space-y-3">
              <label className="block">
                <span className="text-[12px] text-ink-600 dark:text-ink-400">Key name</span>
                <input value={newName} onChange={(e) => setNewName(e.target.value)} placeholder="e.g. prod · ingestion"
                  className="mt-1 w-full h-10 px-3 rounded-lg border hairline bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none focus:border-ink-950 dark:focus:border-white transition-colors" />
              </label>
              <label className="block">
                <span className="text-[12px] text-ink-600 dark:text-ink-400">Scope</span>
                <select value={newScope} onChange={(e) => setNewScope(e.target.value)}
                  className="mt-1 w-full h-10 px-3 rounded-lg border hairline bg-white dark:bg-ink-950 text-[14px] text-ink-950 dark:text-white outline-none">
                  <option value="read">read</option>
                  <option value="read+write">read+write</option>
                </select>
              </label>
              <div className="flex gap-2 pt-1">
                <Button as="button" size="sm" variant="primary" disabled={creating} className="flex-1 justify-center">{creating ? 'Creating…' : 'Create'}</Button>
                <Button as="button" size="sm" variant="ghost" onClick={() => setNewModal(false)} className="flex-1 justify-center">Cancel</Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {revealed && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="bg-white dark:bg-ink-950 rounded-2xl border hairline p-6 w-full max-w-sm shadow-xl">
            <div className="flex items-center gap-2 mb-3">
              <Icon name="shield-check" size={16} className="text-emerald-500" />
              <h3 className="text-[16px] font-medium text-ink-950 dark:text-white">Copy your key now</h3>
            </div>
            <p className="text-[12.5px] text-ink-500 mb-3">This is shown once only. Store it securely.</p>
            <div className="bg-ink-950 dark:bg-black/60 rounded-lg p-3 mono text-[12px] text-emerald-300 break-all select-all">{revealed}</div>
            <Button as="button" size="sm" variant="primary" className="w-full justify-center mt-4" onClick={() => setRevealed(null)}>I've saved it</Button>
          </div>
        </div>
      )}
    </>
  )
}

// ── Sessions ───────────────────────────────────────────────────────────────
function ProfileSessions() {
  const { data: sessions, setData: setSessions, loading } = useRemote(() => api.profile.sessions(), DEMO_SESSIONS)

  const revokeSession = (id: string) => {
    if (!confirm('Revoke this session?')) return
    api.profile.revokeSession(id)
      .catch(() => {})
      .finally(() => setSessions((s) => s.filter((x) => x.id !== id)))
  }

  const revokeOthers = () => {
    if (!confirm('Sign out all other sessions?')) return
    api.profile.revokeOthers()
      .catch(() => {})
      .finally(() => setSessions((s) => s.filter((x) => x.current)))
  }

  return (
    <SectionCard title="Active sessions" action={
      <Button size="sm" variant="ghost" className="!text-rose-600 dark:!text-rose-400" onClick={revokeOthers}>Revoke all others</Button>
    }>
      {loading
        ? <div className="divide-y hairline">{Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex justify-between px-5 py-4 gap-4">
              <div className="space-y-2"><Skeleton className="h-3.5 w-40" /><Skeleton className="h-2.5 w-56" /></div>
              <Skeleton className="h-8 w-16" />
            </div>))}</div>
        : <div className="divide-y hairline">
            {sessions.map((s) => (
              <div key={s.id} className="px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
                <div className="flex items-start gap-3">
                  <Icon name="monitor" size={16} className="text-ink-500 mt-0.5 shrink-0" />
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-[13.5px] font-medium text-ink-950 dark:text-white">{s.device}</span>
                      {s.current && <Badge tone="ok"><StatusDot tone="ok" />current</Badge>}
                    </div>
                    <div className="mono text-[11.5px] text-ink-500 mt-0.5">{s.browser} · {s.location} · {s.ip}</div>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <span className="mono text-[11px] text-ink-500">{s.lastActiveRelative}</span>
                  {!s.current && (
                    <Button size="sm" variant="ghost" className="!text-rose-600 dark:!text-rose-400" onClick={() => revokeSession(s.id)}>Revoke</Button>
                  )}
                </div>
              </div>
            ))}
          </div>}
    </SectionCard>
  )
}

// ── Billing ────────────────────────────────────────────────────────────────
function ProfileBilling() {
  const { data: billing, loading } = useRemote(() => api.profile.billing(), DEMO_BILLING)

  const pct = Math.min(100, Math.round((billing.traceEventsUsed / billing.traceEventsLimit) * 100))

  return (
    <div className="space-y-4">
      <SectionCard title="Current plan">
        {loading
          ? <div className="p-5 space-y-4"><Skeleton className="h-6 w-24" /><Skeleton className="h-3 w-48" /><Skeleton className="h-24 rounded-xl" /></div>
          : <div className="p-5">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <div className="text-[20px] font-medium tracking-tight text-ink-950 dark:text-white">{billing.plan}</div>
                  <div className="text-[13px] text-ink-500 mt-0.5">
                    {billing.amount ? `$${billing.amount}/mo` : 'Free'} ·{' '}
                    {billing.nextBillingDate ? `next billing ${billing.nextBillingDate}` : 'no billing'}
                  </div>
                </div>
                {billing.plan === 'Free' && <Button size="sm" variant="primary">Upgrade</Button>}
              </div>

              <div className="mb-4">
                <div className="flex justify-between mono text-[11px] text-ink-500 mb-1.5">
                  <span>Trace events</span>
                  <span>{fmtNum(billing.traceEventsUsed)} / {fmtNum(billing.traceEventsLimit)}</span>
                </div>
                <div className="h-2 rounded-full bg-ink-100 dark:bg-ink-800 overflow-hidden">
                  <div className={`h-full rounded-full transition-all ${pct > 85 ? 'bg-rose-500' : 'bg-accent-500'}`} style={{ width: `${pct}%` }} />
                </div>
                <div className="mono text-[11px] text-ink-400 mt-1">{pct}% used</div>
              </div>

              <div className="rounded-xl border hairline overflow-hidden">
                {([
                  ['Replay forks', billing.replayForksUnlimited ? 'Unlimited' : 'Limited'],
                  ['JDBC trace store', 'Included'],
                  ['JSON file store', 'Included'],
                ] as const).map(([k, v], i) => (
                  <div key={k} className={`flex justify-between px-4 py-2.5 text-[13px] ${i > 0 ? 'border-t hairline' : ''}`}>
                    <span className="text-ink-600 dark:text-ink-400">{k}</span>
                    <span className="text-ink-950 dark:text-white mono text-[12px]">{v}</span>
                  </div>
                ))}
              </div>
            </div>}
      </SectionCard>
    </div>
  )
}

// ── Preferences ────────────────────────────────────────────────────────────
function ProfilePrefs() {
  const { data: prefs, setData: setPrefs, loading } = useRemote(() => api.profile.prefs(), DEMO_PREFS)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved]   = useState(false)

  const toggle = async (key: keyof Prefs) => {
    const next = { ...prefs, [key]: !prefs[key] }
    setPrefs(next)
    setSaving(true); setSaved(false)
    try {
      await api.profile.savePrefs(next)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } catch {
      // best-effort; local state already updated
    } finally { setSaving(false) }
  }

  return (
    <SectionCard title="Preferences" action={
      <span className={`mono text-[11px] transition-opacity ${saved ? 'text-emerald-600 dark:text-emerald-400 opacity-100' : 'opacity-0'}`}>
        Saved ✓
      </span>
    }>
      {loading
        ? <div className="divide-y hairline">{Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex justify-between items-center px-5 py-4">
              <div className="space-y-1.5"><Skeleton className="h-3.5 w-40" /><Skeleton className="h-2.5 w-56" /></div>
              <Skeleton className="h-5.5 w-10 rounded-full" />
            </div>))}</div>
        : <div className="divide-y hairline">
            {([
              ['emailDigest',    'Weekly digest email',   'Summary of runs, replays, and usage.'],
              ['failureAlerts',  'Failure alerts',        'Email me when a run fails in production.'],
              ['betaFeatures',   'Beta features',         'Early access to experimental features.'],
            ] as const).map(([key, label, desc]) => (
              <div key={key} className="flex items-center justify-between px-5 py-4">
                <div>
                  <div className="text-[13.5px] text-ink-950 dark:text-white">{label}</div>
                  <div className="text-[12px] text-ink-500 mt-0.5">{desc}</div>
                </div>
                <button
                  onClick={() => !saving && toggle(key)}
                  disabled={saving}
                  className={`relative rounded-full transition-colors disabled:opacity-60 ${prefs[key] ? 'bg-emerald-500' : 'bg-ink-200 dark:bg-ink-800'}`}
                  style={{ width: 40, height: 22 }}>
                  <span
                    className="absolute top-0.5 left-0.5 rounded-full bg-white shadow transition-transform"
                    style={{ width: 18, height: 18, transform: prefs[key] ? 'translateX(18px)' : 'translateX(0)' }} />
                </button>
              </div>
            ))}
          </div>}
    </SectionCard>
  )
}

// ── Root ───────────────────────────────────────────────────────────────────
const TABS = [
  ['overview', 'Overview',    'layout-dashboard'],
  ['traces',   'My traces',   'git-branch'],
  ['security', 'Security',    'shield'],
  ['keys',     'API keys',    'key'],
  ['sessions', 'Sessions',    'monitor'],
  ['billing',  'Billing',     'credit-card'],
  ['prefs',    'Preferences', 'sliders-horizontal'],
] as const

type TabId = (typeof TABS)[number][0]

export function Profile() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState<TabId>('overview')

  const handleSignOut = () => { signOut(); navigate('/') }

  if (!user) {
    return (
      <div className="max-w-md mx-auto px-6 py-24 text-center">
        <div className="w-12 h-12 rounded-full bg-ink-100 dark:bg-ink-900 mx-auto mb-5 flex items-center justify-center text-ink-700 dark:text-ink-300">
          <Icon name="lock" size={20} />
        </div>
        <h1 className="text-[24px] font-medium tracking-tight text-ink-950 dark:text-white">Sign in required</h1>
        <p className="mt-2 text-[13.5px] text-ink-500">You need to sign in to view your profile and traces.</p>
        <div className="mt-6 flex justify-center gap-2">
          <Button size="md" variant="primary" as="a" href="/signin">Sign in</Button>
          <Button size="md" variant="ghost" as="a" href="/signup">Create account</Button>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-[1320px] mx-auto px-6 lg:px-8 py-10">
      <ProfileHero user={user} onSignOut={handleSignOut} />

      <div className="mt-8 grid lg:grid-cols-[220px_1fr] gap-8">
        <aside className="lg:sticky lg:top-20 lg:self-start">
          <nav className="flex lg:flex-col gap-0.5 overflow-x-auto">
            {TABS.map(([id, label, ic]) => (
              <button key={id} onClick={() => setTab(id)}
                className={`flex items-center gap-2.5 px-3 h-9 rounded-md text-[13.5px] whitespace-nowrap transition-colors ${
                  tab === id
                    ? 'bg-ink-100 dark:bg-ink-900 text-ink-950 dark:text-white font-medium'
                    : 'text-ink-600 dark:text-ink-400 hover:text-ink-950 dark:hover:text-white'
                }`}>
                <Icon name={ic} size={14} />{label}
              </button>
            ))}
          </nav>
        </aside>

        <div className="min-w-0">
          {tab === 'overview'  && <ProfileOverview user={user} />}
          {tab === 'traces'    && <ProfileTraces />}
          {tab === 'security'  && <ProfileSecurity user={user} />}
          {tab === 'keys'      && <ProfileKeys />}
          {tab === 'sessions'  && <ProfileSessions />}
          {tab === 'billing'   && <ProfileBilling />}
          {tab === 'prefs'     && <ProfilePrefs />}
        </div>
      </div>
    </div>
  )
}
