import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { ApiError, retryJob, type JobView, type SummaryItem } from '../../api/client'
import { apiErrorMessage } from '../input/messages'
import {
  CHECK_FAILED,
  CHECKING,
  failureMessage,
  ITEM_GROUPS,
  NO_ITEMS,
  NOT_FOUND,
  RETRY_HINT,
  STATUS_LABELS,
  THROTTLED,
  UNSUPPORTED,
} from './messages'
import { isNotFound, jobQueryKey, useJobStatus } from './useJobStatus'

/**
 * 작업 상태 화면 (SPEC F2, `#/jobs/{id}`). 상태를 2초마다 조회해 대기 → 진행 → 완료/실패를 보여준다.
 * 항목·담당자·기한은 모두 일반 텍스트로 렌더링한다 (SPEC S6). 작업 ID는 화면 문구와 문서 제목에 넣지 않는다.
 *
 * @param jobId 작업 ID. 형식이 틀린 주소면 null이고, 서버에 묻지 않고 없는 작업으로 보여준다
 */
export function JobStatusPage({ jobId }: { jobId: string | null }) {
  const query = useJobStatus(jobId)

  let body
  if (jobId === null || isNotFound(query.error)) {
    body = (
      <p role="alert" className="text-red-700">
        ⚠ {NOT_FOUND}
      </p>
    )
  } else {
    body = (
      <>
        {query.data ? <JobView jobId={jobId} view={query.data} /> : <p className="text-gray-700">{CHECKING}</p>}
        {query.error && (
          <p className="text-sm text-gray-700">
            {query.error instanceof ApiError && query.error.code === 'TOO_MANY_REQUESTS' ? THROTTLED : CHECK_FAILED}
          </p>
        )}
      </>
    )
  }

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">정리 결과</h2>
      {body}
      <a className="text-sm text-blue-700 underline" href="#/">
        새 스크립트 정리
      </a>
    </section>
  )
}

function JobView({ jobId, view }: { jobId: string; view: JobView }) {
  const label = STATUS_LABELS[view.status]
  return (
    <>
      <p role="status" className="font-medium">
        <span aria-hidden="true">{label.icon}</span> {label.text}
      </p>
      {view.status === 'FAILED' && <Failure jobId={jobId} failure={view.failure} />}
      {view.status === 'DONE' && <Items items={view.items ?? []} />}
    </>
  )
}

function Failure({ jobId, failure }: { jobId: string; failure: string | null }) {
  const queryClient = useQueryClient()
  // 화면이 다시 그려지기 전의 연속 클릭도 막는다
  const inFlight = useRef(false)
  const retry = useMutation({
    mutationFn: () => retryJob(jobId),
    // 새 상태(QUEUED)를 받아 올 때까지 버튼을 꺼 두고, 받아 오면 조회가 다시 시작된다
    onSuccess: () => queryClient.refetchQueries({ queryKey: jobQueryKey(jobId) }),
    onError: (error) => {
      // 다른 곳에서 이미 다시 시도했거나 작업이 지워졌으면 지금 상태를 다시 받아 온다
      if (error instanceof ApiError && (error.code === 'NOT_RETRYABLE' || error.code === 'NOT_FOUND')) {
        return queryClient.refetchQueries({ queryKey: jobQueryKey(jobId) })
      }
    },
    onSettled: () => {
      inFlight.current = false
    },
  })

  function start() {
    if (inFlight.current) {
      return
    }
    inFlight.current = true
    retry.mutate()
  }

  return (
    <div className="flex flex-col gap-2">
      <p>{failureMessage(failure)}</p>
      <p className="text-sm text-gray-700">{RETRY_HINT}</p>
      <div>
        <button
          type="button"
          className="rounded bg-blue-700 px-4 py-2 text-white disabled:bg-gray-400"
          disabled={retry.isPending}
          onClick={start}
        >
          다시 시도
        </button>
      </div>
      {retry.isError && (
        <p role="alert" className="text-sm text-red-700">
          ⚠ {apiErrorMessage(retry.error instanceof ApiError ? retry.error.code : 'UNKNOWN')}
        </p>
      )}
    </div>
  )
}

function Items({ items }: { items: SummaryItem[] }) {
  if (items.length === 0) {
    return <p className="text-gray-700">{NO_ITEMS}</p>
  }
  return (
    <div className="flex flex-col gap-4">
      {ITEM_GROUPS.map(({ type, label }) => {
        const group = items.filter((item) => item.type === type)
        if (group.length === 0) {
          return null
        }
        return (
          <section key={type} className="flex flex-col gap-2">
            <h3 className="font-semibold">{label}</h3>
            <ul className="flex flex-col gap-2">
              {group.map((item, index) => (
                <Item key={index} item={item} />
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

function Item({ item }: { item: SummaryItem }) {
  const supported = item.evidence.length > 0
  return (
    <li
      className={`flex flex-col gap-1 rounded border p-2 ${supported ? 'border-gray-300' : 'border-amber-500 bg-amber-50'}`}
    >
      <p>{item.text}</p>
      {supported ? (
        <p className="text-sm text-gray-700">근거 발언 {item.evidence.join(', ')}</p>
      ) : (
        <p className="text-sm font-medium text-amber-900">
          <span aria-hidden="true">⚠</span> {UNSUPPORTED}
        </p>
      )}
      {/* 담당자·기한은 근거 발언에서 확인된 값만 서버가 보낸다. 없으면 채우지 않는다 */}
      {item.type === 'TODO' && item.assignee !== null && <p className="text-sm">담당자: {item.assignee}</p>}
      {item.type === 'TODO' && item.due !== null && <p className="text-sm">기한: {item.due}</p>}
    </li>
  )
}
