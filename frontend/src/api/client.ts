// 백엔드 API 호출. 주소는 같은 출처의 /api (개발 중에는 Vite 프록시가 백엔드로 넘긴다). 백엔드 주소를 코드에 두지 않는다.

/** 화면이 구분해서 안내하는 오류 코드. 서버가 보낸 그 밖의 값과 본문 문구는 쓰지 않는다. */
export type ApiErrorCode =
  | 'BLANK'
  | 'TOO_LONG'
  | 'CONTAINS_NUL'
  | 'NO_UTTERANCES'
  | 'TOO_MANY_REQUESTS'
  | 'PAYLOAD_TOO_LARGE'
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
    return 'TOO_MANY_REQUESTS'
  }
  if (response.status === 413) {
    return 'PAYLOAD_TOO_LARGE'
  }
  try {
    const body: unknown = await response.json()
    const code = typeof body === 'object' && body !== null ? (body as { code?: unknown }).code : undefined
    return typeof code === 'string' && KNOWN_CODES.has(code) ? (code as ApiErrorCode) : 'UNKNOWN'
  } catch {
    return 'UNKNOWN'
  }
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
