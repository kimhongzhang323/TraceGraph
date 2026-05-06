import { StatusDot } from './StatusDot'

export function Footer() {
  return (
    <footer className="border-t hairline bg-white dark:bg-ink-950">
      <div className="max-w-[1500px] mx-auto px-6 lg:px-8 py-12 grid grid-cols-2 md:grid-cols-5 gap-8">
        <div className="col-span-2">
          <p className="mt-4 text-[13.5px] text-ink-600 dark:text-ink-400 max-w-xs leading-relaxed">
            Typed execution-graph runtime for the JVM. Apache 2.0.
          </p>
          <div className="mt-5 flex items-center gap-2 mono text-[11px] text-ink-500">
            <StatusDot tone="ok" />
            All systems operational
          </div>
        </div>
        {[
          {title:'Product', items:[['Docs','/docs'],['Trace explorer','/trace'],['Graph studio','/studio'],['Changelog','/changelog']] as [string,string][]},
          {title:'Reference',items:[['Quickstart','/docs'],['REST API','/api'],['Javadoc','#'],['Examples','#']] as [string,string][]},
          {title:'Project',  items:[['GitHub','https://github.com/kimhongzhang323/TraceGraph'],['Maven Central','#'],['License — Apache 2.0','#']] as [string,string][]},
        ].map((col) => (
          <div key={col.title}>
            <h5 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mb-3">{col.title}</h5>
            <ul className="space-y-2">
              {col.items.map(([label, href]) => (
                <li key={label}>
                  <a href={href} className="text-[13.5px] text-ink-600 dark:text-ink-400 hover:text-ink-950 dark:hover:text-white">{label}</a>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t hairline">
        <div className="max-w-[1500px] mx-auto px-6 lg:px-8 py-5 flex items-center justify-between mono text-[11px] text-ink-500">
          <span>© 2026 TraceGraph contributors · Apache-2.0</span>
          <span>v0.3.0 · jdk-21+</span>
        </div>
      </div>
    </footer>
  )
}
