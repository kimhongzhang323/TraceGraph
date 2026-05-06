import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { Header } from '@/components/Header'
import { Footer } from '@/components/Footer'
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

  return (
    <div className="min-h-screen flex flex-col bg-white dark:bg-ink-950">
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
