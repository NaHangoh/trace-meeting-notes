// 입력 상한. 서버가 다시 검사하므로 프론트 값은 안내용이다 (SPEC F1, S3).

/** 파일 바이트 상한 200KB = 204,800바이트. 백엔드 app.max-request-bytes 기본값과 같다. */
export const MAX_FILE_BYTES = 204_800

/** 입력 최대 글자 수 (줄바꿈을 \n으로 정규화한 뒤 코드 포인트 수). 백엔드 app.max-script-chars 기본값과 같다. */
export const MAX_SCRIPT_CHARS = 50_000
