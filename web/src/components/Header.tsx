import { Button } from './Button'
import { Icon } from './Icon'

const NAV = [
  { id: 'home',      href: '/',          label: 'Overview' },
  { id: 'docs',      href: '/docs',      label: 'Docs' },
  { id: 'trace',     href: '/trace',     label: 'Trace' },
  { id: 'studio',    href: '/studio',    label: 'Studio' },
  { id: 'api',       href: '/api',       label: 'API' },
  { id: 'changelog', href: '/changelog', label: 'Changelog' },
]

interface HeaderProps {
  route: string
  theme: 'light' | 'dark'
  setTheme: (t: 'light' | 'dark') => void
}

export function Header({ route, theme, setTheme }: HeaderProps) {
  return (
    <header className="sticky top-0 z-50 backdrop-blur-md bg-white/75 dark:bg-ink-950/75 border-b hairline">
      <div className="max-w-[1500px] mx-auto px-6 lg:px-8 h-14 flex items-center gap-6">
        <a href="/" className="flex items-center gap-2.5">
          <Logo />
          <span className="font-medium tracking-tight text-ink-950 dark:text-white text-[15px]">TraceGraph</span>
          <span className="mono text-[10.5px] text-ink-500 px-1.5 h-[18px] inline-flex items-center rounded bg-ink-100 dark:bg-ink-900">v0.3.0</span>
        </a>

        <nav className="hidden md:flex items-center gap-1 ml-2">
          {NAV.map((n) => (
            <a key={n.id} href={n.href}
               className={`px-3 h-8 inline-flex items-center rounded-md text-[13px] transition-colors ${
                 route === n.id
                   ? 'text-ink-950 dark:text-white bg-ink-100 dark:bg-ink-900'
                   : 'text-ink-600 dark:text-ink-400 hover:text-ink-950 dark:hover:text-white'
               }`}>{n.label}</a>
          ))}
        </nav>

        <div className="flex-1" />

        <div className="flex items-center gap-2">
          <button
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="w-8 h-8 rounded-md inline-flex items-center justify-center text-ink-600 dark:text-ink-400 hover:bg-ink-100 dark:hover:bg-ink-900"
            aria-label="Toggle theme">
            <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={15} />
          </button>
          <a href="https://github.com/tracegraph/tracegraph"
             className="w-8 h-8 rounded-md inline-flex items-center justify-center text-ink-600 dark:text-ink-400 hover:bg-ink-100 dark:hover:bg-ink-900"
             aria-label="GitHub" target="_blank" rel="noreferrer">
            <Icon name="github" size={15} />
          </a>
          <Button as="a" href="/docs" size="sm" variant="primary" className="hidden sm:inline-flex">Quickstart</Button>
        </div>
      </div>
    </header>
  )
}

function Logo() {
  return (
    <svg viewBox="0 0 28 28" className="w-6 h-6 text-ink-950 dark:text-white" fill="none">
      <circle cx="6"  cy="7"  r="2.4" fill="currentColor" />
      <circle cx="22" cy="7"  r="2.4" fill="currentColor" />
      <circle cx="14" cy="14" r="2.4" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="6"  cy="21" r="2.4" stroke="currentColor" strokeWidth="1.6" />
      <circle cx="22" cy="21" r="2.4" stroke="currentColor" strokeWidth="1.6" />
      <path d="M6 9.4 L13 12.4 M22 9.4 L15 12.4 M13 15.6 L6 18.6 M15 15.6 L22 18.6" stroke="currentColor" strokeWidth="1.4" />
    </svg>
  )
}
