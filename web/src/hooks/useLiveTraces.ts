import { useEffect, useRef, useState } from 'react'
import { api } from '@/lib/api'
import type { TraceSummary } from '@/types'
import { MOCK_TRACE_LIST } from '@/data/mock'

const BASE_BACKOFF_MS = 1_000
const MAX_BACKOFF_MS = 30_000

function toSummaries(data: unknown): TraceSummary[] {
  const ids: string[] = Array.isArray(data) ? data : ((data as { items?: string[] }).items ?? [])
  return ids.map((id) => ({ id, graph: 'graph', status: 'COMPLETED' }))
}

export function useLiveTraces(): TraceSummary[] {
  const [list, setList] = useState<TraceSummary[]>(MOCK_TRACE_LIST)
  const backoffRef = useRef(BASE_BACKOFF_MS)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    let cancelled = false

    function fetchList() {
      api.traces
        .list(20)
        .then((data) => {
          if (cancelled) return
          const summaries = toSummaries(data)
          if (summaries.length > 0) setList(summaries)
          backoffRef.current = BASE_BACKOFF_MS
        })
        .catch(() => {
          // backend not available — keep current data
        })
    }

    function openStream() {
      if (cancelled || document.visibilityState === 'hidden') return
      esRef.current?.close()

      const es = api.traces.stream()
      esRef.current = es

      es.addEventListener('Complete', () => {
        fetchList()
        scheduleReconnect()
      })

      es.onerror = () => {
        es.close()
        scheduleReconnect()
      }
    }

    function scheduleReconnect() {
      if (cancelled) return
      timerRef.current = setTimeout(() => {
        backoffRef.current = Math.min(backoffRef.current * 2, MAX_BACKOFF_MS)
        openStream()
      }, backoffRef.current)
    }

    function onVisibilityChange() {
      if (document.visibilityState === 'visible') {
        fetchList()
        openStream()
      } else {
        esRef.current?.close()
        esRef.current = null
      }
    }

    fetchList()
    openStream()
    document.addEventListener('visibilitychange', onVisibilityChange)

    return () => {
      cancelled = true
      if (timerRef.current) clearTimeout(timerRef.current)
      esRef.current?.close()
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
  }, [])

  return list
}
