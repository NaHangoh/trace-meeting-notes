import type { ApiErrorCode } from '../../api/client'
import type { ReadFailure } from './readScriptFile'

// 화면 안내 문구. 서버 응답 본문은 쓰지 않고 오류 코드별 고정 문구만 보여준다.

export const BLANK_GUIDANCE = '스크립트를 붙여 넣거나 txt 파일을 여세요.'

export const JOB_CREATED = '작업을 만들었습니다.'

export function fileErrorMessage(reason: ReadFailure): string {
  switch (reason) {
    case 'NOT_TXT':
      return 'txt 파일만 열 수 있습니다.'
    case 'TOO_LARGE':
      return '파일이 200KB를 넘어 열 수 없습니다.'
    case 'CONTAINS_NUL':
      return '텍스트 파일이 아닙니다. NUL 문자가 들어 있습니다.'
    case 'UNDECODABLE':
      return '읽을 수 없는 인코딩입니다. UTF-8 또는 EUC-KR 파일을 여세요.'
  }
}

export function apiErrorMessage(code: ApiErrorCode): string {
  switch (code) {
    case 'BLANK':
      return BLANK_GUIDANCE
    case 'NO_UTTERANCES':
      return '발언이 없습니다. 화자 줄 아래에 내용을 입력하세요.'
    case 'TOO_LONG':
      return '입력이 최대 글자 수를 넘었습니다.'
    case 'CONTAINS_NUL':
      return '텍스트가 아닌 문자(NUL)가 들어 있습니다.'
    case 'PAYLOAD_TOO_LARGE':
      return '입력이 너무 큽니다.'
    case 'TOO_MANY_REQUESTS':
      return '요청이 많습니다. 잠시 후 다시 시도하세요.'
    case 'UNKNOWN':
      return '요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요.'
  }
}
