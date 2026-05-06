// Tiny icon component that wraps Lucide. Renders inline SVG, no DOM mutation.

const Icon = ({ name, size = 16, className = '', strokeWidth = 1.75 }) => {
  const ref = React.useRef(null);
  React.useEffect(() => {
    if (!ref.current || !window.lucide) return;
    const icons = window.lucide.icons;
    // Lucide stores icons in PascalCase (e.g. ArrowRight) — convert kebab-case.
    const key = name.split('-').map(w => w[0].toUpperCase() + w.slice(1)).join('');
    const icon = icons[key];
    if (!icon) {
      ref.current.innerHTML = '';
      return;
    }
    const [, attrs, children] = icon;
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    Object.entries({ ...attrs, width: size, height: size, 'stroke-width': strokeWidth }).forEach(([k, v]) =>
      svg.setAttribute(k, String(v))
    );
    children.forEach(([tag, a]) => {
      const el = document.createElementNS('http://www.w3.org/2000/svg', tag);
      Object.entries(a).forEach(([k, v]) => el.setAttribute(k, String(v)));
      svg.appendChild(el);
    });
    ref.current.innerHTML = '';
    ref.current.appendChild(svg);
  }, [name, size, strokeWidth]);
  return <span ref={ref} className={`inline-flex items-center justify-center ${className}`} style={{ width: size, height: size }} />;
};

window.Icon = Icon;
