// Trace Explorer — interactive 3-pane debugger.

const TraceExplorer = () => {
  const [activeIdx, setActiveIdx] = React.useState(0);
  const [liveIds, setLiveIds] = React.useState([]);

  React.useEffect(() => {
    fetch('/tracegraph/traces?limit=20')
      .then(r => r.ok ? r.json() : null)
      .then(d => { if (d && d.items) setLiveIds(d.items); else if (Array.isArray(d)) setLiveIds(d); })
      .catch(() => {});
  }, []);

  const traceList = liveIds.length > 0
    ? liveIds.map(id => ({ id: typeof id === 'string' ? id : id.executionId, graph: id.graph || 'graph', status: id.status || 'COMPLETED' }))
    : window.TRACE_LIST;

  return (
    <div className="max-w-[1500px] mx-auto px-4 lg:px-6 py-6 fade-up">
      <TraceTopBar activeIdx={activeIdx} traceList={traceList} />
      <div className="mt-4 grid grid-cols-1 lg:grid-cols-[300px_1fr_360px] gap-3 h-[calc(100vh-260px)] min-h-[640px]">
        <StepListPanel activeIdx={activeIdx} setActiveIdx={setActiveIdx} />
        <GraphPanel activeIdx={activeIdx} setActiveIdx={setActiveIdx} />
        <InspectorPanel activeIdx={activeIdx} />
      </div>
      <TimelinePanel activeIdx={activeIdx} setActiveIdx={setActiveIdx} />
      <LogsPanel />
    </div>
  );
};

const TraceTopBar = ({ activeIdx, traceList }) => (
  <div className="rounded-xl border hairline bg-white dark:bg-ink-950 px-4 py-2.5 flex items-center gap-3 mono text-[12px] text-ink-700 dark:text-ink-300 flex-wrap">
    <Icon name="git-branch" size={14} className="text-ink-500" />
    <span className="text-ink-950 dark:text-white">execution {window.TRACE.executionId.slice(0,18)}…</span>
    <span className="text-ink-300 dark:text-ink-700">·</span>
    <Badge tone="err">FAILED</Badge>
    <span className="text-ink-300 dark:text-ink-700">·</span>
    <span>graph <strong className="text-ink-950 dark:text-white">{window.TRACE.graph}</strong></span>
    <span className="text-ink-300 dark:text-ink-700">·</span>
    <span>{window.TRACE.startedAt}</span>
    <span className="text-ink-300 dark:text-ink-700">·</span>
    <span>{window.TRACE.duration}ms · 5 steps · 1 retry · 1 fail</span>
    <div className="flex-1" />
    <select className="mono text-[12px] px-2.5 h-8 rounded-lg border hairline bg-ink-50 dark:bg-ink-900 text-ink-950 dark:text-white">
      {traceList.map(t => (
        <option key={t.id}>{t.id} · {t.graph} · {t.status}</option>
      ))}
    </select>
    <Button size="sm" variant="ghost" icon="git-compare">Diff with…</Button>
    <Button size="sm" variant="primary" icon="redo-2"
            onClick={() => alert(`Forking from #${String(activeIdx).padStart(2,'0')} · ${window.TRACE.steps[activeIdx].name}\n\nPOST /tracegraph/traces/${window.TRACE.executionId.slice(0,8)}/replay?step=${activeIdx}`)}>
      Fork from step
    </Button>
  </div>
);

const Panel = ({ title, action, children, className = '' }) => (
  <div className={`rounded-xl border hairline bg-white dark:bg-ink-950 flex flex-col min-h-0 overflow-hidden ${className}`}>
    <div className="px-4 py-2.5 border-b hairline flex items-center justify-between bg-ink-50/50 dark:bg-ink-900/40">
      <span className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500">{title}</span>
      <div className="flex items-center gap-1">{action}</div>
    </div>
    <div className="flex-1 overflow-auto scroll-thin min-h-0">{children}</div>
  </div>
);

const StepListPanel = ({ activeIdx, setActiveIdx }) => (
  <Panel title={`Steps · ${window.TRACE.steps.length}`}
         action={<>
           <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="filter" size={12} /></button>
           <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="search" size={12} /></button>
         </>}>
    <div className="py-1.5">
      {window.TRACE.steps.map(s => (
        <button key={s.i} onClick={() => setActiveIdx(s.i)}
                className={`w-full text-left grid grid-cols-[24px_1fr_auto] gap-2 items-center px-3.5 py-2.5 border-l-2 transition-colors ${
                  activeIdx === s.i
                    ? 'bg-accent-50 dark:bg-accent-700/15 border-accent-500'
                    : 'border-transparent hover:bg-ink-50 dark:hover:bg-ink-900/60'
                }`}>
          <span className={`mono text-[10.5px] ${activeIdx === s.i ? 'text-accent-700 dark:text-accent-100' : 'text-ink-400'}`}>#{String(s.i).padStart(2,'0')}</span>
          <span className="min-w-0">
            <span className={`mono text-[12.5px] block truncate ${s.status === 'err' ? 'text-rose-600 dark:text-rose-400' : 'text-ink-950 dark:text-white'}`}>{s.name}</span>
            <span className="flex items-center gap-1 mt-0.5">
              {s.status === 'parallel' && <Badge tone="neutral">∥ {s.branches}</Badge>}
              {s.async && <Badge tone="neutral">async</Badge>}
              {s.attempts > 1 && <Badge tone="warn">↻ {s.attempts}</Badge>}
              {s.status === 'err' && <Badge tone="err">FAIL</Badge>}
              {s.status === 'ok' && <Badge tone="ok">ok</Badge>}
            </span>
          </span>
          <span className="mono text-[10.5px] text-ink-500">{s.dur}ms</span>
        </button>
      ))}
    </div>
  </Panel>
);

const GraphPanel = ({ activeIdx, setActiveIdx }) => (
  <Panel title={`graph · ${window.TRACE.graph}`}
         action={<>
           <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="maximize-2" size={12} /></button>
           <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="zoom-in" size={12} /></button>
           <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="zoom-out" size={12} /></button>
         </>}>
    <div className="grid-bg h-full p-2">
      <TraceGraphSvg activeIdx={activeIdx} setActiveIdx={setActiveIdx} />
    </div>
  </Panel>
);

const TraceGraphSvg = ({ activeIdx, setActiveIdx }) => {
  const layout = window.NODE_LAYOUT;
  const edges = window.EDGES;
  const stepFor = name => window.TRACE.steps.find(s => s.name === name);

  const nodeStyle = (name) => {
    const step = stepFor(name);
    const isActive = step && step.i === activeIdx;
    if (!step) return { fill: '#fff', stroke: 'currentColor', sw: 1.4, sub: '' };
    if (step.status === 'err') return { fill: 'rgba(244,63,94,0.06)', stroke: '#e11d48', sw: isActive?2.4:1.8, sub: '✕ FAIL', subColor:'#e11d48' };
    if (step.status === 'retry') return { fill: 'rgba(245,158,11,0.07)', stroke: '#d97706', sw: isActive?2.4:1.6, sub: '↻ '+step.attempts, subColor:'#d97706' };
    if (step.status === 'parallel') return { fill: 'rgba(0,0,0,0.02)', stroke: 'currentColor', sw: isActive?2.4:1.4, sub: '∥ '+step.branches, subColor:'#6b7280' };
    if (step.status === 'ok') return { fill: 'rgba(13,143,99,0.08)', stroke: '#0d8f63', sw: isActive?2.4:1.6, sub: '✓ '+step.dur+'ms', subColor:'#0d8f63' };
    return { fill: '#fff', stroke: 'currentColor', sw: 1.4, sub: '' };
  };

  const edgeStroke = (e) => {
    const f = stepFor(e.from), t = stepFor(e.to);
    if (f && t) {
      if (f.status === 'err' || t.status === 'err') return '#e11d48';
      return '#0d8f63';
    }
    return 'currentColor';
  };

  return (
    <svg viewBox="0 0 760 580" preserveAspectRatio="xMidYMid meet" className="w-full h-full text-ink-950 dark:text-white">
      <defs>
        <marker id="tg-arr-acc" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#0d8f63" /></marker>
        <marker id="tg-arr-err" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#e11d48" /></marker>
      </defs>

      {/* entry chip */}
      <rect x={layout.validate.x+15} y="30" width={layout.validate.w-30} height="22" rx="4" fill="currentColor" />
      <text x={layout.validate.x + layout.validate.w/2} y="45" textAnchor="middle" fill="white" className="mono" fontSize="10">▼ entry</text>

      {/* edges */}
      {edges.map((e, i) => {
        const a = layout[e.from], b = layout[e.to];
        const x1 = a.x + a.w/2, y1 = a.y + a.h;
        const x2 = b.x + b.w/2, y2 = b.y;
        const mx = (x1+x2)/2, my = (y1+y2)/2;
        const stroke = edgeStroke(e);
        const marker = stroke === '#e11d48' ? 'tg-arr-err' : 'tg-arr-acc';
        return (
          <g key={i}>
            <path d={`M ${x1} ${y1} C ${x1} ${y1+30}, ${x2} ${y2-30}, ${x2} ${y2}`}
                  fill="none" stroke={stroke} strokeWidth="1.6" markerEnd={`url(#${marker})`} />
            {e.label && (
              <g>
                <rect x={mx-32} y={my-10} width="64" height="20" rx="4" fill="white" stroke={stroke} strokeOpacity="0.4" className="dark:fill-ink-950" />
                <text x={mx} y={my+4} textAnchor="middle" className="mono fill-ink-700 dark:fill-ink-300" fontSize="10">{e.label}</text>
              </g>
            )}
          </g>
        );
      })}

      {/* nodes */}
      {Object.entries(layout).map(([name, n]) => {
        const s = nodeStyle(name);
        return (
          <g key={name} style={{ cursor: 'pointer' }}
             onClick={() => { const st = stepFor(name); if (st) setActiveIdx(st.i); }}>
            <rect x={n.x} y={n.y} width={n.w} height={n.h} rx="10"
                  fill={s.fill} stroke={s.stroke} strokeWidth={s.sw}
                  className={s.fill === '#fff' ? 'dark:fill-ink-900' : ''} />
            <text x={n.x+n.w/2} y={n.y+22} textAnchor="middle" className="mono fill-ink-950 dark:fill-white" fontSize="13" fontWeight="500">{name}</text>
            <text x={n.x+n.w/2} y={n.y+39} textAnchor="middle" className="mono" fontSize="10" fill={s.subColor || '#6b7280'}>{n.kind}{s.sub ? ' · ' + s.sub : ''}</text>
          </g>
        );
      })}

      {/* terminal */}
      <line x1={layout.ship.x+layout.ship.w/2} y1={layout.ship.y+layout.ship.h} x2={layout.ship.x+layout.ship.w/2} y2={layout.ship.y+layout.ship.h+24} stroke="currentColor" strokeWidth="1.4" />
      <rect x={layout.ship.x+15} y={layout.ship.y+layout.ship.h+24} width={layout.ship.w-30} height="22" rx="4" fill="currentColor" />
      <text x={layout.ship.x+layout.ship.w/2} y={layout.ship.y+layout.ship.h+39} textAnchor="middle" fill="white" className="mono" fontSize="10">▲ terminal</text>
    </svg>
  );
};

const InspectorPanel = ({ activeIdx }) => {
  const step = window.TRACE.steps[activeIdx];
  const Field = ({ k, v, tone }) => (
    <div className="grid grid-cols-[100px_1fr] gap-3 py-2 border-t hairline first:border-t-0 text-[12.5px]">
      <span className="mono text-[11px] text-ink-500">{k}</span>
      <span className={`mono text-[12px] break-all ${tone === 'err' ? 'text-rose-600 dark:text-rose-400' : tone === 'ok' ? 'text-emerald-600 dark:text-emerald-400' : 'text-ink-950 dark:text-white'}`}>{v}</span>
    </div>
  );
  return (
    <Panel title={`step · ${step.name}`}
           action={<button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="copy" size={12} /></button>}>
      <div className="p-4 pb-6">
        <Field k="node" v={step.name} />
        <Field k="index" v={'#' + String(step.i).padStart(2,'0')} />
        <Field k="status" v={step.status} tone={step.status === 'err' ? 'err' : (step.status === 'ok' ? 'ok' : '')} />
        <Field k="duration" v={step.dur + ' ms'} />
        <Field k="attempts" v={String(step.attempts)} />
        <Field k="async" v={step.async ? 'true' : 'false'} />
        {step.usage && <Field k="usage" v={`p=${step.usage.prompt} c=${step.usage.completion}`} />}
        {step.err && <Field k="error" v={step.err} tone="err" />}

        <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-6 mb-2">State diff</h4>
        <div className="rounded-lg bg-ink-950 dark:bg-black/60 border border-white/5 p-3.5 mono text-[11.5px] leading-[1.65] whitespace-pre-wrap break-all">
          {step.diff.map((line, i) => {
            let cls = 'text-ink-500';
            if (line.startsWith('+')) cls = 'text-emerald-300';
            else if (line.startsWith('-')) cls = 'text-rose-300';
            else if (line.startsWith('!')) cls = 'text-rose-300';
            else cls = 'text-ink-400';
            return <div key={i} className={cls}>{line}</div>;
          })}
        </div>

        <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">State · before</h4>
        <pre className="rounded-lg bg-ink-50 dark:bg-ink-900 border hairline p-3.5 mono text-[11px] text-ink-700 dark:text-ink-300 overflow-x-auto scroll-thin">{JSON.stringify(step.before, null, 2)}</pre>

        <h4 className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500 mt-5 mb-2">State · after</h4>
        {step.after
          ? <pre className="rounded-lg bg-ink-50 dark:bg-ink-900 border hairline p-3.5 mono text-[11px] text-ink-700 dark:text-ink-300 overflow-x-auto scroll-thin">{JSON.stringify(step.after, null, 2)}</pre>
          : <div className="rounded-lg border border-rose-200 dark:border-rose-900/40 bg-rose-50 dark:bg-rose-900/20 p-3.5 mono text-[11px] text-rose-700 dark:text-rose-300">(no exit; node failed)</div>}
      </div>
    </Panel>
  );
};

const TimelinePanel = ({ activeIdx, setActiveIdx }) => {
  const total = 1100;
  const step = window.TRACE.steps[activeIdx];
  const cursorPos = ((step.t0 + step.dur/2) / total) * 100;
  return (
    <div className="mt-3 rounded-xl border hairline bg-white dark:bg-ink-950 px-4 py-3.5">
      <div className="flex items-center justify-between mono text-[11px] text-ink-500 mb-2.5">
        <span>timeline · 1.04s · virtual-thread executor</span>
        <div className="flex items-center gap-3">
          <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-accent-500/70" />ok</span>
          <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-amber-300" />parallel</span>
          <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-amber-500" />retry</span>
          <span className="inline-flex items-center gap-1.5"><span className="w-2.5 h-2.5 rounded-sm bg-rose-500" />error</span>
        </div>
      </div>
      <div className="relative h-12 bg-ink-50 dark:bg-ink-900 rounded-lg overflow-hidden">
        {Array.from({ length: 12 }).map((_, i) => (
          <div key={i} className="absolute top-0 bottom-0 w-px bg-black/5 dark:bg-white/5" style={{ left: `${(i/11)*100}%` }} />
        ))}
        {window.TRACE.steps.map(s => {
          let bg = 'bg-accent-200 text-ink-950';
          if (s.status === 'parallel') bg = 'bg-amber-200 text-ink-950';
          if (s.status === 'retry') bg = 'bg-amber-400 text-ink-950';
          if (s.status === 'err') bg = 'bg-rose-500 text-white';
          return (
            <button key={s.i}
                    onClick={() => setActiveIdx(s.i)}
                    className={`absolute top-2 h-8 rounded mono text-[10.5px] px-2 flex items-center overflow-hidden whitespace-nowrap ${bg} ${activeIdx === s.i ? 'ring-2 ring-ink-950 dark:ring-white' : ''}`}
                    style={{ left: `${(s.t0/total)*100}%`, width: `${Math.max((s.dur/total)*100, 3)}%` }}
                    title={`${s.name} · ${s.dur}ms`}>
              {s.name}
            </button>
          );
        })}
        <div className="absolute top-0 bottom-0 w-0.5 bg-ink-950 dark:bg-white pointer-events-none" style={{ left: `${cursorPos}%` }} />
      </div>
    </div>
  );
};

const LogsPanel = () => (
  <div className="mt-3 rounded-xl border hairline bg-white dark:bg-ink-950 overflow-hidden">
    <div className="px-4 py-2.5 border-b hairline flex items-center justify-between bg-ink-50/50 dark:bg-ink-900/40">
      <span className="mono text-[10.5px] uppercase tracking-[0.14em] text-ink-500">logs · execution {window.TRACE.executionId.slice(0,8)}</span>
      <button className="w-6 h-6 rounded inline-flex items-center justify-center text-ink-500 hover:bg-ink-100 dark:hover:bg-ink-900"><Icon name="chevron-down" size={12} /></button>
    </div>
    <div className="max-h-[200px] overflow-auto scroll-thin mono text-[11.5px] leading-[1.65]">
      {window.TRACE.logs.map((l, i) => {
        const lvColors = { info: 'text-ink-500', warn: 'text-amber-600 dark:text-amber-400', err: 'text-rose-600 dark:text-rose-400', evt: 'text-accent-600 dark:text-accent-100' };
        return (
          <div key={i} className="grid grid-cols-[60px_50px_1fr] gap-3 px-4 py-1 border-t hairline first:border-t-0 text-ink-700 dark:text-ink-300">
            <span className="text-ink-400">{String(l.t).padStart(4,'0')}ms</span>
            <span className={`font-medium ${lvColors[l.lv]}`}>{l.lv.toUpperCase()}</span>
            <span>{l.msg}</span>
          </div>
        );
      })}
    </div>
  </div>
);

window.TraceExplorer = TraceExplorer;
