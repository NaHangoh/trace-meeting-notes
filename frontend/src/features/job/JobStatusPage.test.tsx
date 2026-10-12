import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { JobStatusPage } from './JobStatusPage'

// SPEC F2 작업 상태 화면. 2초 조회는 가짜 타이머로 확인한다 (실제 시간을 기다리지 않음).
// 입력은 fireEvent로 넣는다 (user-event는 가짜 타이머에서 멈춘다, ScriptInputPage.test.tsx 참고).
// 조회 간격(React Query refetchInterval)은 setInterval만 쓰므로 setInterval만 가짜로 바꾼다.
// setTimeout까지 바꾸면 React Query가 결과를 구독자에게 다음 차례(setTimeout 0)로 넘기는 경로가 멈춰,
// 캐시는 바뀌어도 화면이 다시 그려지지 않는다 (확인함). 그래서 0ms 차례는 실제 setTimeout으로 기다린다.

const JOB_ID = '3f2b8c1e-5a6d-4e7f-8a9b-0c1d2e3f4a5b'
const STATUS_URL = `/api/jobs/${JOB_ID}`
const RETRY_URL = `/api/jobs/${JOB_ID}/retry`

type Reply = { status: number; body?: unknown; gate?: Promise<void> }

const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>()
/** 조회 응답. 다 쓰면 마지막 응답을 계속 돌려준다. */
let statusReplies: Reply[] = []
let lastStatusReply: Reply = { status: 500, body: { code: 'INTERNAL_ERROR' } }
let retryReplies: Reply[] = []

function respond(reply: Reply): Response {
  return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
    status: reply.status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function statusCalls() {
  return fetchMock.mock.calls.filter(([url, init]) => url === STATUS_URL && (init?.method ?? 'GET') === 'GET')
}

function retryCalls() {
  return fetchMock.mock.calls.filter(([url, init]) => url === RETRY_URL && init?.method === 'POST')
}

function renderPage(jobId = JOB_ID) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <JobStatusPage jobId={jobId} />
    </QueryClientProvider>,
  )
}

/** 가짜 setInterval을 ms만큼 진행하고, 실제 0ms 차례를 몇 번 기다려 응답 처리와 렌더링을 끝낸다. */
async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
  for (let i = 0; i < 5; i++) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
  }
}

function deferred() {
  let resolve: () => void = () => {}
  const promise = new Promise<void>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

const status = () => screen.getByRole('status')
const retryButton = () => screen.getByRole('button', { name: '다시 시도' })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
  statusReplies = []
  lastStatusReply = { status: 500, body: { code: 'INTERNAL_ERROR' } }
  retryReplies = []
  fetchMock.mockReset()
  fetchMock.mockImplementation(async (url, init) => {
    let reply: Reply
    if (url === RETRY_URL && init?.method === 'POST') {
      reply = retryReplies.shift() ?? { status: 500, body: { code: 'INTERNAL_ERROR' } }
    } else if (url === STATUS_URL) {
      reply = statusReplies.shift() ?? lastStatusReply
      lastStatusReply = reply
    } else {
      reply = { status: 404, body: { code: 'NOT_FOUND' } }
    }
    if (reply.gate) {
      await reply.gate
    }
    return respond(reply)
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const done = (items: unknown[]) => ({ status: 200, body: { status: 'DONE', items } })

describe('JobStatusPage', () => {
  /** 상태는 아이콘과 문구를 함께 보여준다 (색만으로 구분하지 않음). */
  it('showsQueuedRunningDoneFailedWithIconAndText', async () => {
    statusReplies.push(
      { status: 200, body: { status: 'QUEUED' } },
      { status: 200, body: { status: 'RUNNING' } },
      done([]),
    )
    renderPage()
    await advance(0)
    expect(status()).toHaveTextContent('⏳')
    expect(status()).toHaveTextContent('대기 중')

    await advance(2000)
    expect(status()).toHaveTextContent('🔄')
    expect(status()).toHaveTextContent('정리 중')

    await advance(2000)
    expect(status()).toHaveTextContent('✓')
    expect(status()).toHaveTextContent('정리 완료')
    expect(screen.getByText('정리된 항목이 없습니다.')).toBeInTheDocument()

    cleanup()
    statusReplies.push({ status: 200, body: { status: 'FAILED', failure: 'TIMEOUT' } })
    renderPage()
    await advance(0)
    expect(status()).toHaveTextContent('⚠')
    expect(status()).toHaveTextContent('정리 실패')
  })

  /** 2초마다 조회하고 DONE·FAILED에서 멈춘다. */
  it('stopsPollingWhenDoneOrFailed', async () => {
    statusReplies.push({ status: 200, body: { status: 'RUNNING' } }, { status: 200, body: { status: 'RUNNING' } }, done([]))
    renderPage()
    await advance(0)
    expect(statusCalls()).toHaveLength(1)

    await advance(1999)
    expect(statusCalls()).toHaveLength(1)
    await advance(1)
    expect(statusCalls()).toHaveLength(2)
    await advance(2000)
    expect(statusCalls()).toHaveLength(3)

    await advance(20_000)
    expect(statusCalls()).toHaveLength(3)

    cleanup()
    fetchMock.mockClear()
    statusReplies.push({ status: 200, body: { status: 'FAILED', failure: 'LLM_ERROR' } })
    renderPage()
    await advance(20_000)
    expect(statusCalls()).toHaveLength(1)
  })

  /** 조회가 429면 오류 화면으로 바꾸지 않고 마지막 상태를 유지한 채 간격을 늘려 다시 조회한다. */
  it('rateLimitedPollingKeepsLastStateAndBacksOff', async () => {
    statusReplies.push(
      { status: 200, body: { status: 'RUNNING' } },
      { status: 429, body: { code: 'TOO_MANY_REQUESTS' } },
      { status: 429, body: { code: 'TOO_MANY_REQUESTS' } },
      done([]),
    )
    renderPage()
    await advance(0)
    await advance(2000)
    expect(statusCalls()).toHaveLength(2)

    // 마지막 상태 유지, 오류 화면 아님
    expect(status()).toHaveTextContent('정리 중')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.getByText('조회 요청이 많아 잠시 뒤에 다시 확인합니다.')).toBeInTheDocument()

    // 첫 429 뒤에는 4초
    await advance(3999)
    expect(statusCalls()).toHaveLength(2)
    await advance(1)
    expect(statusCalls()).toHaveLength(3)
    expect(status()).toHaveTextContent('정리 중')

    // 연속 두 번째 429 뒤에는 8초
    await advance(7999)
    expect(statusCalls()).toHaveLength(3)
    await advance(1)
    expect(statusCalls()).toHaveLength(4)

    expect(status()).toHaveTextContent('정리 완료')
    expect(screen.queryByText('조회 요청이 많아 잠시 뒤에 다시 확인합니다.')).not.toBeInTheDocument()
  })

  /** 429가 이어져도 간격은 30초를 넘지 않는다. */
  it('backoffIsCappedAt30Seconds', async () => {
    statusReplies.push({ status: 200, body: { status: 'QUEUED' } })
    lastStatusReply = { status: 429, body: { code: 'TOO_MANY_REQUESTS' } }
    statusReplies.push(...Array.from({ length: 10 }, () => ({ status: 429, body: { code: 'TOO_MANY_REQUESTS' } })))
    renderPage()
    await advance(0)
    // 2 + 4 + 8 + 16초 뒤에는 30초 간격
    await advance(2000 + 4000 + 8000 + 16_000)
    const before = statusCalls().length
    await advance(29_999)
    expect(statusCalls()).toHaveLength(before)
    await advance(1)
    expect(statusCalls()).toHaveLength(before + 1)
    expect(status()).toHaveTextContent('대기 중')
  })

  /** 처음 조회부터 429면 상태 확인 중 문구를 유지하고 다시 조회한다. */
  it('rateLimitedFirstLookupShowsCheckingNotError', async () => {
    statusReplies.push({ status: 429, body: { code: 'TOO_MANY_REQUESTS' } }, { status: 200, body: { status: 'QUEUED' } })
    renderPage()
    await advance(0)

    expect(screen.getByText('상태를 확인하는 중입니다.')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    await advance(4000)
    expect(status()).toHaveTextContent('대기 중')
  })

  /** 404는 없는 작업과 삭제된 작업을 구분하지 않는 고정 문구다. 더 조회하지 않는다. */
  it('unknownJobShowsNotFound', async () => {
    statusReplies.push({ status: 404, body: { code: 'NOT_FOUND', message: '서버 문구' } })
    renderPage()
    await advance(0)

    expect(screen.getByRole('alert')).toHaveTextContent('작업이 없거나 보관 기간이 지나 삭제되었습니다.')
    expect(screen.queryByText(/서버 문구/)).not.toBeInTheDocument()
    await advance(20_000)
    expect(statusCalls()).toHaveLength(1)
  })

  /** 조회 중에 작업이 삭제되면(보관 기간) 같은 문구로 바뀌고 조회를 멈춘다. */
  it('jobDeletedWhilePollingShowsNotFound', async () => {
    statusReplies.push({ status: 200, body: { status: 'QUEUED' } }, { status: 404, body: { code: 'NOT_FOUND' } })
    renderPage()
    await advance(0)
    await advance(2000)

    expect(screen.getByRole('alert')).toHaveTextContent('작업이 없거나 보관 기간이 지나 삭제되었습니다.')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    await advance(20_000)
    expect(statusCalls()).toHaveLength(2)
  })

  /** 종류별로 묶어 보여준다. */
  it('groupsItemsByType', async () => {
    statusReplies.push(
      done([
        { type: 'TODO', text: '할 일 하나', evidence: [4], assignee: null, due: null },
        { type: 'AGENDA', text: '안건 하나', evidence: [1], assignee: null, due: null },
        { type: 'DECISION', text: '결정 하나', evidence: [3], assignee: null, due: null },
        { type: 'DISCUSSION', text: '논의 하나', evidence: [2], assignee: null, due: null },
      ]),
    )
    renderPage()
    await advance(0)

    const headings = screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent)
    expect(headings).toEqual(['안건', '논의 내용', '결정 사항', '할 일'])
    expect(within(section('결정 사항')).getByText('결정 하나')).toBeInTheDocument()
    expect(within(section('할 일')).getByText('할 일 하나')).toBeInTheDocument()
  })

  /** 항목 내용·담당자·기한은 일반 텍스트로 렌더링한다 (S6). 화자 이름은 이 화면에 없다 (F1 입력 화면 테스트). */
  it('rendersItemFieldsAsPlainText', async () => {
    const script = '<script>window.injected = true</script>'
    const img = '<img src=x onerror="window.injected = true">'
    statusReplies.push(done([{ type: 'TODO', text: script, evidence: [1], assignee: img, due: '<b>내일</b>' }]))
    renderPage()
    await advance(0)

    expect(screen.getByText(script)).toBeInTheDocument()
    expect(screen.getByText(img, { exact: false })).toBeInTheDocument()
    expect(screen.getByText('<b>내일</b>', { exact: false })).toBeInTheDocument()
    // jsdom은 스크립트를 실행하지 않으므로 아래 요소 검사가 실제 확인이다. window.injected는 실제 브라우저 대비용
    expect(document.querySelector('main script, section script')).toBeNull()
    expect(document.querySelector('img')).toBeNull()
    expect(document.querySelector('b')).toBeNull()
    expect((window as { injected?: boolean }).injected).toBeUndefined()
  })

  /** 근거가 빈 항목은 아이콘과 문구로 "근거 없음"을 표시한다. 근거가 있으면 발언 번호를 보여준다. */
  it('unsupportedItemShowsIconAndText', async () => {
    statusReplies.push(
      done([
        { type: 'DECISION', text: '근거 있는 결정', evidence: [3, 5], assignee: null, due: null },
        { type: 'DECISION', text: '근거 없는 결정', evidence: [], assignee: null, due: null },
      ]),
    )
    renderPage()
    await advance(0)

    const supported = item('근거 있는 결정')
    const unsupported = item('근거 없는 결정')
    expect(supported).toHaveTextContent('근거 발언 3, 5')
    expect(supported).not.toHaveTextContent('근거 없음')
    expect(unsupported).toHaveTextContent('⚠')
    expect(unsupported).toHaveTextContent('근거 없음')
  })

  /** 할 일의 담당자·기한은 값이 있을 때만 보여주고, 없으면 채우지 않는다. */
  it('todoShowsAssigneeAndDueOnlyWhenPresent', async () => {
    statusReplies.push(
      done([
        { type: 'TODO', text: '둘 다 있음', evidence: [1], assignee: '참석자A', due: '다음 주' },
        { type: 'TODO', text: '담당자만', evidence: [1], assignee: '참석자B', due: null },
        { type: 'TODO', text: '기한만', evidence: [1], assignee: null, due: '금요일' },
        { type: 'TODO', text: '둘 다 없음', evidence: [1], assignee: null, due: null },
        { type: 'DECISION', text: '할 일 아닌 항목', evidence: [1], assignee: '참석자C', due: '월요일' },
      ]),
    )
    renderPage()
    await advance(0)

    expect(item('둘 다 있음')).toHaveTextContent('담당자: 참석자A')
    expect(item('둘 다 있음')).toHaveTextContent('기한: 다음 주')
    expect(item('담당자만')).toHaveTextContent('담당자: 참석자B')
    expect(item('담당자만')).not.toHaveTextContent('기한')
    expect(item('기한만')).toHaveTextContent('기한: 금요일')
    expect(item('기한만')).not.toHaveTextContent('담당자')
    expect(item('둘 다 없음')).not.toHaveTextContent('담당자')
    expect(item('둘 다 없음')).not.toHaveTextContent('기한')
    expect(item('할 일 아닌 항목')).not.toHaveTextContent('담당자')
    expect(item('할 일 아닌 항목')).not.toHaveTextContent('기한')
  })

  /** 실패 사유마다 고정 문구가 있고, 서버 문구는 쓰지 않는다. */
  it('failureReasonsHaveFixedMessages', async () => {
    const reasons: Record<string, string> = {
      INVALID_OUTPUT: '정리 결과의 형식이 올바르지 않았습니다.',
      TIMEOUT: '정리 시간이 너무 오래 걸려 중단했습니다.',
      CONNECTION_REFUSED: '정리 모델에 연결하지 못했습니다.',
      LLM_ERROR: '정리 모델이 오류를 돌려주었습니다.',
      SERVER_RESTART: '서버가 다시 시작되어 작업이 중단되었습니다.',
      INTERNAL_ERROR: '서버 오류로 정리하지 못했습니다.',
      SOMETHING_NEW: '정리하지 못했습니다.',
    }
    for (const [failure, message] of Object.entries(reasons)) {
      cleanup()
      statusReplies.push({ status: 200, body: { status: 'FAILED', failure } })
      renderPage()
      await advance(0)
      expect(screen.getByText(message)).toBeInTheDocument()
      expect(retryButton()).toBeEnabled()
    }
  })

  /** 실패하면 다시 시도할 수 있고, 다시 시도 중에는 버튼이 꺼진다. 성공하면 조회를 다시 시작한다. */
  it('retryButtonOnFailure', async () => {
    const gate = deferred()
    statusReplies.push(
      { status: 200, body: { status: 'FAILED', failure: 'TIMEOUT' } },
      { status: 200, body: { status: 'QUEUED' } },
      done([]),
    )
    retryReplies.push({ status: 202, gate: gate.promise })
    renderPage()
    await advance(0)

    fireEvent.click(retryButton())
    await advance(0)
    expect(retryButton()).toBeDisabled()
    fireEvent.click(retryButton())
    await advance(0)
    expect(retryCalls()).toHaveLength(1)

    gate.resolve()
    await advance(0)
    expect(status()).toHaveTextContent('대기 중')
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()

    await advance(2000)
    expect(status()).toHaveTextContent('정리 완료')
  })

  /** 화면이 다시 그려지기 전에 연달아 눌러도 다시 시도 요청은 한 번만 보낸다. */
  it('rapidDoubleRetryClickSendsOneRequest', async () => {
    const gate = deferred()
    lastStatusReply = { status: 200, body: { status: 'FAILED', failure: 'TIMEOUT' } }
    retryReplies.push({ status: 202, gate: gate.promise })
    renderPage()
    await advance(0)

    const button = retryButton()
    fireEvent.click(button)
    fireEvent.click(button)
    await advance(0)
    expect(retryCalls()).toHaveLength(1)
    gate.resolve()
    await advance(0)
  })

  /** 다시 시도 버튼은 실패 상태에서만 보인다. */
  it('retryButtonOnlyWhenFailed', async () => {
    for (const body of [{ status: 'QUEUED' }, { status: 'RUNNING' }, { status: 'DONE', items: [] }]) {
      cleanup()
      statusReplies.push({ status: 200, body })
      renderPage()
      await advance(0)
      expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
    }
  })

  /** 다시 시도 중에 작업이 지워졌으면(404) 상태를 다시 받아 고정 문구를 보이고 조회를 멈춘다. */
  it('retryNotFoundShowsNotFoundAndStopsPolling', async () => {
    statusReplies.push(
      { status: 200, body: { status: 'FAILED', failure: 'TIMEOUT' } },
      { status: 404, body: { code: 'NOT_FOUND', message: '서버 문구' } },
    )
    retryReplies.push({ status: 404, body: { code: 'NOT_FOUND', message: '서버 문구' } })
    renderPage()
    await advance(0)

    fireEvent.click(retryButton())
    await advance(0)

    expect(screen.getByRole('alert')).toHaveTextContent('작업이 없거나 보관 기간이 지나 삭제되었습니다.')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
    expect(screen.queryByText(/서버 문구/)).not.toBeInTheDocument()
    expect(statusCalls()).toHaveLength(2)
    await advance(20_000)
    expect(statusCalls()).toHaveLength(2)
  })

  /** 다시 시도가 거부되면 QUEUE_FULL과 TOO_MANY_REQUESTS를 다른 문구로 안내하고 다시 누를 수 있다. */
  it('queueFullAndRateLimitMessagesDiffer', async () => {
    lastStatusReply = { status: 200, body: { status: 'FAILED', failure: 'TIMEOUT' } }
    retryReplies.push(
      { status: 429, body: { code: 'QUEUE_FULL', message: '서버 문구' } },
      { status: 429, body: { code: 'TOO_MANY_REQUESTS', message: '서버 문구' } },
    )
    renderPage()
    await advance(0)

    fireEvent.click(retryButton())
    await advance(0)
    const queueFull = screen.getByRole('alert').textContent
    expect(queueFull).toContain('대기 중인 정리 작업이 많습니다. 잠시 후 다시 시도하세요.')
    expect(retryButton()).toBeEnabled()

    fireEvent.click(retryButton())
    await advance(0)
    const rateLimited = screen.getByRole('alert').textContent
    expect(rateLimited).toContain('요청이 많습니다. 잠시 후 다시 시도하세요.')
    expect(rateLimited).not.toEqual(queueFull)
    expect(screen.queryByText(/서버 문구/)).not.toBeInTheDocument()
  })

  /** 다른 곳에서 이미 다시 시도해 409면 안내하고 상태를 다시 조회한다. */
  it('retryConflictRefreshesStatus', async () => {
    statusReplies.push(
      { status: 200, body: { status: 'FAILED', failure: 'TIMEOUT' } },
      { status: 200, body: { status: 'RUNNING' } },
    )
    retryReplies.push({ status: 409, body: { code: 'NOT_RETRYABLE' } })
    renderPage()
    await advance(0)

    fireEvent.click(retryButton())
    await advance(0)

    expect(status()).toHaveTextContent('정리 중')
    expect(statusCalls()).toHaveLength(2)
  })

  /** 작업 ID는 열람 권한이므로 화면 문구와 문서 제목에 넣지 않는다. */
  it('jobIdNotInTitleOrText', async () => {
    const replies: Reply[] = [
      done([{ type: 'DECISION', text: '결정', evidence: [1], assignee: null, due: null }]),
      { status: 200, body: { status: 'FAILED', failure: 'TIMEOUT' } },
      { status: 404, body: { code: 'NOT_FOUND' } },
      { status: 429, body: { code: 'TOO_MANY_REQUESTS' } },
      { status: 500, body: { code: 'INTERNAL_ERROR' } },
    ]
    for (const reply of replies) {
      cleanup()
      statusReplies.push(reply)
      renderPage()
      await advance(0)

      expect(document.body.textContent).not.toContain(JOB_ID)
      expect(document.title).not.toContain(JOB_ID)
    }
  })

  /** 응답 형식이 맞지 않으면 그 내용을 그리지 않고, 확인 실패 문구를 보이며 다시 조회한다. */
  it('malformedResponseIsNotRendered', async () => {
    statusReplies.push(
      { status: 200, body: { status: 'DONE', items: [{ type: 'DECISION', text: { html: '<b>x</b>' }, evidence: [1] }] } },
      { status: 200, body: { status: 'QUEUED' } },
    )
    renderPage()
    await advance(0)

    expect(screen.getByText('일시적인 오류로 잠시 뒤에 다시 확인합니다.')).toBeInTheDocument()
    expect(screen.queryByText(/정리 완료/)).not.toBeInTheDocument()
    await advance(4000)
    expect(status()).toHaveTextContent('대기 중')
  })

  /** 500·형식 오류도 마지막 상태를 유지하고 간격을 늘리지만, 문구는 429와 다르다. */
  it('transientErrorKeepsLastStateWithDifferentMessageFromRateLimit', async () => {
    statusReplies.push(
      { status: 200, body: { status: 'RUNNING' } },
      { status: 500, body: { code: 'INTERNAL_ERROR', message: '서버 문구' } },
      { status: 200, body: { status: 'NOT_A_STATUS' } },
      { status: 429, body: { code: 'TOO_MANY_REQUESTS' } },
      done([]),
    )
    renderPage()
    await advance(0)

    // 500: 마지막 상태 유지, 일시적 오류 문구, 오류 화면 아님
    await advance(2000)
    expect(statusCalls()).toHaveLength(2)
    expect(status()).toHaveTextContent('정리 중')
    expect(screen.getByText('일시적인 오류로 잠시 뒤에 다시 확인합니다.')).toBeInTheDocument()
    expect(screen.queryByText('조회 요청이 많아 잠시 뒤에 다시 확인합니다.')).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByText(/서버 문구/)).not.toBeInTheDocument()

    // 형식 오류: 4초 뒤, 같은 문구
    await advance(3999)
    expect(statusCalls()).toHaveLength(2)
    await advance(1)
    expect(statusCalls()).toHaveLength(3)
    expect(status()).toHaveTextContent('정리 중')
    expect(screen.getByText('일시적인 오류로 잠시 뒤에 다시 확인합니다.')).toBeInTheDocument()

    // 429: 8초 뒤, 429 문구로 바뀜
    await advance(8000)
    expect(statusCalls()).toHaveLength(4)
    expect(screen.getByText('조회 요청이 많아 잠시 뒤에 다시 확인합니다.')).toBeInTheDocument()
    expect(screen.queryByText('일시적인 오류로 잠시 뒤에 다시 확인합니다.')).not.toBeInTheDocument()

    await advance(16_000)
    expect(status()).toHaveTextContent('정리 완료')
    expect(screen.queryByText(/잠시 뒤에 다시 확인합니다/)).not.toBeInTheDocument()
  })
})

function section(heading: string): HTMLElement {
  const element = screen.getByRole('heading', { level: 3, name: heading }).closest('section')
  if (!element) {
    throw new Error(`section ${heading} not found`)
  }
  return element
}

function item(text: string): HTMLElement {
  const element = screen.getByText(text).closest('li')
  if (!element) {
    throw new Error(`item ${text} not found`)
  }
  return element
}
