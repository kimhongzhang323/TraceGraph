import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import { lazy, Suspense } from 'react'
import { Header } from '@/components/Header'
import { Footer } from '@/components/Footer'
import { Seo } from '@/components/Seo'
import { useTheme } from '@/hooks/useTheme'
import { useAuth } from '@/hooks/useAuth'

const Home         = lazy(() => import('@/pages/Home').then((m) => ({ default: m.Home })))
const Docs         = lazy(() => import('@/pages/Docs').then((m) => ({ default: m.Docs })))
const TraceExplorer = lazy(() => import('@/pages/TraceExplorer').then((m) => ({ default: m.TraceExplorer })))
const Studio       = lazy(() => import('@/pages/Studio').then((m) => ({ default: m.Studio })))
const Changelog    = lazy(() => import('@/pages/Changelog').then((m) => ({ default: m.Changelog })))
const ApiReference = lazy(() => import('@/pages/ApiReference').then((m) => ({ default: m.ApiReference })))
const SignIn       = lazy(() => import('@/pages/SignIn').then((m) => ({ default: m.SignIn })))
const SignUp       = lazy(() => import('@/pages/SignUp').then((m) => ({ default: m.SignUp })))
const Forgot       = lazy(() => import('@/pages/Forgot').then((m) => ({ default: m.Forgot })))
const ResetPassword = lazy(() => import('@/pages/ResetPassword').then((m) => ({ default: m.ResetPassword })))
const Profile      = lazy(() => import('@/pages/Profile').then((m) => ({ default: m.Profile })))

const APP_ROUTES = ['docs', 'trace', 'studio', 'api', 'changelog', 'signin', 'signup', 'forgot', 'reset-password', 'profile']

function routeId(pathname: string): string {
  const seg = pathname.split('/').filter(Boolean)[0] ?? ''
  return APP_ROUTES.includes(seg) ? seg : 'home'
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) {
    return <Navigate to="/signin" state={{ from: location.pathname }} replace />
  }
  return <>{children}</>
}

function GuestOnlyRoute({ children }: { children: React.ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  // ?switch=1 lets a signed-in user force their way to the auth page
  // (e.g. broken session, want to change account) without clearing storage manually.
  const forceShow = new URLSearchParams(location.search).get('switch') === '1'
  if (user && !forceShow) {
    return <Navigate to="/profile" replace />
  }
  return <>{children}</>
}

function Layout() {
  const [theme, setTheme] = useTheme()
  const location = useLocation()
  const route = routeId(location.pathname)
  const isAuth = route === 'signin' || route === 'signup' || route === 'forgot' || route === 'reset-password'
  const hideFooter = route === 'trace' || route === 'studio' || isAuth
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
        <Suspense fallback={null}>
          <Routes>
            <Route path="/"               element={<Home />} />
            <Route path="/docs"           element={<Docs />} />
            <Route path="/docs/:id"       element={<Docs />} />
            <Route path="/api"            element={<ApiReference />} />
            <Route path="/changelog"      element={<Changelog />} />

            {/* Auth-gated pages */}
            <Route path="/trace"    element={<ProtectedRoute><TraceExplorer /></ProtectedRoute>} />
            <Route path="/studio"   element={<ProtectedRoute><Studio /></ProtectedRoute>} />
            <Route path="/profile"  element={<ProtectedRoute><Profile /></ProtectedRoute>} />

            {/* Guest-only pages — redirect signed-in users away */}
            <Route path="/signin"         element={<GuestOnlyRoute><SignIn /></GuestOnlyRoute>} />
            <Route path="/signup"         element={<GuestOnlyRoute><SignUp /></GuestOnlyRoute>} />
            <Route path="/forgot"         element={<GuestOnlyRoute><Forgot /></GuestOnlyRoute>} />
            <Route path="/reset-password" element={<GuestOnlyRoute><ResetPassword /></GuestOnlyRoute>} />

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Suspense>
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
