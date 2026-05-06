/* TraceGraph SPA — vanilla ES modules, hash router */

const view = document.getElementById('view');
const toast = document.getElementById('toast');
let toastTimer = null;

// ── Utilities ────────────────────────────────────────────────────────────────

function showToast(msg, durationMs = 4000) {
  toast.textContent = msg;
  toast.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.add('hidden'), durationMs);
}

async function apiFetch(path, opts = {}) {
  const res = await fetch(path, opts);
  if (!res.ok) throw Object.assign(new Error(`HTTP ${res.status}`), { status: res.status });
  const ct = res.headers.get('content-type') || '';
  if (ct.includes('application/json')) return res.json();
  return res.text();
}

function prettyJson(val) {
  try {
    return JSON.stringify(typeof val === 'string' ? JSON.parse(val) : val, null, 2);
  } catch {
    return String(val);
  }
}

/** Very simple state diff: highlight top-level keys that differ. */
function renderStateDiff(before, after) {
  const b = typeof before === 'object' && before !== null ? before : {};
  const a = typeof after  === 'object' && after  !== null ? after  : {};
  const allKeys = new Set([...Object.keys(b), ...Object.keys(a)]);
  let html = '<table style="width:100%;border-collapse:collapse">';
  html += '<tr><th style="width:35%;text-align:left;padding:2px 4px;color:var(--text-muted)">Key</th>'
        + '<th style="text-align:left;padding:2px 4px;color:var(--text-muted)">Before → After</th></tr>';
  for (const k of allKeys) {
    const bv = JSON.stringify(b[k]);
    const av = JSON.stringify(a[k]);
    const changed = bv !== av;
    const bg = changed ? 'background:var(--diff-add);' : '';
    html += `<tr style="${bg}"><td style="padding:3px 4px;font-family:var(--font-mono);font-size:0.78rem">${esc(k)}</td>`
          + `<td style="padding:3px 4px;font-family:var(--font-mono);font-size:0.78rem">${esc(bv)} → ${esc(av)}</td></tr>`;
  }
  html += '</table>';
  return html;
}

function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function duration(step) {
  if (step.startedAt && step.completedAt) {
    const ms = new Date(step.completedAt) - new Date(step.startedAt);
    return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`;
  }
  return '—';
}

function setActiveNav(route) {
  document.querySelectorAll('.nav-links a').forEach(a => {
    a.classList.toggle('active', a.dataset.route === route);
  });
}

// ── Router ───────────────────────────────────────────────────────────────────

function route() {
  const hash = location.hash || '#/traces';
  const [base, ...rest] = hash.replace('#/', '').split('/');
  switch (base) {
    case 'traces':
      if (rest.length > 0) renderTrace(rest[0]);
      else renderTraceList();
      break;
    case 'graph':
      renderGraph();
      break;
    case 'diff':
      renderDiff(new URLSearchParams(hash.split('?')[1] || ''));
      break;
    default:
      renderTraceList();
  }
}

window.addEventListener('hashchange', route);
window.addEventListener('DOMContentLoaded', route);

// ── View: Trace list ─────────────────────────────────────────────────────────

async function renderTraceList() {
  setActiveNav('traces');
  view.innerHTML = '<h1>Traces</h1><p class="loading">Loading…</p>';
  let ids;
  try {
    ids = await apiFetch('/tracegraph/traces');
  } catch (e) {
    view.innerHTML = `<h1>Traces</h1><p class="error-msg">Failed to load traces: ${esc(e.message)}</p>`;
    return;
  }
  if (!Array.isArray(ids) || ids.length === 0) {
    view.innerHTML = '<h1>Traces</h1><p class="muted">No traces recorded yet.</p>';
    return;
  }

  // Fetch summaries in parallel (best-effort)
  const summaries = await Promise.allSettled(ids.map(id => apiFetch(`/tracegraph/traces/${id}`)));

  let rows = '';
  ids.forEach((id, i) => {
    const t = summaries[i].status === 'fulfilled' ? summaries[i].value : null;
    const status  = t?.status  ?? '—';
    const started = t?.startedAt ? new Date(t.startedAt).toLocaleString() : '—';
    const steps   = Array.isArray(t?.steps) ? t.steps.length : '—';
    rows += `<tr data-id="${esc(id)}">
      <td style="font-family:var(--font-mono);font-size:0.8rem">${esc(id)}</td>
      <td><span class="badge badge-${esc(status)}">${esc(status)}</span></td>
      <td>${esc(started)}</td>
      <td>${esc(String(steps))}</td>
    </tr>`;
  });

  view.innerHTML = `
    <h1>Traces</h1>
    <table>
      <thead><tr><th>Execution ID</th><th>Status</th><th>Started</th><th>Steps</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>`;

  view.querySelectorAll('tbody tr').forEach(tr => {
    tr.addEventListener('click', () => {
      location.hash = `#/traces/${tr.dataset.id}`;
    });
  });
}

// ── View: Single trace ───────────────────────────────────────────────────────

async function renderTrace(id) {
  setActiveNav('traces');
  view.innerHTML = `<h1>Trace: <code>${esc(id)}</code></h1><p class="loading">Loading…</p>`;
  let trace;
  try {
    trace = await apiFetch(`/tracegraph/traces/${esc(id)}`);
  } catch (e) {
    view.innerHTML = `<h1>Trace</h1><p class="error-msg">${e.status === 404 ? 'Trace not found.' : esc(e.message)}</p>`;
    return;
  }

  const steps = trace.steps ?? [];
  let stepHtml = '';
  steps.forEach((s, idx) => {
    stepHtml += `<div class="step-row" data-idx="${idx}">
      <span class="step-index">${idx}</span>
      <span class="step-name">${esc(s.nodeName ?? '?')}</span>
      <span class="step-meta">attempts: ${s.attempts ?? 1} &nbsp;|&nbsp; ${duration(s)}</span>
      <button class="step-btn" data-replay="${idx}">Replay from here</button>
    </div>`;
  });

  view.innerHTML = `
    <h1>Trace: <code style="font-size:0.85rem">${esc(id)}</code></h1>
    <div class="trace-meta">
      <span><strong>Status:</strong> <span class="badge badge-${esc(trace.status)}">${esc(trace.status)}</span></span>
      <span><strong>Started:</strong> ${esc(trace.startedAt ? new Date(trace.startedAt).toLocaleString() : '—')}</span>
      <span><strong>Completed:</strong> ${esc(trace.completedAt ? new Date(trace.completedAt).toLocaleString() : '—')}</span>
      ${trace.forkedFromExecutionId ? `<span><strong>Forked from:</strong> <a href="#/traces/${esc(trace.forkedFromExecutionId)}">${esc(trace.forkedFromExecutionId)}</a> step ${trace.forkedFromStepIndex}</span>` : ''}
    </div>
    <div class="detail-layout">
      <div>
        <h2>Steps</h2>
        <div class="timeline">${stepHtml || '<p class="muted">No steps recorded.</p>'}</div>
      </div>
      <div class="side-panel" id="step-detail">
        <h3>Step detail</h3>
        <p class="muted">Click a step to inspect state.</p>
      </div>
    </div>`;

  // Step click → side panel
  view.querySelectorAll('.step-row').forEach(row => {
    row.addEventListener('click', e => {
      if (e.target.classList.contains('step-btn')) return;
      const idx = parseInt(row.dataset.idx, 10);
      const s = steps[idx];
      const panel = document.getElementById('step-detail');
      panel.innerHTML = `
        <h3>Step ${idx} — ${esc(s.nodeName ?? '?')}</h3>
        <p class="muted" style="margin-bottom:0.5rem">Attempts: ${s.attempts ?? 1} &nbsp;|&nbsp; ${duration(s)}</p>
        <h2 style="margin-top:0.75rem">State diff</h2>
        ${renderStateDiff(s.before, s.after)}
        <h2>Before</h2><pre>${esc(prettyJson(s.before))}</pre>
        <h2>After</h2><pre>${esc(prettyJson(s.after))}</pre>
        ${s.error ? `<h2>Error</h2><pre class="error-msg">${esc(prettyJson(s.error))}</pre>` : ''}`;
    });
  });

  // Replay buttons
  view.querySelectorAll('[data-replay]').forEach(btn => {
    btn.addEventListener('click', async () => {
      const step = btn.dataset.replay;
      try {
        const result = await apiFetch(`/tracegraph/traces/${encodeURIComponent(id)}/replay?step=${step}`, { method: 'POST' });
        const newId = typeof result === 'string' ? result : (result.executionId ?? JSON.stringify(result));
        showToast(`Replay started — new execution ID: ${newId}`);
      } catch (e) {
        showToast(`Replay failed: ${e.message}`);
      }
    });
  });

  // Live tail note for RUNNING traces
  if (trace.status === 'RUNNING') {
    const liveNote = document.createElement('p');
    liveNote.className = 'muted';
    liveNote.style.marginTop = '1rem';
    liveNote.textContent = 'Live tail: submit initial state to POST /tracegraph/traces/stream (SSE) to stream events for running graphs.';
    view.querySelector('.timeline').after(liveNote);
  }
}

// ── View: Graph ───────────────────────────────────────────────────────────────

async function renderGraph() {
  setActiveNav('graph');
  view.innerHTML = '<h1>Graph</h1><p class="loading">Loading…</p>';

  const [mermaidResult, complexityResult] = await Promise.allSettled([
    apiFetch('/tracegraph/ui/graph'),
    apiFetch('/tracegraph/ui/complexity'),
  ]);

  let html = '<h1>Graph</h1>';

  if (mermaidResult.status === 'fulfilled') {
    const src = mermaidResult.value;
    html += `<div class="mermaid-wrap"><div class="mermaid">${esc(src)}</div></div>`;
  } else {
    html += `<p class="error-msg">Could not load graph: ${esc(mermaidResult.reason.message)}</p>`;
  }

  if (complexityResult.status === 'fulfilled') {
    const c = complexityResult.value;
    let statsRows = '';
    if (c && typeof c === 'object') {
      for (const [k, v] of Object.entries(c)) {
        statsRows += `<tr><td>${esc(k)}</td><td>${esc(String(v))}</td></tr>`;
      }
    }
    html += `<h2>Complexity metrics</h2>
      <table class="stats-table">
        <thead><tr><th>Metric</th><th>Value</th></tr></thead>
        <tbody>${statsRows || '<tr><td colspan="2" class="muted">No metrics available.</td></tr>'}</tbody>
      </table>`;
  } else {
    html += `<p class="muted" style="margin-top:1rem">Complexity metrics unavailable (${esc(complexityResult.reason.message)}).</p>`;
  }

  view.innerHTML = html;

  if (mermaidResult.status === 'fulfilled') {
    // eslint-disable-next-line no-undef
    mermaid.initialize({ startOnLoad: false, theme: 'dark' });
    // eslint-disable-next-line no-undef
    await mermaid.run({ nodes: view.querySelectorAll('.mermaid') });
  }
}

// ── View: Diff ────────────────────────────────────────────────────────────────

async function renderDiff(params) {
  setActiveNav('diff');
  const initA = params.get('a') ?? '';
  const initB = params.get('b') ?? '';

  view.innerHTML = `
    <h1>Diff</h1>
    <form class="diff-form" id="diff-form">
      <label>Execution A <input type="text" id="diff-a" value="${esc(initA)}" placeholder="execution-id-a" /></label>
      <label>Execution B <input type="text" id="diff-b" value="${esc(initB)}" placeholder="execution-id-b" /></label>
      <button type="submit" class="btn-primary">Compare</button>
    </form>
    <div id="diff-result"></div>`;

  document.getElementById('diff-form').addEventListener('submit', async e => {
    e.preventDefault();
    const a = document.getElementById('diff-a').value.trim();
    const b = document.getElementById('diff-b').value.trim();
    if (!a || !b) return;
    location.hash = `#/diff?a=${encodeURIComponent(a)}&b=${encodeURIComponent(b)}`;
    await loadDiff(a, b);
  });

  if (initA && initB) await loadDiff(initA, initB);
}

async function loadDiff(a, b) {
  const result = document.getElementById('diff-result');
  result.innerHTML = '<p class="loading">Computing diff…</p>';
  let diff;
  try {
    diff = await apiFetch(`/tracegraph/traces/${encodeURIComponent(a)}/diff/${encodeURIComponent(b)}`);
  } catch (e) {
    result.innerHTML = `<p class="error-msg">${e.status === 404 ? 'One or both traces not found.' : esc(e.message)}</p>`;
    return;
  }

  const prefixSteps = diff.commonPrefix ?? [];
  const leftSteps   = diff.leftRemainder  ?? [];
  const rightSteps  = diff.rightRemainder ?? [];
  const divIdx      = diff.divergenceIndex ?? prefixSteps.length;

  function stepLine(s, i) {
    return `<div class="step-row">
      <span class="step-index">${i}</span>
      <span class="step-name">${esc(s.nodeName ?? '?')}</span>
      <span class="step-meta">attempts: ${s.attempts ?? 1}</span>
    </div>`;
  }

  let html = `
    <div style="display:flex;gap:2rem;margin-bottom:0.75rem;flex-wrap:wrap">
      <span>Identical: <strong>${diff.identical ? 'yes' : 'no'}</strong></span>
      <span>Same status: <strong>${diff.sameStatus ? 'yes' : 'no'}</strong></span>
      <span>Same final state: <strong>${diff.sameFinalState ? 'yes' : 'no'}</strong></span>
      <span>Divergence at step: <strong>${divIdx}</strong></span>
    </div>`;

  if (prefixSteps.length > 0) {
    html += '<h2>Common prefix</h2><div class="timeline">';
    prefixSteps.forEach((s, i) => { html += stepLine(s, i); });
    html += '</div>';
  }

  if (leftSteps.length > 0 || rightSteps.length > 0) {
    html += `<h2>Divergence (step ${divIdx}+)</h2>
      <div class="diff-columns">
        <div class="diff-col">
          <h3>A — ${esc(a)}</h3>
          <div class="timeline diff-remove">`;
    leftSteps.forEach((s, i)  => { html += stepLine(s, divIdx + i); });
    html += `  </div></div>
        <div class="diff-col">
          <h3>B — ${esc(b)}</h3>
          <div class="timeline diff-add">`;
    rightSteps.forEach((s, i) => { html += stepLine(s, divIdx + i); });
    html += '  </div></div></div>';
  } else {
    html += '<p class="muted" style="margin-top:1rem">No divergence — traces share all steps.</p>';
  }

  result.innerHTML = html;
}
