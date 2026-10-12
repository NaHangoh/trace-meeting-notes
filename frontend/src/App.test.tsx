import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import indexHtml from '../index.html?raw'
import App from './App'

// 해시 라우팅 (SPEC F2): 입력 화면 #/ ↔ 상태 화면 #/jobs/{id}. 새 라우터 의존성 없이 hashchange로 바꾼다.
// setInterval만 가짜로 바꾸고 0ms 차례는 실제로 기다린다 (이유는 JobStatusPage.test.tsx 참고).

const JOB_ID = '3f2b8c1e-5a6d-4e7f-8a9b-0c1d2e3f4a5b'

type Reply = { status: number; body: unknown }

const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>()
let replies: Record<string, Reply[]> = {}

function respond(reply: Reply): Response {
  return new Response(JSON.stringify(reply.body), {
    status: reply.status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function renderApp() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <App />
    </QueryClientProvider>,
  )
}

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

function setHash(hash: string) {
  act(() => {
    window.location.hash = hash
    window.dispatchEvent(new HashChangeEvent('hashchange'))
  })
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
  window.location.hash = ''
  replies = {}
  fetchMock.mockReset()
  fetchMock.mockImplementation(async (url) => respond(replies[url]?.shift() ?? { status: 500, body: {} }))
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  cleanup()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  window.location.hash = ''
})

describe('App', () => {
  /** 작업을 만들면 그 작업의 상태 화면으로 이동한다. 작업 ID는 URL 해시에만 있다. */
  it('navigatesToJobAfterCreation', async () => {
    replies['/api/jobs'] = [{ status: 201, body: { jobId: JOB_ID } }]
    replies[`/api/jobs/${JOB_ID}`] = [{ status: 200, body: { status: 'QUEUED' } }]
    renderApp()

    fireEvent.change(screen.getByLabelText('회의 스크립트'), { target: { value: '참석자: 네' } })
    fireEvent.click(screen.getByRole('button', { name: '정리 시작' }))
    await advance(0)
    await advance(0)

    expect(window.location.hash).toBe(`#/jobs/${JOB_ID}`)
    expect(screen.getByRole('status')).toHaveTextContent('대기 중')
    expect(fetchMock.mock.calls.some(([url]) => url === `/api/jobs/${JOB_ID}`)).toBe(true)
    expect(document.body.textContent).not.toContain(JOB_ID)
    expect(document.title).not.toContain(JOB_ID)
  })

  /** 해시 주소로 바로 열면 상태 화면이고, 새 정리 링크로 입력 화면에 돌아간다. */
  it('jobHashOpensStatusPageAndLinkReturnsToInput', async () => {
    replies[`/api/jobs/${JOB_ID}`] = [{ status: 200, body: { status: 'RUNNING' } }]
    window.location.hash = `#/jobs/${JOB_ID}`
    renderApp()
    await advance(0)

    expect(screen.getByRole('status')).toHaveTextContent('정리 중')
    expect(screen.queryByLabelText('회의 스크립트')).not.toBeInTheDocument()

    const link = screen.getByRole('link', { name: '새 스크립트 정리' })
    expect(link).toHaveAttribute('href', '#/')
    setHash('#/')
    expect(screen.getByLabelText('회의 스크립트')).toBeInTheDocument()
  })

  /** 작업 ID 형식이 아닌 해시는 서버에 묻지 않고 같은 404 문구를 보인다 (경로 조작 방지). */
  it('malformedJobHashShowsNotFoundWithoutRequest', async () => {
    for (const hash of ['#/jobs/..%2Fpreview', '#/jobs/../preview', '#/jobs/', '#/jobs/not a job']) {
      cleanup()
      fetchMock.mockClear()
      window.location.hash = hash
      renderApp()
      await advance(0)

      expect(screen.getByRole('alert')).toHaveTextContent('작업이 없거나 보관 기간이 지나 삭제되었습니다.')
      expect(fetchMock).not.toHaveBeenCalled()
    }
  })

  /** 작업 ID가 URL에 있으므로 리퍼러를 보내지 않고, 외부 스크립트를 불러오지 않는다. */
  it('indexHtmlSendsNoReferrerAndLoadsNoExternalScript', () => {
    const page = new DOMParser().parseFromString(indexHtml, 'text/html')

    expect(page.querySelector('meta[name="referrer"]')?.getAttribute('content')).toBe('no-referrer')
    const sources = Array.from(page.querySelectorAll('script[src]'), (script) => script.getAttribute('src'))
    expect(sources.length).toBeGreaterThan(0)
    for (const src of sources) {
      expect(src).toMatch(/^\/(?!\/)/)
    }
  })

  /** 알 수 없는 해시는 입력 화면이다. */
  it('unknownHashShowsInput', async () => {
    window.location.hash = '#/somewhere'
    renderApp()

    expect(screen.getByLabelText('회의 스크립트')).toBeInTheDocument()
  })
})
