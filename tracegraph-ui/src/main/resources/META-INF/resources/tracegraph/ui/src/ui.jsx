// Reusable UI primitives used across pages.

const Button = ({ children, variant = 'primary', size = 'md', icon, iconRight, as = 'button', href, onClick, className = '', ...rest }) => {
  const base = 'inline-flex items-center justify-center gap-2 font-medium tracking-tight transition-all duration-200 select-none whitespace-nowrap';
  const sizes = {
    sm: 'h-8 px-3 text-[12.5px] rounded-lg',
    md: 'h-10 px-4 text-[13.5px] rounded-lg',
    lg: 'h-12 px-6 text-[15px] rounded-full',
  };
  const variants = {
    primary: 'bg-ink-950 text-white hover:bg-ink-800 dark:bg-white dark:text-ink-950 dark:hover:bg-ink-100',
    ghost:   'border hairline text-ink-950 dark:text-white hover:bg-ink-50 dark:hover:bg-ink-900',
  };
  const cls = `${base} ${sizes[size]} ${variants[variant]} ${className}`;
  const inner = (
    <>
      {icon && <Icon name={icon} size={size === 'lg' ? 16 : 14} />}
      {children}
      {iconRight && <Icon name={iconRight} size={size === 'lg' ? 16 : 14} />}
    </>
  );
  if (as === 'a') return <a href={href} className={cls} onClick={onClick} {...rest}>{inner}</a>;
  return <button className={cls} onClick={onClick} {...rest}>{inner}</button>;
};

const Code = ({ children, className = '' }) => (
  <code className={`mono text-[0.92em] px-1.5 py-0.5 rounded bg-ink-100 dark:bg-ink-900 text-ink-800 dark:text-ink-200 ${className}`}>{children}</code>
);

const Badge = ({ children, tone = 'neutral' }) => {
  const tones = {
    neutral: 'bg-ink-100 text-ink-700 dark:bg-ink-900 dark:text-ink-300',
    ok:      'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-200',
    warn:    'bg-amber-50 text-amber-700 dark:bg-amber-900/30 dark:text-amber-200',
    err:     'bg-rose-50 text-rose-700 dark:bg-rose-900/30 dark:text-rose-200',
    accent:  'bg-accent-50 text-accent-700 dark:bg-accent-700/30 dark:text-accent-100',
    dark:    'bg-ink-950 text-white dark:bg-white dark:text-ink-950',
  };
  return <span className={`mono inline-flex items-center gap-1 text-[10.5px] px-1.5 h-[18px] rounded uppercase tracking-wider ${tones[tone]}`}>{children}</span>;
};

const StatusDot = ({ tone = 'ok', pulse = false }) => {
  const tones = {
    ok:      'bg-emerald-500',
    warn:    'bg-amber-500',
    err:     'bg-rose-500',
    neutral: 'bg-ink-400',
  };
  return <span className={`inline-block w-1.5 h-1.5 rounded-full ${tones[tone]} ${pulse ? 'pulse-dot' : ''}`} />;
};

const SectionLabel = ({ children, num, className = '' }) => (
  <div className={`mono text-[11px] uppercase tracking-[0.16em] text-ink-500 ${className}`}>
    {num && <span className="text-ink-400 mr-2">/ {num}</span>}
    {children}
  </div>
);

const CodeBlock = ({ children, filename, language = 'java' }) => (
  <div className="rounded-2xl border hairline overflow-hidden bg-ink-950 dark:bg-black/40">
    <div className="flex items-center justify-between px-4 py-2.5 border-b border-white/10">
      <span className="mono text-[11px] text-white/50">{filename || ''}</span>
      <span className="mono text-[10px] text-white/30 uppercase tracking-wider">{language}</span>
    </div>
    <pre className="code-block p-5 overflow-x-auto scroll-thin text-white/85"
         dangerouslySetInnerHTML={{ __html: typeof children === 'string' ? children : '' }} />
  </div>
);

window.Button = Button;
window.Code = Code;
window.Badge = Badge;
window.StatusDot = StatusDot;
window.SectionLabel = SectionLabel;
window.CodeBlock = CodeBlock;
