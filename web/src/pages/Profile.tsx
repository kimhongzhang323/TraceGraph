import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/Button'
import { Icon } from '@/components/Icon'
import { Badge } from '@/components/Badge'
import { StatusDot } from '@/components/StatusDot'
import { useAuth } from '@/hooks/useAuth'
import type { AuthUser } from '@/hooks/useAuth'

const RECENT_TRACES = [
  { id: 'e9c4f1a2', graph: 'order-pipeline',  status: 'failed',      dur: 1041, when: '12m ago',   steps: 5,  fork: '-' },
  { id: 'a73d2eb5', graph: 'order-pipeline',  status: 'completed',   dur: 842,  when: '2h ago',    steps: 5,  fork: '-' },
  { id: 'b1f9c022', graph: 'rag-agent',       status: 'completed',   dur: 1812, when: '5h ago',    steps: 8,  fork: '4c7e' },
  { id: '4c7e8d11', graph: 'react-agent',     status: 'interrupted', dur: 6240, when: 'yesterday', steps: 12, fork: '-' },
  { id: 'd8a44b06', graph: 'order-pipeline',  status: 'completed',   dur: 1104, when: '2 days ago',steps: 5,  fork: '-' },
]

const API_KEYS = [
  { name: 'prod · ingestion',   prefix: 'tg_live_7a4f…b21c', created: '2026-03-12', used: '12m ago',   scope: 'read+write' },
  { name: 'staging · ci',       prefix: 'tg_test_91c2…04ef', created: '2026-04-02', used: '2h ago',    scope: 'read+write' },
  { name: 'local · dev laptop', prefix: 'tg_test_3b80…ff10', created: '2026-04-21', used: 'yesterday', scope: 'read' },
]

const SESSIONS = [
  { device: 'MacBook Pro 14"',   browser: 'Chrome 124 · macOS 15.4',  loc: 'Singapore · SG', ip: '203.116.42.18', when: 'active now', current: true },
  { device: 'iPhone 16 Pro',     browser: 'Safari · iOS 18.4',        loc: 'Singapore · SG', ip: '203.116.42.18', when: '1h ago' },
  { device: 'Linux workstation', browser: 'Firefox 126 · Ubuntu 24',  loc: 'Bengaluru · IN', ip: '49.207.182.4',  when: '3 days ago' },
]

const AUDIT = [
  { t: '12m ago',    what: 'Forked execution e9c4f1a2 from step 3', ip: '203.116.42.18' },
  { t: '1h ago',     what: 'Created API key prod · ingestion',      ip: '203.116.42.18' },
  { t: 'yesterday',  what: 'Enabled hardware-key MFA',              ip: '203.116.42.18' },
  { t: '2 days ago', what: 'Rotated personal access token',         ip: '203.116.42.18' },
  { t: '3 days ago', what: 'Updated email preferences',             ip: '49.207.182.4' },
]

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

function statusTone(s: string): 'ok' | 'err' | 'warn' | 'neutral' {
  if (s === 'completed') return 'ok'
  if (s === 'failed') return 'err'
  if (s === 'interrupted') return 'warn'
  return 'neutral'
}

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
          <span>·</span>
          <span>{user.org ?? 'Personal'}</span>
          <span>·</span>
          <span>joined {user.joined}</span>
          <span>·</span>
          <span>via {user.provider}</span>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <Button size="sm" variant="ghost" icon="log-out" onClick={onSignOut}>Sign out</Button>
      </div>
    </div>
  )
}

function ProfileOverview({ user }: { user: AuthUser }) {
  return (
    <div className="space-y-6">
      <div className="grid sm:grid-cols-4 gap-3">
        {([
          ['Total runs',   '1,284', '+38 this week'],
          ['Replay forks', '312',   '+5 this week'],
          ['Failed runs',  '6.2%',  '-1.4% vs prior'],
          ['Tokens used',  '2.4M',  'p50 412 / call'],
        ] as const).map(([k, v, sub]) => (
          <div key={k} className="rounded-xl border hairline bg-white dark:bg-ink-950 p-4">
            <div className="mono text-[10.5px] uppercase tracking-wider text-ink-500">{k}</div>
            <div className="text-[26px] font-medium tracking-tight text-ink-950 dark:text-white mt-1.5">{v}</div>
            <div className="mono text-[11px] text-ink-500 mt-1">{sub}</div>
          </div>
        ))}
      </div>

      <SectionCard title="Recent activity">
        <ul className="divide-y hairline">
          {AUDIT.map((a, i) => (
            <li key={i} className="flex items-start justify-between px-5 py-3 gap-6">
              <span className="text-[13px] text-ink-700 dark:text-ink-300 flex-1">{a.what}</span>
              <span className="mono text-[11px] text-ink-500 shrink-0">{a.t} · {a.ip}</span>
            </li>
          ))}
        </ul>
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

function ProfileTraces() {
  return (
    <SectionCard title="My traces" action={<a href="/trace" className="mono text-[11.5px] text-ink-500 hover:text-ink-950 dark:hover:text-white">Open explorer →</a>}>
      <div className="overflow-x-auto">
        <table className="w-full text-[12.5px]">
          <thead>
            <tr className="border-b hairline">
              {['ID', 'Graph', 'Status', 'Duration', 'Steps', 'Fork', 'When'].map((h) => (
                <th key={h} className="text-left px-5 py-2.5 mono text-[10.5px] uppercase tracking-wider text-ink-500 font-normal">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y hairline">
            {RECENT_TRACES.map((r) => (
              <tr key={r.id} className="hover:bg-ink-50 dark:hover:bg-ink-900/50 transition-colors">
                <td className="px-5 py-3 mono text-ink-950 dark:text-white">{r.id}</td>
                <td className="px-5 py-3 text-ink-700 dark:text-ink-300">{r.graph}</td>
                <td className="px-5 py-3">
                  <Badge tone={statusTone(r.status)}><StatusDot tone={statusTone(r.status)} />{r.status}</Badge>
                </td>
                <td className="px-5 py-3 mono text-ink-500">{r.dur}ms</td>
                <td className="px-5 py-3 mono text-ink-500">{r.steps}</td>
                <td className="px-5 py-3 mono text-ink-500">{r.fork}</td>
                <td className="px-5 py-3 mono text-ink-500">{r.when}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </SectionCard>
  )
}

function ProfileSecurity({ user }: { user: AuthUser }) {
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
        <ul className="divide-y hairline">
          {AUDIT.map((a, i) => (
            <li key={i} className="flex items-start justify-between px-5 py-3 gap-4">
              <span className="text-[13px] text-ink-700 dark:text-ink-300 flex-1">{a.what}</span>
              <span className="mono text-[11px] text-ink-500 shrink-0">{a.t}</span>
            </li>
          ))}
        </ul>
      </SectionCard>

      <SectionCard title="Danger zone">
        <div className="p-5 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-[13.5px] text-ink-950 dark:text-white">Change password</div>
              <div className="text-[12px] text-ink-500 mt-0.5">Last changed: never (OAuth account)</div>
            </div>
            <Button size="sm" variant="ghost">Change</Button>
          </div>
          <div className="flex items-center justify-between pt-3 border-t hairline">
            <div>
              <div className="text-[13.5px] text-rose-600 dark:text-rose-400">Delete account</div>
              <div className="text-[12px] text-ink-500 mt-0.5">Permanently deletes all traces and data.</div>
            </div>
            <Button size="sm" variant="ghost" className="!text-rose-600 dark:!text-rose-400 !border-rose-200 dark:!border-rose-800">Delete</Button>
          </div>
        </div>
      </SectionCard>
    </div>
  )
}

function ProfileKeys() {
  return (
    <SectionCard title="API keys" action={<Button size="sm" variant="primary" icon="plus">New key</Button>}>
      <div className="divide-y hairline">
        {API_KEYS.map((k, i) => (
          <div key={i} className="px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
            <div>
              <div className="text-[13.5px] font-medium text-ink-950 dark:text-white">{k.name}</div>
              <div className="mono text-[11.5px] text-ink-500 mt-1">{k.prefix} · {k.scope} · created {k.created}</div>
            </div>
            <div className="flex items-center gap-3">
              <span className="mono text-[11px] text-ink-500">last used {k.used}</span>
              <Button size="sm" variant="ghost" icon="trash-2" className="!text-rose-600 dark:!text-rose-400">Revoke</Button>
            </div>
          </div>
        ))}
      </div>
    </SectionCard>
  )
}

function ProfileSessions() {
  return (
    <SectionCard title="Active sessions" action={<Button size="sm" variant="ghost" className="!text-rose-600 dark:!text-rose-400">Revoke all others</Button>}>
      <div className="divide-y hairline">
        {SESSIONS.map((s, i) => (
          <div key={i} className="px-5 py-4 flex items-center justify-between gap-4 flex-wrap">
            <div className="flex items-start gap-3">
              <Icon name="monitor" size={16} className="text-ink-500 mt-0.5 shrink-0" />
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-[13.5px] font-medium text-ink-950 dark:text-white">{s.device}</span>
                  {s.current && <Badge tone="ok"><StatusDot tone="ok" />current</Badge>}
                </div>
                <div className="mono text-[11.5px] text-ink-500 mt-0.5">{s.browser} · {s.loc} · {s.ip}</div>
              </div>
            </div>
            <div className="flex items-center gap-3">
              <span className="mono text-[11px] text-ink-500">{s.when}</span>
              {!s.current && <Button size="sm" variant="ghost" className="!text-rose-600 dark:!text-rose-400">Revoke</Button>}
            </div>
          </div>
        ))}
      </div>
    </SectionCard>
  )
}

function ProfileBilling({ user }: { user: AuthUser }) {
  return (
    <div className="space-y-4">
      <SectionCard title="Current plan">
        <div className="p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <div className="text-[20px] font-medium tracking-tight text-ink-950 dark:text-white">{user.plan}</div>
              <div className="text-[13px] text-ink-500 mt-0.5">
                {user.plan === 'Free' ? '50k trace events / month' : '5M trace events / month'}
              </div>
            </div>
            <Button size="sm" variant="primary">Upgrade</Button>
          </div>
          <div className="rounded-xl border hairline overflow-hidden">
            {([
              ['Trace events', user.plan === 'Free' ? '12,483 / 50,000' : '1.2M / 5M'],
              ['Replay forks', user.plan === 'Free' ? 'Unlimited' : 'Unlimited'],
              ['JDBC trace store', 'Included'],
              ['JSON file store', 'Included'],
            ] as const).map(([k, v], i) => (
              <div key={k} className={`flex justify-between px-4 py-2.5 text-[13px] ${i > 0 ? 'border-t hairline' : ''}`}>
                <span className="text-ink-600 dark:text-ink-400">{k}</span>
                <span className="text-ink-950 dark:text-white mono text-[12px]">{v}</span>
              </div>
            ))}
          </div>
        </div>
      </SectionCard>
    </div>
  )
}

function ProfilePrefs() {
  const [emailDigest, setEmailDigest] = useState(true)
  const [failureAlerts, setFailureAlerts] = useState(true)
  const [betaFeatures, setBetaFeatures] = useState(false)

  return (
    <SectionCard title="Preferences">
      <div className="divide-y hairline">
        {([
          ['Weekly digest email', 'Summary of runs, replays, and usage.', emailDigest, setEmailDigest],
          ['Failure alerts', 'Email me when a run fails in production.', failureAlerts, setFailureAlerts],
          ['Beta features', 'Early access to experimental features.', betaFeatures, setBetaFeatures],
        ] as [string, string, boolean, (v: boolean) => void][]).map(([label, desc, val, set]) => (
          <div key={label} className="flex items-center justify-between px-5 py-4">
            <div>
              <div className="text-[13.5px] text-ink-950 dark:text-white">{label}</div>
              <div className="text-[12px] text-ink-500 mt-0.5">{desc}</div>
            </div>
            <button
              onClick={() => set(!val)}
              className={`relative w-10 h-5.5 rounded-full transition-colors ${val ? 'bg-emerald-500' : 'bg-ink-200 dark:bg-ink-800'}`}
              style={{ width: 40, height: 22 }}>
              <span
                className="absolute top-0.5 left-0.5 w-4.5 h-4.5 rounded-full bg-white shadow transition-transform"
                style={{ width: 18, height: 18, transform: val ? 'translateX(18px)' : 'translateX(0)' }} />
            </button>
          </div>
        ))}
      </div>
    </SectionCard>
  )
}

const TABS = [
  ['overview', 'Overview',     'layout-dashboard'],
  ['traces',   'My traces',    'git-branch'],
  ['security', 'Security',     'shield'],
  ['keys',     'API keys',     'key'],
  ['sessions', 'Sessions',     'monitor'],
  ['billing',  'Billing',      'credit-card'],
  ['prefs',    'Preferences',  'sliders-horizontal'],
] as const

type TabId = (typeof TABS)[number][0]

export function Profile() {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState<TabId>('overview')

  const handleSignOut = () => {
    signOut()
    navigate('/')
  }

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
                <Icon name={ic} size={14} />
                {label}
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
          {tab === 'billing'   && <ProfileBilling user={user} />}
          {tab === 'prefs'     && <ProfilePrefs />}
        </div>
      </div>
    </div>
  )
}
