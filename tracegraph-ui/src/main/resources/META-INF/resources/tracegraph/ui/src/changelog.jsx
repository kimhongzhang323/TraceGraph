// Changelog page.

const Changelog = () => (
  <div className="max-w-[920px] mx-auto px-6 lg:px-8 py-16 fade-up">
    <div className="mono text-[11px] uppercase tracking-[0.14em] text-ink-500 mb-4">PROJECT / CHANGELOG</div>
    <h1 className="display-tight text-[56px] text-ink-950 dark:text-white">Changelog</h1>
    <p className="mt-4 text-[16px] text-ink-600 dark:text-ink-400 max-w-xl">Release notes, breaking changes, and upgrade tips. We follow semver; pre-1.0 minor bumps may include breaking changes (always called out below).</p>

    <div className="mt-16 space-y-20">
      {window.CHANGELOG.map((rel, i) => (
        <section key={rel.v} className="relative">
          <div className="grid md:grid-cols-[180px_1fr] gap-8">
            <div className="md:sticky md:top-20 md:self-start">
              <div className="flex items-center gap-2">
                <span className="mono text-[24px] tracking-tight text-ink-950 dark:text-white font-medium">v{rel.v}</span>
                {rel.tag === 'latest' && <Badge tone="accent">latest</Badge>}
              </div>
              <div className="mono text-[12px] text-ink-500 mt-1.5">{rel.date}</div>
            </div>
            <ul className="space-y-3">
              {rel.notes.map((n, j) => (
                <li key={j} className="flex items-start gap-3 text-[14.5px] text-ink-700 dark:text-ink-300 leading-relaxed">
                  <KindTag kind={n.kind} />
                  <span>{n.text}</span>
                </li>
              ))}
            </ul>
          </div>
        </section>
      ))}
    </div>
  </div>
);

const KindTag = ({ kind }) => {
  const styles = {
    feat:     ['bg-accent-50 text-accent-700 dark:bg-accent-700/20 dark:text-accent-100', 'feat'],
    fix:      ['bg-blue-50 text-blue-700 dark:bg-blue-900/30 dark:text-blue-200', 'fix'],
    improve:  ['bg-ink-100 text-ink-700 dark:bg-ink-900 dark:text-ink-300', 'improve'],
    breaking: ['bg-rose-50 text-rose-700 dark:bg-rose-900/30 dark:text-rose-200', 'breaking'],
  };
  const [cls, label] = styles[kind] || styles.improve;
  return <span className={`mono text-[10.5px] px-2 h-[20px] inline-flex items-center rounded uppercase tracking-wider mt-0.5 flex-shrink-0 ${cls}`}>{label}</span>;
};

window.Changelog = Changelog;
