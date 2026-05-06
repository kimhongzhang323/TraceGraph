import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import type { TraceSummary } from '@/types'
import { MOCK_TRACE_LIST } from '@/data/mock'

export function useLiveTraces(): TraceSummary[] {
  const [list, setList] = useState<TraceSummary[]>(MOCK_TRACE_LIST)

  useEffect(() => {
    api.traces
      .list(20)
      .then((data) => {
        const ids: string[] = Array.isArray(data) ? data : (data as { items: string[] }).items ?? []
        if (ids.length > 0) {
          setList(ids.map((id) => ({ id, graph: 'graph', status: 'COMPLETED' })))
        }
      })
      .catch(() => {
        // backend not available — keep mock data
      })
  }, [])

  return list
}
