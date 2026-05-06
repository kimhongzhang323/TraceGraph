interface CodeBlockProps {
  children: string
  filename?: string
  language?: string
}

export function CodeBlock({ children, filename, language = 'java' }: CodeBlockProps) {
  return (
    <div className="rounded-2xl border hairline overflow-hidden bg-ink-950 dark:bg-black/40">
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/10">
        <span className="mono text-[11px] text-white/50">{filename ?? ''}</span>
        <span className="mono text-[10px] text-white/30 uppercase tracking-wider">{language}</span>
      </div>
      <pre
        className="code-block p-5 overflow-x-auto scroll-thin text-white/85"
        dangerouslySetInnerHTML={{ __html: children }}
      />
    </div>
  )
}
