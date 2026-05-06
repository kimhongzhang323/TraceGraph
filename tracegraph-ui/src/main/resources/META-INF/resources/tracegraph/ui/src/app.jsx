// App shell — hash router + theme + render.

const useHashRoute = () => {
  const [route, setRoute] = React.useState(() => parseRoute(window.location.hash));
  React.useEffect(() => {
    const onHash = () => {
      setRoute(parseRoute(window.location.hash));
      window.scrollTo({ top: 0, behavior: 'instant' });
    };
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);
  return route;
};

const parseRoute = (hash) => {
  const h = (hash || '').replace(/^#/, '').replace(/^\//, '');
  if (!h) return 'home';
  const seg = h.split('/')[0];
  return ['docs','trace','studio','api','changelog'].includes(seg) ? seg : 'home';
};

const useTheme = () => {
  const [theme, setTheme] = React.useState(() => {
    const stored = localStorage.getItem('tg-theme');
    if (stored) return stored;
    return 'light';
  });
  React.useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark');
    localStorage.setItem('tg-theme', theme);
  }, [theme]);
  return [theme, setTheme];
};

const App = () => {
  const route = useHashRoute();
  const [theme, setTheme] = useTheme();

  let content;
  switch (route) {
    case 'docs':      content = <Docs />; break;
    case 'trace':     content = <TraceExplorer />; break;
    case 'studio':    content = <Studio />; break;
    case 'api':       content = <ApiRef />; break;
    case 'changelog': content = <Changelog />; break;
    default:          content = <Home />;
  }

  // Footer hidden on app-style views to maximize canvas space.
  const showFooter = route === 'home' || route === 'docs' || route === 'changelog' || route === 'api';

  return (
    <div className="min-h-screen flex flex-col">
      <Header route={route} theme={theme} setTheme={setTheme} />
      <main className="flex-1">{content}</main>
      {showFooter && <Footer />}
    </div>
  );
};

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(<App />);
