import { useQuery } from '@tanstack/react-query'
import { useRef } from 'react'
import { ApiError, fetchJobStatus } from '../../api/client'

/** 상태 조회 간격 (SPEC F2 2초). */
export const POLL_INTERVAL_MS = 2000
/** 조회가 연달아 실패할 때 늘린 간격의 상한. */
export const MAX_POLL_INTERVAL_MS = 30_000

/** 연속 실패 n번째 뒤의 간격: 2초에서 두 배씩, 30초까지. */
export function pollInterval(failures: number): number {
  return Math.min(POLL_INTERVAL_MS * 2 ** failures, MAX_POLL_INTERVAL_MS)
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'NOT_FOUND'
}

export function jobQueryKey(jobId: string | null) {
  return ['job', jobId] as const
}

/**
 * 작업 상태를 2초마다 조회하고 DONE·FAILED에서 멈춘다. 404면 멈춘다.
 * 그 밖의 실패(429 등)는 마지막 상태를 그대로 두고 간격을 늘려 다시 조회한다 (React Query는 실패해도 data를 유지).
 */
export function useJobStatus(jobId: string | null) {
  // 연속 실패 수. React Query의 실패 수는 조회를 시작할 때마다 0으로 돌아가 연속 횟수를 셀 수 없다
  const failures = useRef(0)
  return useQuery({
    queryKey: jobQueryKey(jobId),
    queryFn: async () => {
      try {
        const view = await fetchJobStatus(jobId as string)
        failures.current = 0
        return view
      } catch (error) {
        failures.current += 1
        throw error
      }
    },
    enabled: jobId !== null,
    retry: false,
    refetchOnWindowFocus: false,
    refetchInterval: (query) => {
      const { data, error } = query.state
      if (isNotFound(error)) {
        return false
      }
      if (!error && data && (data.status === 'DONE' || data.status === 'FAILED')) {
        return false
      }
      return pollInterval(failures.current)
    },
  })
}
