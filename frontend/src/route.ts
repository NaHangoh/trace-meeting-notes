import { useSyncExternalStore } from 'react'

// 해시 라우팅 (SPEC F2). 라우터 의존성 없이 location.hash와 hashchange만 쓴다.
// 작업 ID는 URL 해시에만 둔다: 해시는 서버로 가지 않고, index.html의 no-referrer로 리퍼러에도 실리지 않는다.

export type Route = { page: 'input' } | { page: 'job'; jobId: string | null }

const JOB_PREFIX = '#/jobs/'
/** 서버가 만드는 작업 ID 형식(UUID). 그 밖의 값은 서버에 묻지 않는다 (경로 조작 방지). */
const JOB_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/** `#/jobs/{id}`는 상태 화면(형식이 틀리면 jobId null), 그 밖은 입력 화면. */
export function parseHash(hash: string): Route {
  if (!hash.startsWith(JOB_PREFIX)) {
    return { page: 'input' }
  }
  const id = hash.slice(JOB_PREFIX.length)
  return { page: 'job', jobId: JOB_ID.test(id) ? id : null }
}

export function jobHash(jobId: string): string {
  return `${JOB_PREFIX}${jobId}`
}

function subscribe(onChange: () => void) {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

export function useRoute(): Route {
  return parseHash(useSyncExternalStore(subscribe, () => window.location.hash))
}
