import { CodeBlock, Code } from '@/components'
import { Seo } from '@/components/Seo'
import { API_GROUPS } from '@/data/mock'
import type { ApiEndpoint } from '@/types'

const methodColor: Record<string, string> = {
  GET:    'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-200',
  POST:   'bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200',
  DELETE: 'bg-rose-50 text-rose-700 dark:bg-rose-900/30 dark:text-rose-200',
  PUT:    'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-200',
}

function Endpoint({ ep, first }: { ep: ApiEndpoint; first: boolean }) {
  return (
    <div className={`grid grid-cols-[80px_1fr] gap-4 px-5 py-4 items-start ${!first ? 'border-t hairline' : ''}`}>
      <span className={`mono text-[11px] uppercase tracking-wider px-2 h-6 inline-flex items-center rounded ${methodColor[ep.m] ?? 'bg-ink-100 text-ink-700'}`}>{ep.m}</span>
      <div className="min-w-0">
        <div className="mono text-[13.5px] text-ink-950 dark:text-white">{ep.p}</div>
        <p className="mt-1 text-[13.5px] text-ink-600 dark:text-ink-400">{ep.desc}</p>
        <div className="mt-2 mono text-[11.5px] text-ink-500">↳ returns <span className="text-ink-700 dark:text-ink-300">{ep.returns}</span></div>
      </div>
    </div>
  )
}

const curlExample = `# Fork run e9c4f1a2 from step 2 against the current graph
$ curl -X POST "http://localhost:8082/tracegraph/traces/e9c4f1a2/replay?step=2"
{
  "executionId": "f1d92cb0-...",
  "forkedFromExecutionId": "e9c4f1a2-...",
  "forkedFromStepIndex": 2,
  "status": "COMPLETED"
}`

export function ApiReference() {
  return (
    <div className="max-w-[1100px] mx-auto px-6 lg:px-8 py-16 fade-up">
      <Seo
        title="API reference"
        description="REST endpoints exposed by the TraceGraph Spring Boot starter for traces, replay, streaming, and graph inspection."
        path="/api"
      />
      <div className="mono text-[11px] uppercase tracking-[0.14em] text-ink-500 mb-4">REFERENCE / REST API</div>
      <h1 className="display-tight text-[56px] text-ink-950 dark:text-white">API reference</h1>
      <p className="mt-4 text-[16px] text-ink-600 dark:text-ink-400 max-w-2xl">
        Endpoints exposed by <Code>tracegraph-spring-boot-starter</Code> when <Code>tracegraph.web.enabled=true</Code>.
        All paths are relative to your application context root. Responses are JSON unless noted.
      </p>
      <div className="mt-12 space-y-14">
        {API_GROUPS.map((grp) => (
          <section key={grp.group}>
            <h2 className="text-[28px] text-ink-950 dark:text-white tracking-tight font-medium mb-5">{grp.group}</h2>
            <div className="rounded-2xl border hairline overflow-hidden bg-white dark:bg-ink-950">
              {grp.endpoints.map((ep, i) => <Endpoint key={i} ep={ep} first={i === 0} />)}
            </div>
          </section>
        ))}
      </div>
      <section className="mt-16">
        <h2 className="text-[28px] text-ink-950 dark:text-white tracking-tight font-medium mb-4">Example: fork from a step</h2>
        <CodeBlock filename="curl" language="bash">{curlExample}</CodeBlock>
      </section>
    </div>
  )
}
