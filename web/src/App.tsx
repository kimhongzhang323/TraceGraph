import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Header } from '@/components/Header'
import { Footer } from '@/components/Footer'
import { Seo } from '@/components/Seo'
import { Home } from '@/pages/Home'
import { Docs } from '@/pages/Docs'
import { TraceExplorer } from '@/pages/TraceExplorer'
import { Studio } from '@/pages/Studio'
import { Changelog } from '@/pages/Changelog'
import { ApiReference } from '@/pages/ApiReference'
import { useTheme } from '@/hooks/useTheme'

const APP_ROUTES = ['docs', 'trace', 'studio', 'api', 'changelog']

function routeId(pathname: string): string {
  const seg = pathname.split('/').filter(Boolean)[0] ?? ''
  return APP_ROUTES.includes(seg) ? seg : 'home'
}

function Layout() {
  const [theme, setTheme] = useTheme()
  const location = useLocation()
  const route = routeId(location.pathname)
  const hideFooter = route === 'trace' || route === 'studio'
  const seo =
    route === 'docs'
      ? {
          title: 'Docs',
          description: 'Documentation for TraceGraph: quickstart, runtime features, Spring Boot integration, replay, and API reference.',
          path: location.pathname,
          noindex: false,
        }
      : route === 'trace'
        ? {
            title: 'Trace explorer',
            description: 'Inspect executions, replay traces, and compare state changes in the TraceGraph trace explorer.',
            path: location.pathname,
            noindex: true,
          }
        : route === 'studio'
          ? {
              title: 'Studio',
              description: 'Visualize graph structure, relationships, and execution context in the TraceGraph studio.',
              path: location.pathname,
              noindex: true,
            }
          : route === 'api'
            ? {
                title: 'API reference',
                description: 'REST endpoints exposed by the TraceGraph Spring Boot starter for traces, replay, stream, and graph inspection.',
                path: location.pathname,
                noindex: false,
              }
            : route === 'changelog'
              ? {
                  title: 'Changelog',
                  description: 'Release notes, breaking changes, and upgrade tips for TraceGraph.',
                  path: location.pathname,
                  noindex: false,
                }
              : {
                  title: 'Typed agent runtime for the JVM',
                  description: 'TraceGraph is a typed execution-graph runtime for the JVM. Replay runs, resume checkpoints, and observe every step.',
                  path: '/',
                  noindex: false,
                }

  return (
    <div className="min-h-screen flex flex-col bg-white dark:bg-ink-950">
      <Seo {...seo} />
      <Header route={route} theme={theme} setTheme={setTheme} />
      <main className="flex-1">
        <Routes>
          <Route path="/"            element={<Home />} />
          <Route path="/docs"        element={<Docs />} />
          <Route path="/docs/:id"    element={<Docs />} />
          <Route path="/trace"       element={<TraceExplorer />} />
          <Route path="/studio"      element={<Studio />} />
          <Route path="/api"         element={<ApiReference />} />
          <Route path="/changelog"   element={<Changelog />} />
          <Route path="*"            element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      {!hideFooter && <Footer />}
    </div>
  )
}

export function App() {
  return (
    <BrowserRouter>
      <Layout />
    </BrowserRouter>
  )
}
