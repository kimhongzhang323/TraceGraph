type Tone = 'ok' | 'warn' | 'err' | 'neutral'

const colors: Record<Tone, string> = {
  ok:      'bg-emerald-500',
  warn:    'bg-amber-500',
  err:     'bg-rose-500',
  neutral: 'bg-ink-400',
}

export function StatusDot({ tone = 'ok', pulse = false }: { tone?: Tone; pulse?: boolean }) {
  return (
    <span className={`inline-block w-1.5 h-1.5 rounded-full ${colors[tone]} ${pulse ? 'pulse-dot' : ''}`} />
  )
}
