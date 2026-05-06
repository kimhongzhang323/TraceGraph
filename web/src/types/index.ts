export interface TraceStep {
  i: number
  name: string
  kind: string
  status: 'ok' | 'err' | 'retry' | 'parallel'
  attempts: number
  async: boolean
  dur: number
  t0: number
  branches?: number
  usage?: { prompt: number; completion: number }
  err?: string
  before: Record<string, unknown> | null
  after: Record<string, unknown> | null
  diff: string[]
}

export interface TraceLog {
  t: number
  lv: 'info' | 'warn' | 'err' | 'evt'
  msg: string
}

export interface ExecutionTrace {
  executionId: string
  graph: string
  status: 'COMPLETED' | 'FAILED' | 'RUNNING' | 'INTERRUPTED'
  startedAt: string
  duration: number
  steps: TraceStep[]
  logs: TraceLog[]
}

export interface TraceSummary {
  id: string
  graph: string
  status: string
}

export interface GraphComplexity {
  nodeCount: number
  edgeCount: number
  maxFanOut: number
  maxDepth: number
  cyclomaticComplexity: number
  parallelBranches: number
  subgraphDepth: number
  hotspots: string[]
}

export interface Edge {
  from: string
  to: string
  label?: string
}

export interface StudioNode {
  name: string
  kind: string
  fn: string
  retry: string
  entry: boolean
  terminal: boolean
}

export interface Module {
  name: string
  desc: string
  status: 'stable' | 'experimental' | 'planned'
  cov: string
}

export interface ChangelogEntry {
  v: string
  date: string
  tag?: string
  notes: { kind: 'feat' | 'fix' | 'improve' | 'breaking'; text: string }[]
}

export interface ApiEndpoint {
  m: 'GET' | 'POST' | 'DELETE' | 'PUT'
  p: string
  desc: string
  returns: string
}

export interface ApiGroup {
  group: string
  endpoints: ApiEndpoint[]
}
