export function Code({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <code className={`mono text-[0.92em] px-1.5 py-0.5 rounded bg-ink-100 dark:bg-ink-900 text-ink-800 dark:text-ink-200 ${className}`}>
      {children}
    </code>
  )
}
