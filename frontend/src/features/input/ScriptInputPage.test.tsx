import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MAX_FILE_BYTES } from './limits'
import { ScriptInputPage } from './ScriptInputPage'

// SPEC F1 입력 화면. 디바운스는 가짜 타이머로 확인한다 (실제 시간을 기다리지 않음).
// 입력은 fireEvent로 넣는다. user-event는 Testing Library의 비동기 래퍼가 setTimeout(0)을 기다리는데,
// vitest 가짜 타이머에서는 그 타이머가 돌지 않아 멈춘다.

type Reply = { status: number; body: unknown }

const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>()
let previewReplies: Reply[] = []
let jobReplies: Reply[] = []
/** 설정하면 작업 생성 응답이 이 약속이 풀릴 때까지 늦어진다 (요청 중 상태 확인용). */
let jobGate: Promise<void> | null = null

function respond(reply: Reply): Response {
  return new Response(JSON.stringify(reply.body), {
    status: reply.status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function previewCalls() {
  return fetchMock.mock.calls.filter(([url]) => url === '/api/preview')
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <ScriptInputPage />
    </QueryClientProvider>,
  )
}

/**
 * 가짜 타이머를 ms만큼 진행하고 그 사이의 렌더링·응답 처리를 끝낸다.
 * 디바운스가 끝나 렌더링된 뒤에 요청이 시작되고, React Query는 결과 알림을 setTimeout(0)으로 보내므로
 * 0ms 타이머만 몇 번 더 돌린다 (그 이상 시간을 진행하지 않는다).
 */
async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms)
  })
  for (let i = 0; i < 3; i++) {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0)
    })
  }
}

const textarea = () => screen.getByLabelText('회의 스크립트')
const startButton = () => screen.getByRole('button', { name: '정리 시작' })
const fileInput = () => screen.getByLabelText('txt 파일 열기')

function typeText(value: string) {
  fireEvent.change(textarea(), { target: { value } })
}

function pickFile(file: File) {
  fireEvent.change(fileInput(), { target: { files: [file] } })
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval', 'Date'] })
  previewReplies = []
  jobReplies = []
  jobGate = null
  fetchMock.mockReset()
  fetchMock.mockImplementation(async (url) => {
    if (url === '/api/jobs' && jobGate) {
      await jobGate
    }
    const queue = url === '/api/preview' ? previewReplies : jobReplies
    const reply = queue.shift() ?? { status: 500, body: { code: 'INTERNAL_ERROR' } }
    return respond(reply)
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  // vitest 전역(globals)을 켜지 않아 Testing Library 자동 정리가 돌지 않으므로 직접 정리한다
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('ScriptInputPage', () => {
  it('startDisabledWithGuidanceWhenBlank', async () => {
    renderPage()

    expect(startButton()).toBeDisabled()
    expect(screen.getByText('스크립트를 붙여 넣거나 txt 파일을 여세요.')).toBeInTheDocument()

    typeText('   ')
    await advance(500)

    expect(startButton()).toBeDisabled()
    expect(screen.getByText('스크립트를 붙여 넣거나 txt 파일을 여세요.')).toBeInTheDocument()
    // 공백만 있는 입력은 미리보기를 부르지 않는다
    expect(previewCalls()).toHaveLength(0)
  })

  /** 빈 입력 최종 판정은 미리보기 응답 기준이다 (프론트와 서버의 공백 기준이 다를 수 있음). */
  it('startDisabledWhenPreviewSaysBlankOrNoUtterances', async () => {
    previewReplies.push({ status: 400, body: { code: 'BLANK', message: '입력이 비어 있습니다.' } })
    previewReplies.push({ status: 400, body: { code: 'NO_UTTERANCES', message: '발언이 없습니다.' } })
    renderPage()

    typeText('x')
    await advance(500)

    expect(startButton()).toBeDisabled()
    expect(screen.getByText('스크립트를 붙여 넣거나 txt 파일을 여세요.')).toBeInTheDocument()

    typeText('김민수:')
    await advance(500)

    expect(startButton()).toBeDisabled()
    expect(screen.getByText(/발언이 없습니다\. 화자 줄 아래에 내용을 입력하세요\./)).toBeInTheDocument()
  })

  it('callsPreviewOnceAfter500msIdle', async () => {
    previewReplies.push({ status: 200, body: { utteranceCount: 1, speakers: ['김민수'] } })
    renderPage()

    typeText('김')
    await advance(300)
    typeText('김민')
    await advance(300)
    typeText('김민수: 네')
    await advance(499)
    expect(previewCalls()).toHaveLength(0)

    await advance(1)

    expect(previewCalls()).toHaveLength(1)
    const [url, init] = previewCalls()[0]
    // 백엔드 주소를 코드에 넣지 않고 같은 출처의 /api로 부른다 (개발 중에는 Vite 프록시)
    expect(url).toBe('/api/preview')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ text: '김민수: 네' })

    await advance(2000)
    expect(previewCalls()).toHaveLength(1)
  })

  it('showsSummaryLine', async () => {
    previewReplies.push({ status: 200, body: { utteranceCount: 132, speakers: ['김민수', '이영희', '박준호', '미상'] } })
    renderPage()

    typeText('김민수: 시작합니다')
    await advance(500)

    expect(screen.getByText('발언 132개 · 화자 4명 (김민수, 이영희, 박준호, 미상)')).toBeInTheDocument()
    expect(startButton()).toBeEnabled()
  })

  /** SPEC S6: 화자 이름은 일반 텍스트로만 보인다. */
  it('rendersSpeakerNamesAsPlainText', async () => {
    const speaker = '<script>alert(1)</script>'
    previewReplies.push({ status: 200, body: { utteranceCount: 1, speakers: [speaker] } })
    renderPage()

    typeText('x')
    await advance(500)

    expect(screen.getByText(`발언 1개 · 화자 1명 (${speaker})`)).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
  })

  it('previewErrorHidesSummaryLine', async () => {
    previewReplies.push({ status: 200, body: { utteranceCount: 2, speakers: ['김민수'] } })
    previewReplies.push({ status: 429, body: { code: 'TOO_MANY_REQUESTS', message: '요청이 너무 많습니다.' } })
    previewReplies.push({ status: 400, body: { code: 'CONTAINS_NUL', message: '서버 문구' } })
    renderPage()

    typeText('김민수: 가')
    await advance(500)
    expect(screen.getByText(/^발언 2개/)).toBeInTheDocument()

    typeText('김민수: 가나')
    await advance(500)
    expect(screen.queryByText(/^발언 /)).not.toBeInTheDocument()
    // 미리보기 429는 시작을 막지 않는다 (작업 생성은 별도 제한)
    expect(startButton()).toBeEnabled()

    typeText('김민수: 가나다')
    await advance(500)
    expect(screen.queryByText(/^발언 /)).not.toBeInTheDocument()
    expect(screen.queryByText(/서버 문구/)).not.toBeInTheDocument()
  })

  it('overLimitShowsCountAndBlocksStart', async () => {
    renderPage()

    typeText('가'.repeat(50_001))
    await advance(500)

    expect(screen.getByText(/글자 수 50,001 \/ 최대 50,000자/)).toBeInTheDocument()
    expect(startButton()).toBeDisabled()
    expect(previewCalls()).toHaveLength(0)
  })

  /** 글자 수는 서버와 같이 CRLF를 LF로 바꾼 뒤 코드 포인트로 센다. */
  it('countsLikeServer', async () => {
    previewReplies.push({ status: 200, body: { utteranceCount: 1, speakers: ['미상'] } })
    renderPage()
    const emoji = String.fromCodePoint(0x1f600)
    const crlf = String.fromCharCode(13, 10)

    // 49,998 + 줄바꿈 1 + 1 = 50,000자 (UTF-16 단위로는 100,000을 넘는다)
    typeText(emoji.repeat(49_998) + crlf + '가')
    await advance(500)

    expect(screen.queryByText(/글자 수/)).not.toBeInTheDocument()
    expect(startButton()).toBeEnabled()
  })

  it('filePickFillsTextarea', async () => {
    previewReplies.push({ status: 200, body: { utteranceCount: 1, speakers: ['김민수'] } })
    renderPage()

    pickFile(new File(['김민수: 파일에서 읽음'], 'meeting.txt', { type: 'text/plain' }))
    await advance(0)

    expect(textarea()).toHaveValue('김민수: 파일에서 읽음')
  })

  it('fileErrorShowsReason', async () => {
    renderPage()
    typeText('기존 입력')

    const cases: [File, string][] = [
      [new File(['가'], 'meeting.docx'), 'txt 파일만 열 수 있습니다.'],
      [new File([new Uint8Array(MAX_FILE_BYTES + 1).fill(0x41)], 'big.txt'), '파일이 200KB를 넘어 열 수 없습니다.'],
      [new File([new Uint8Array([0x41, 0x00])], 'nul.txt'), '텍스트 파일이 아닙니다.'],
      [new File([new Uint8Array([0x41, 0xff, 0xff])], 'bad.txt'), '읽을 수 없는 인코딩입니다. UTF-8 또는 EUC-KR 파일을 여세요.'],
    ]
    for (const [file, message] of cases) {
      pickFile(file)
      await advance(0)

      expect(screen.getByRole('alert')).toHaveTextContent(message)
      expect(textarea()).toHaveValue('기존 입력')
    }
  })

  it('jobCreation429ShowsRetryMessage', async () => {
    previewReplies.push({ status: 200, body: { utteranceCount: 1, speakers: ['김민수'] } })
    jobReplies.push({ status: 429, body: { code: 'TOO_MANY_REQUESTS', message: '서버 문구' } })
    renderPage()

    typeText('김민수: 네')
    await advance(500)
    fireEvent.click(startButton())
    await advance(0)

    expect(screen.getByRole('alert')).toHaveTextContent('요청이 많습니다. 잠시 후 다시 시도하세요.')
    const jobCall = fetchMock.mock.calls.find(([url]) => url === '/api/jobs')
    expect(JSON.parse(String(jobCall?.[1]?.body))).toEqual({ text: '김민수: 네' })
  })

  /** 오류 응답 본문은 화면에 그대로 쓰지 않고 오류 코드별 고정 문구로 안내한다. */
  it('jobCreationErrorShowsFixedMessageNotResponseBody', async () => {
    jobReplies.push({ status: 400, body: { code: 'TOO_LONG', message: '서버가 보낸 문구 <b>x</b>' } })
    jobReplies.push({ status: 500, body: { code: 'SOMETHING_ELSE', message: '내부 문구' } })
    renderPage()

    typeText('김민수: 네')
    fireEvent.click(startButton())
    await advance(0)

    expect(screen.getByRole('alert')).toHaveTextContent('입력이 최대 글자 수를 넘었습니다.')
    expect(screen.queryByText(/서버가 보낸 문구/)).not.toBeInTheDocument()

    fireEvent.click(startButton())
    await advance(0)

    expect(screen.getByRole('alert')).toHaveTextContent('요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요.')
    expect(screen.queryByText(/내부 문구/)).not.toBeInTheDocument()
  })

  it('jobCreationSuccessShowsStatus', async () => {
    jobReplies.push({ status: 201, body: { jobId: '3f2b8c1e-0000-4000-8000-000000000000' } })
    renderPage()

    typeText('김민수: 네')
    fireEvent.click(startButton())
    await advance(0)

    expect(screen.getByRole('status')).toHaveTextContent('작업을 만들었습니다.')
    // 작업 ID는 열람 권한이므로 화면에 쓰지 않는다
    expect(screen.queryByText(/3f2b8c1e/)).not.toBeInTheDocument()
  })

  function jobCalls() {
    return fetchMock.mock.calls.filter(([url]) => url === '/api/jobs')
  }

  /** 같은 입력으로 작업이 두 번 만들어지지 않는다 (S4 한도, F2부터는 LLM 비용). */
  it('jobCreationSuccessBlocksRestartWithSameInput', async () => {
    jobReplies.push({ status: 201, body: { jobId: '3f2b8c1e-0000-4000-8000-000000000000' } })
    renderPage()

    typeText('김민수: 네')
    fireEvent.click(startButton())
    await advance(0)

    expect(startButton()).toBeDisabled()
    fireEvent.click(startButton())
    await advance(0)
    expect(jobCalls()).toHaveLength(1)

    typeText('김민수: 네, 다시')
    expect(startButton()).toBeEnabled()
  })

  /** 요청 중에는 입력을 고칠 수 없고 다시 시작할 수 없다. 진행 중인 결과를 버리지 않는다. */
  it('inputIsLockedWhileJobIsPending', async () => {
    let release: () => void = () => {}
    jobGate = new Promise((resolve) => {
      release = resolve
    })
    jobReplies.push({ status: 201, body: { jobId: '3f2b8c1e-0000-4000-8000-000000000000' } })
    renderPage()

    typeText('김민수: 네')
    fireEvent.click(startButton())
    await advance(0)

    expect(startButton()).toBeDisabled()
    expect(textarea()).toHaveAttribute('readonly')
    expect(fileInput()).toBeDisabled()

    release()
    await advance(0)

    expect(screen.getByRole('status')).toHaveTextContent('작업을 만들었습니다.')
    expect(textarea()).not.toHaveAttribute('readonly')
    expect(jobCalls()).toHaveLength(1)
  })

  /** 화면이 다시 그려지기 전의 연속 클릭도 요청 하나로 막는다. */
  it('rapidDoubleClickCreatesOneJob', async () => {
    jobReplies.push({ status: 201, body: { jobId: '3f2b8c1e-0000-4000-8000-000000000000' } })
    jobReplies.push({ status: 201, body: { jobId: '9a9a9a9a-0000-4000-8000-000000000000' } })
    renderPage()

    typeText('김민수: 네')
    const button = startButton()
    fireEvent.click(button)
    fireEvent.click(button)
    await advance(0)

    expect(jobCalls()).toHaveLength(1)
  })
})
