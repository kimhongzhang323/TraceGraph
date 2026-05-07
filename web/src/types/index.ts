export interface TraceStep {
  // Frontend display fields (mock-compatible)
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
  // Backend record fields (from TraceStep<S>)
  index?: number
  nodeName?: string
  duration?: string
  error?: string | { className: string; message: string }
  children?: TraceStep[]
}

export interface TraceLog {
  t: number
  lv: 'info' | 'warn' | 'err' | 'evt'
  msg: string
}

export interface ExecutionTrace {
  executionId: string
  // Backend uses initialState/finalState; frontend uses graph/duration
  graph?: string
  initialState?: Record<string, unknown> | null
  finalState?: Record<string, unknown> | null
  status: 'COMPLETED' | 'FAILED' | 'RUNNING' | 'INTERRUPTED' | 'HALTED'
  startedAt: string
  completedAt?: string
  duration?: number
  steps: TraceStep[]
  logs?: TraceLog[]
  forkedFromExecutionId?: string | null
  forkedFromStepIndex?: number
}

export interface TraceDiff {
  leftExecutionId: string
  rightExecutionId: string
  commonPrefix: TraceStep[]
  divergenceIndex: number
  leftRemainder: TraceStep[]
  rightRemainder: TraceStep[]
  sameStatus: boolean
  sameFinalState: boolean
}

export interface ReplayResponse {
  executionId: string
  forkedFromExecutionId: string
  forkedFromStepIndex: number
  status: string
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

export type LensMode = 'topology' | 'relations' | 'execution'

