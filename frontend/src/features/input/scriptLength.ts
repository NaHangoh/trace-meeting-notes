/** 서버와 같은 기준의 글자 수: 줄바꿈(\r\n)을 \n으로 바꾼 뒤 코드 포인트 수 (SPEC F1, S3). */
export function scriptLength(text: string): number {
  let count = 0
  for (const _ of text.replaceAll('\r\n', '\n')) {
    count++
  }
  return count
}
