import type { ItemType, JobStatus } from '../../api/client'

// 작업 상태 화면 문구 (SPEC F2). 서버 응답 문구는 쓰지 않고 상태·실패 사유·오류 코드별 고정 문구만 보여준다.
// 상태는 색만으로 구분하지 않도록 아이콘과 문구를 함께 쓴다.

export const STATUS_LABELS: Record<JobStatus, { icon: string; text: string }> = {
  QUEUED: { icon: '⏳', text: '대기 중' },
  RUNNING: { icon: '🔄', text: '정리 중' },
  DONE: { icon: '✓', text: '정리 완료' },
  FAILED: { icon: '⚠', text: '정리 실패' },
}

/** 화면에 묶어 보여주는 순서. */
export const ITEM_GROUPS: { type: ItemType; label: string }[] = [
  { type: 'AGENDA', label: '안건' },
  { type: 'DISCUSSION', label: '논의 내용' },
  { type: 'DECISION', label: '결정 사항' },
  { type: 'TODO', label: '할 일' },
]

/** 없는 작업과 보관 기간이 지나 삭제된 작업을 구분하지 않는다 (서버도 같은 404). */
export const NOT_FOUND = '작업이 없거나 보관 기간이 지나 삭제되었습니다.'

export const CHECKING = '상태를 확인하는 중입니다.'
export const THROTTLED = '조회 요청이 많아 잠시 뒤에 다시 확인합니다.'
export const CHECK_FAILED = '일시적인 오류로 잠시 뒤에 다시 확인합니다.'
export const NO_ITEMS = '정리된 항목이 없습니다.'
export const UNSUPPORTED = '근거 없음'
export const RETRY_HINT = '다시 시도할 수 있습니다.'

export function failureMessage(failure: string | null): string {
  switch (failure) {
    case 'INVALID_OUTPUT':
      return '정리 결과의 형식이 올바르지 않았습니다.'
    case 'TIMEOUT':
      return '정리 시간이 너무 오래 걸려 중단했습니다.'
    case 'CONNECTION_REFUSED':
      return '정리 모델에 연결하지 못했습니다.'
    case 'LLM_ERROR':
      return '정리 모델이 오류를 돌려주었습니다.'
    case 'SERVER_RESTART':
      return '서버가 다시 시작되어 작업이 중단되었습니다.'
    case 'INTERNAL_ERROR':
      return '서버 오류로 정리하지 못했습니다.'
    default:
      return '정리하지 못했습니다.'
  }
}
