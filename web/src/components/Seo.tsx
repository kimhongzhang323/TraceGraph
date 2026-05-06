import { useEffect } from 'react'

type SeoProps = {
  title: string
  description: string
  path?: string
  noindex?: boolean
}

function getSiteUrl() {
  return (import.meta.env.VITE_SITE_URL ?? window.location.origin).replace(/\/$/, '')
}

function upsertMeta(selector: string, create: () => HTMLMetaElement, content: string) {
  const existing = document.head.querySelector<HTMLMetaElement>(selector)
  const el = existing ?? create()
  el.setAttribute('content', content)
  return el
}

function upsertLink(selector: string, create: () => HTMLLinkElement, href: string) {
  const existing = document.head.querySelector<HTMLLinkElement>(selector)
  const el = existing ?? create()
  el.setAttribute('href', href)
  return el
}

export function Seo({ title, description, path = '/', noindex = false }: SeoProps) {
  useEffect(() => {
    const siteUrl = getSiteUrl()
    const canonical = `${siteUrl}${path.startsWith('/') ? path : `/${path}`}`.replace(/\/$/, path === '/' ? '/' : '')
    const pageTitle = title.endsWith('TraceGraph') ? title : `${title} · TraceGraph`

    document.title = pageTitle

    upsertMeta('meta[name="description"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('name', 'description')
      document.head.appendChild(el)
      return el
    }, description)

    upsertMeta('meta[name="robots"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('name', 'robots')
      document.head.appendChild(el)
      return el
    }, noindex ? 'noindex,follow' : 'index,follow')

    upsertMeta('meta[property="og:title"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('property', 'og:title')
      document.head.appendChild(el)
      return el
    }, pageTitle)

    upsertMeta('meta[property="og:description"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('property', 'og:description')
      document.head.appendChild(el)
      return el
    }, description)

    upsertMeta('meta[property="og:type"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('property', 'og:type')
      document.head.appendChild(el)
      return el
    }, 'website')

    upsertMeta('meta[property="og:url"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('property', 'og:url')
      document.head.appendChild(el)
      return el
    }, canonical)

    upsertMeta('meta[property="og:site_name"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('property', 'og:site_name')
      document.head.appendChild(el)
      return el
    }, 'TraceGraph')

    upsertMeta('meta[name="twitter:card"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('name', 'twitter:card')
      document.head.appendChild(el)
      return el
    }, 'summary_large_image')

    upsertMeta('meta[name="twitter:title"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('name', 'twitter:title')
      document.head.appendChild(el)
      return el
    }, pageTitle)

    upsertMeta('meta[name="twitter:description"]', () => {
      const el = document.createElement('meta')
      el.setAttribute('name', 'twitter:description')
      document.head.appendChild(el)
      return el
    }, description)

    upsertLink('link[rel="canonical"]', () => {
      const el = document.createElement('link')
      el.setAttribute('rel', 'canonical')
      document.head.appendChild(el)
      return el
    }, canonical)
  }, [description, noindex, path, title])

  return null
}