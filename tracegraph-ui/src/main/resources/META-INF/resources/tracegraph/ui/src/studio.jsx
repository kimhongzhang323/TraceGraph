// Graph studio — design / inspect graphs, similar layout to trace explorer.

const Studio = () => {
  const [active, setActive] = React.useState('charge');
  const [mermaid, setMermaid] = React.useState(null);
  const [complexity, setComplexity] = React.useState(null);

  React.useEffect(() => {
    fetch('/tracegraph/ui/graph').then(r => r.ok ? r.text() : null).then(t => t && setMermaid(t)).catch(() => {});
    fetch('/tracegraph/ui/complexity').then(r => r.ok ? r.json() : null).then(d => d && setComplexity(d)).catch(() => {});
  }, []);

  return (
    <div className="max-w-[1500px] mx-auto px-4 lg:px-6 py-6 fade-up">
      <div className="rounded-xl border hairline bg-white dark:bg-ink-950 px-4 py-2.5 flex items-center gap-3 mono text-[12px] text-ink-700 dark:text-ink-300 flex-wrap">
        <Icon name="workflow" size={14} className="text-ink-500" />
        <span className="text-ink-950 dark:text-white">graph · order-pipeline.java</span>
        <span className="text-ink-300 dark:text-ink-700">·</span>
        {complexity
          ? <span>{complexity.nodeCount} nodes · {complexity.edgeCount} edges · depth {complexity.maxDepth}</span>
          : <span>5 nodes · 4 edges · 1 entry · 1 terminal</span>}
        <span className="text-ink-300 dark:text-ink-700">·</span>
        <Badge tone="ok">VALID</Badge>
        <div className="flex-1" />
        <select className="mono text-[12px] px-2.5 h-8 rounded-lg border hairline bg-ink-50 dark:bg-ink-900 text-ink-950 dark:text-white">
          <option>order-pipeline</option>
          <option>rag-agent</option>
          <option>react-agent</option>
          <option>hitl-approval</option>
        </select>
        <Button size="sm" variant="ghost" icon="file-code" onClick={() => mermaid && alert(mermaid)}>Mermaid</Button>
        <Button size="sm" variant="ghost" icon="file-text">PlantUML</Button>
        <Button size="sm" variant="primary" icon="play">Run</Button>
      </div>

      <div className="mt-4 grid grid-cols-1 lg:grid-cols-[260px_1fr_320px] gap-3 h-[calc(100vh-200px)] min-h-[640px]">
        <Panel title="Nodes">
          <div className="py-1.5">
            {window.STUDIO_NODES.map(n => (
              <button key={n.name} onClick={() => setActive(n.name)}
                      className={`w-full text-left grid grid-cols-[20px_1fr_auto] gap-2 items-center px-3.5 py-2.5 border-l-2 transition-colors ${
                        active === n.name
                          ? 'bg-accent-50 dark:bg-accent-700/15 border-accent-500'
                          : 'border-transparent hover:bg-ink-50 dark:hover:bg-ink-900/60'
                      }`}>
                <Icon name={n.kind === 'parallel' ? 'split' : n.kind === 'async' ? 'zap' : 'circle'} size={12} className="text-ink-500" />
                <span className="min-w-0">
                  <span className="mono text-[12.5px] text-ink-950 dark:text-white">{n.name}</span>
                  <span className="flex items-center gap-1 mt-0.5">
                    {n.entry && <Badge tone="dark">entry</Badge>}
                    {n.terminal && <Badge tone="dark">terminal</Badge>}
                    <Badge tone="neutral">{n.kind}</Badge>
                  </span>
                </span>
                <span className="mono text-[10.5px] text-ink-500">{n.kind}</span>
              </button>
            ))}
          </div>
        </Panel>

        <Panel title="Canvas"
               action={<>
                 <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="layout-grid" size={12} /></button>
                 <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="zoom-in" size={12} /></button>
                 <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="zoom-out" size={12} /></button>
                 <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="maximize-2" size={12} /></button>
               </>}>
          <div className="grid-bg h-full p-2">
            <StudioGraphSvg active={active} setActive={setActive} />
          </div>
        </Panel>

        <Panel title={`node · ${active}`}>
          <StudioProps active={active} />
        </Panel>
      </div>
    </div>
  );
};

const StudioGraphSvg = ({ active, setActive }) => {
  const layout = window.NODE_LAYOUT;
  const edges = window.EDGES;
  return (
    <svg viewBox="0 0 760 580" preserveAspectRatio="xMidYMid meet" className="w-full h-full text-ink-950 dark:text-white">
      <defs>
        <marker id="st-arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="currentColor" /></marker>
      </defs>

      <rect x={layout.validate.x+15} y="30" width={layout.validate.w-30} height="22" rx="4" fill="currentColor" />
      <text x={layout.validate.x + layout.validate.w/2} y="45" textAnchor="middle" fill="white" className="mono" fontSize="10">▼ entry</text>

      {edges.map((e, i) => {
        const a = layout[e.from], b = layout[e.to];
        const x1 = a.x + a.w/2, y1 = a.y + a.h;
        const x2 = b.x + b.w/2, y2 = b.y;
        const mx = (x1+x2)/2, my = (y1+y2)/2;
        return (
          <g key={i}>
            <path d={`M ${x1} ${y1} C ${x1} ${y1+30}, ${x2} ${y2-30}, ${x2} ${y2}`}
                  fill="none" stroke="currentColor" strokeWidth="1.4" markerEnd="url(#st-arr)" />
            {e.label && (
              <g>
                <rect x={mx-32} y={my-10} width="64" height="20" rx="4" fill="white" stroke="currentColor" strokeOpacity="0.3" className="dark:fill-ink-900" />
                <text x={mx} y={my+4} textAnchor="middle" className="mono fill-ink-700 dark:fill-ink-300" fontSize="10">{e.label}</text>
              </g>
            )}
          </g>
        );
      })}

      {Object.entries(layout).map(([name, n]) => {
        const isActive = name === active;
        return (
          <g key={name} style={{ cursor: 'pointer' }} onClick={() => setActive(name)}>
            <rect x={n.x} y={n.y} width={n.w} height={n.h} rx="10"
                  fill={isActive ? 'rgba(13,143,99,0.10)' : 'white'}
                  stroke={isActive ? '#0d8f63' : 'currentColor'}
                  strokeWidth={isActive ? 2.2 : 1.4}
                  className={isActive ? '' : 'dark:fill-ink-900'} />
            <text x={n.x+n.w/2} y={n.y+22} textAnchor="middle" className="mono fill-ink-950 dark:fill-white" fontSize="13" fontWeight="500">{name}</text>
            <text x={n.x+n.w/2} y={n.y+39} textAnchor="middle" className="mono fill-ink-500" fontSize="10">{n.kind}</text>
          </g>
        );
      })}

      <line x1={layout.ship.x+layout.ship.w/2} y1={layout.ship.y+layout.ship.h} x2={layout.ship.x+layout.ship.w/2} y2={layout.ship.y+layout.ship.h+24} stroke="currentColor" strokeWidth="1.4" />
      <rect x={layout.ship.x+15} y={layout.ship.y+layout.ship.h+24} width={layout.ship.w-30} height="22" rx="4" fill="currentColor" />
      <text x={layout.ship.x+layout.ship.w/2} y={layout.ship.y+layout.ship.h+39} textAnchor="middle" fill="white" className="mono" fontSize="10">▲ terminal</text>
    </svg>
  );
};

const StudioProps = ({ active }) => {
  const n = window.STUDIO_NODES.find(x => x.name === active);
  const incoming = window.EDGES.filter(e => e.to === n.name);
  const outgoing = window.EDGES.filter(e => e.from === n.name);
  const Field = ({ k, v }) => (
    <div className="grid grid-cols-[100px_1fr] gap-3 py-2 border-t hairline first:border-t-0 text-[12.5px]">
      <span className="mono text-[11px] text-ink-500">{k}</span>
      <span className="mono text-[12px] text-ink-950 dark:text-white break-all">{v}</span>
    </div>
  );
  return (
    <div className="p-4 pb-6">
      <Field k="name" v={n.name} />
      <Field k="kind" v={n.kind} />
      <Field k="function" v={n.fn} />
      <Field k="retry" v={n.retry} />
      <Field k="entry" v={n.entry ? 'true' : 'false'} />
      <Field k="terminal" v={n.terminal ? 'true' : 'false'} />

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-6 mb-2">Incoming</h4>
      <div className="rounded-lg bg-ink-50 dark:bg-ink-900 p-3 mono text-[11.5px]">
        {incoming.length === 0 ? <span className="text-ink-500">(entry — no incoming)</span>
          : incoming.map((e, i) => <div key={i} className="text-ink-700 dark:text-ink-300">← <span className="text-emerald-600 dark:text-emerald-400">{e.from}</span>{e.label ? ' if ('+e.label+')' : ''}</div>)}
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Outgoing</h4>
      <div className="rounded-lg bg-ink-50 dark:bg-ink-900 p-3 mono text-[11.5px]">
        {outgoing.length === 0 ? <span className="text-ink-500">(terminal — no outgoing)</span>
          : outgoing.map((e, i) => <div key={i} className="text-ink-700 dark:text-ink-300">→ <span className="text-emerald-600 dark:text-emerald-400">{e.to}</span>{e.label ? ' if ('+e.label+')' : ''}</div>)}
      </div>

      <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">Builder</h4>
      <pre className="rounded-lg bg-ink-950 dark:bg-black/60 border border-white/5 p-3 mono text-[11px] text-white/85 overflow-x-auto scroll-thin">
{`.${n.kind === 'parallel' ? 'parallel' : n.kind === 'async' ? 'asyncNode' : 'node'}("${n.name}", ${n.fn === 'List.of(profile, fraud, inv)' ? 'List.of(...)' : n.fn}${n.retry !== '—' ? ',\n    RetryPolicy.exponential(...)' : ''})`}</pre>
    </div>
  );
};

window.Studio = Studio;
