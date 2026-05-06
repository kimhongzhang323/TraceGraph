export function SectionLabel({ children, num, className = '' }: { children: React.ReactNode; num?: string; className?: string }) {
  return (
    <div className={`mono text-[11px] uppercase tracking-[0.16em] text-ink-500 ${className}`}>
      {num && <span className="text-ink-400 mr-2">/ {num}</span>}
      {children}
    </div>
  )
}
