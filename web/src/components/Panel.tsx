interface PanelProps {
  title: string
  action?: React.ReactNode
  children: React.ReactNode
  className?: string
}

export function Panel({ title, action, children, className = '' }: PanelProps) {
  return (
    <div className={`rounded-xl border hairline bg-white dark:bg-ink-950 flex flex-col min-h-0 overflow-hidden ${className}`}>
      <div className="px-4 py-2.5 border-b hairline flex items-center justify-between bg-ink-50/50 dark:bg-ink-900/40">
        <span className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500">{title}</span>
        <div className="flex items-center gap-1">{action}</div>
      </div>
      <div className="flex-1 overflow-auto scroll-thin min-h-0">{children}</div>
    </div>
  )
}
