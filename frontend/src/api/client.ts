// 백엔드 API 호출. 주소는 같은 출처의 /api (개발 중에는 Vite 프록시가 백엔드로 넘긴다). 백엔드 주소를 코드에 두지 않는다.

/** 화면이 구분해서 안내하는 오류 코드. 서버가 보낸 그 밖의 값과 본문 문구는 쓰지 않는다. */
export type ApiErrorCode =
  | 'BLANK'
  | 'TOO_LONG'
  | 'CONTAINS_NUL'
  | 'NO_UTTERANCES'
  | 'TOO_MANY_REQUESTS'
  | 'QUEUE_FULL'
  | 'PAYLOAD_TOO_LARGE'
  | 'NOT_FOUND'
  | 'NOT_RETRYABLE'
  | 'UNKNOWN'

const KNOWN_CODES: ReadonlySet<string> = new Set(['BLANK', 'TOO_LONG', 'CONTAINS_NUL', 'NO_UTTERANCES'])

export class ApiError extends Error {
  readonly status: number
  readonly code: ApiErrorCode

  constructor(status: number, code: ApiErrorCode) {
    super(`API error ${status} ${code}`)
    this.status = status
    this.code = code
  }
}

export type Preview = { utteranceCount: number; speakers: string[] }

export async function fetchPreview(text: string): Promise<Preview> {
  const body = await post('/api/preview', { text })
  if (!isPreview(body)) {
    throw new ApiError(200, 'UNKNOWN')
  }
  return { utteranceCount: body.utteranceCount, speakers: body.speakers }
}

export async function createJob(text: string): Promise<{ jobId: string }> {
  const body = await post('/api/jobs', { text })
  if (typeof body !== 'object' || body === null || typeof (body as { jobId?: unknown }).jobId !== 'string') {
    throw new ApiError(201, 'UNKNOWN')
  }
  return { jobId: (body as { jobId: string }).jobId }
}

export type JobStatus = 'QUEUED' | 'RUNNING' | 'DONE' | 'FAILED'
export type ItemType = 'AGENDA' | 'DISCUSSION' | 'DECISION' | 'TODO'

/** 회의록 항목. evidence가 비어 있으면 "근거 없음". assignee·due는 근거 발언에서 확인된 값만 있고, 없으면 null. */
export type SummaryItem = {
  type: ItemType
  text: string
  evidence: number[]
  assignee: string | null
  due: string | null
}

/** 작업 조회 결과 (SPEC F2). failure는 FAILED일 때만, items는 DONE일 때만 있다. 발언 본문은 없다. */
export type JobView = { status: JobStatus; failure: string | null; items: SummaryItem[] | null }

const JOB_STATUSES: ReadonlySet<string> = new Set(['QUEUED', 'RUNNING', 'DONE', 'FAILED'])
const ITEM_TYPES: ReadonlySet<string> = new Set(['AGENDA', 'DISCUSSION', 'DECISION', 'TODO'])

/** 작업 상태와 결과. 형식이 맞지 않는 응답은 그리지 않도록 오류로 바꾼다. */
export async function fetchJobStatus(jobId: string): Promise<JobView> {
  const response = await fetch(jobPath(jobId))
  if (!response.ok) {
    throw new ApiError(response.status, await errorCode(response))
  }
  const view = toJobView(await response.json())
  if (view === null) {
    throw new ApiError(200, 'UNKNOWN')
  }
  return view
}

/** 실패한 작업을 다시 실행한다 (202, 본문 없음). */
export async function retryJob(jobId: string): Promise<void> {
  const response = await fetch(`${jobPath(jobId)}/retry`, { method: 'POST' })
  if (!response.ok) {
    throw new ApiError(response.status, await errorCode(response))
  }
}

function jobPath(jobId: string): string {
  return `/api/jobs/${encodeURIComponent(jobId)}`
}

async function post(path: string, payload: unknown): Promise<unknown> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!response.ok) {
    throw new ApiError(response.status, await errorCode(response))
  }
  return response.json()
}

/** 상태 코드로 정해지는 것은 상태 코드로, 나머지는 알려진 code 값만 받아들인다. */
async function errorCode(response: Response): Promise<ApiErrorCode> {
  if (response.status === 429) {
    // 대기열 가득 참과 요청 수 제한은 화면 문구가 다르다 (ADR 0007)
    return (await bodyCode(response)) === 'QUEUE_FULL' ? 'QUEUE_FULL' : 'TOO_MANY_REQUESTS'
  }
  if (response.status === 413) {
    return 'PAYLOAD_TOO_LARGE'
  }
  if (response.status === 404) {
    return 'NOT_FOUND'
  }
  if (response.status === 409) {
    return 'NOT_RETRYABLE'
  }
  const code = await bodyCode(response)
  return code !== null && KNOWN_CODES.has(code) ? (code as ApiErrorCode) : 'UNKNOWN'
}

async function bodyCode(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    const code = typeof body === 'object' && body !== null ? (body as { code?: unknown }).code : undefined
    return typeof code === 'string' ? code : null
  } catch {
    return null
  }
}

function toJobView(body: unknown): JobView | null {
  if (typeof body !== 'object' || body === null) {
    return null
  }
  const { status, failure, items } = body as { status?: unknown; failure?: unknown; items?: unknown }
  if (typeof status !== 'string' || !JOB_STATUSES.has(status)) {
    return null
  }
  if (failure !== undefined && typeof failure !== 'string') {
    return null
  }
  let parsedItems: SummaryItem[] | null = null
  if (status === 'DONE') {
    if (!Array.isArray(items)) {
      return null
    }
    parsedItems = []
    for (const item of items) {
      const parsed = toSummaryItem(item)
      if (parsed === null) {
        return null
      }
      parsedItems.push(parsed)
    }
  }
  return {
    status: status as JobStatus,
    failure: status === 'FAILED' && typeof failure === 'string' ? failure : null,
    items: parsedItems,
  }
}

function toSummaryItem(item: unknown): SummaryItem | null {
  if (typeof item !== 'object' || item === null) {
    return null
  }
  const { type, text, evidence, assignee, due } = item as Record<string, unknown>
  if (typeof type !== 'string' || !ITEM_TYPES.has(type) || typeof text !== 'string') {
    return null
  }
  if (!Array.isArray(evidence) || !evidence.every((no) => Number.isInteger(no))) {
    return null
  }
  if (!isOptionalText(assignee) || !isOptionalText(due)) {
    return null
  }
  return {
    type: type as ItemType,
    text,
    evidence: evidence as number[],
    assignee: assignee ?? null,
    due: due ?? null,
  }
}

function isOptionalText(value: unknown): value is string | null | undefined {
  return value === null || value === undefined || typeof value === 'string'
}

function isPreview(body: unknown): body is Preview {
  if (typeof body !== 'object' || body === null) {
    return false
  }
  const { utteranceCount, speakers } = body as { utteranceCount?: unknown; speakers?: unknown }
  return (
    typeof utteranceCount === 'number' &&
    Array.isArray(speakers) &&
    speakers.every((speaker) => typeof speaker === 'string')
  )
}
