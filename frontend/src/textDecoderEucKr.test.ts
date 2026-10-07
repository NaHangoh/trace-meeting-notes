import { describe, expect, it } from 'vitest'

// F1 파일 읽기(UTF-8 → euc-kr, 둘 다 fatal)가 테스트 환경(jsdom)에서 동작하는지 확인한다
describe('TextDecoder euc-kr (jsdom)', () => {
  // "김민수: 가" 를 CP949로 인코딩한 바이트
  const cp949 = new Uint8Array([0xb1, 0xe8, 0xb9, 0xce, 0xbc, 0xf6, 0x3a, 0x20, 0xb0, 0xa1])

  it('jsdom 환경에서 실행된다', () => {
    expect(typeof window).toBe('object')
  })

  it('fatal euc-kr 디코더로 CP949 바이트를 읽는다', () => {
    const decoded = new TextDecoder('euc-kr', { fatal: true }).decode(cp949)

    expect(decoded).toBe('김민수: 가')
  })

  it('fatal utf-8 디코더는 CP949 바이트에서 실패한다', () => {
    expect(() => new TextDecoder('utf-8', { fatal: true }).decode(cp949)).toThrow(TypeError)
  })

  it('fatal euc-kr 디코더는 잘못된 바이트에서 실패한다', () => {
    expect(() => new TextDecoder('euc-kr', { fatal: true }).decode(new Uint8Array([0xff]))).toThrow(
      TypeError,
    )
  })
})
