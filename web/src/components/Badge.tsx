type Tone = 'neutral' | 'ok' | 'warn' | 'err' | 'accent' | 'dark'

const tones: Record<Tone, string> = {
  neutral: 'bg-ink-100 text-ink-700 dark:bg-ink-900 dark:text-ink-300',
  ok:      'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-200',
  warn:    'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-200',
  err:     'bg-rose-50 text-rose-700 dark:bg-rose-900/30 dark:text-rose-200',
  accent:  'bg-accent-50 text-accent-700 dark:bg-accent-700/30 dark:text-accent-100',
  dark:    'bg-ink-950 text-white dark:bg-white dark:text-ink-950',
}

export function Badge({ children, tone = 'neutral' }: { children: React.ReactNode; tone?: Tone }) {
  return (
    <span className={`mono inline-flex items-center gap-1 text-[10.5px] px-1.5 h-[18px] rounded uppercase tracking-wider ${tones[tone]}`}>
      {children}
    </span>
  )
}
