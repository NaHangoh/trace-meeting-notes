import { describe, expect, it } from 'vitest'
import { MAX_FILE_BYTES } from './limits'
import { readScriptFile } from './readScriptFile'

// SPEC F1 파일 읽기 (프론트). 파일은 브라우저에서 읽고 업로드 API는 없다.

function fileOf(bytes: Uint8Array<ArrayBuffer> | string, name = 'meeting.txt'): File {
  const part = typeof bytes === 'string' ? new TextEncoder().encode(bytes) : bytes
  return new File([part], name, { type: 'text/plain' })
}

// "김민수: 가" 를 CP949로 인코딩한 바이트.
// 한계: 테스트 환경(Node ICU)의 euc-kr 디코더는 CP949 확장 한글(똠·뷁 등)을 읽지 못한다.
// 브라우저의 euc-kr(windows-949)은 읽으므로 확장 한글은 W8 브라우저 수동 확인에서 본다.
const CP949_SAMPLE = new Uint8Array([0xb1, 0xe8, 0xb9, 0xce, 0xbc, 0xf6, 0x3a, 0x20, 0xb0, 0xa1])

describe('readScriptFile', () => {
  it('decodesUtf8', async () => {
    const result = await readScriptFile(fileOf('김민수: 시작합니다\n이영희: 네'))

    expect(result).toEqual({ ok: true, text: '김민수: 시작합니다\n이영희: 네' })
  })

  it('stripsUtf8Bom', async () => {
    const bom = new Uint8Array([0xef, 0xbb, 0xbf])
    const body = new TextEncoder().encode('김민수: 가')

    const result = await readScriptFile(fileOf(new Uint8Array([...bom, ...body])))

    expect(result.ok).toBe(true)
    if (result.ok) {
      // BOM(U+FEFF)은 보이지 않는 문자라 코드 값으로 쓴다
      expect(result.text.startsWith(String.fromCharCode(0xfeff))).toBe(false)
      expect(result.text).toBe('김민수: 가')
    }
  })

  // 한계: UTF-8을 먼저 시도하므로, 파일 전체가 UTF-8로도 해석되는 CP949 글자(징·짜·책 등 217자)와
  // ASCII로만 된 짧은 파일은 깨진 글자(예: 책 → å)로 읽힌다. 다른 한글이 한 글자라도 있으면 euc-kr로 넘어간다.
  // W8에서 읽은 내용이 입력란에 보이므로 사용자가 깨진 글자를 알아챌 수 있다.
  it('decodesCp949SameAsUtf8', async () => {
    const fromCp949 = await readScriptFile(fileOf(CP949_SAMPLE))
    const fromUtf8 = await readScriptFile(fileOf('김민수: 가'))

    expect(fromCp949).toEqual({ ok: true, text: '김민수: 가' })
    expect(fromCp949).toEqual(fromUtf8)
  })

  it('rejectsUndecodableBytes', async () => {
    // UTF-8로도 euc-kr로도 해석되지 않는 바이트
    const result = await readScriptFile(fileOf(new Uint8Array([0x41, 0xff, 0xff, 0x42])))

    expect(result).toEqual({ ok: false, reason: 'UNDECODABLE' })
  })

  it('rejectsNonTxt', async () => {
    for (const name of ['meeting.docx', 'meeting.hwp', 'meeting.srt', 'meeting', 'meeting.txt.exe']) {
      const result = await readScriptFile(fileOf('김민수: 가', name))

      expect(result, name).toEqual({ ok: false, reason: 'NOT_TXT' })
    }
  })

  it('acceptsUppercaseTxtExtension', async () => {
    const result = await readScriptFile(fileOf('김민수: 가', 'MEETING.TXT'))

    expect(result).toEqual({ ok: true, text: '김민수: 가' })
  })

  it('rejectsOver200Kb', async () => {
    const result = await readScriptFile(fileOf(new Uint8Array(MAX_FILE_BYTES + 1).fill(0x41)))

    expect(result).toEqual({ ok: false, reason: 'TOO_LARGE' })
  })

  it('acceptsExactly200Kb', async () => {
    const result = await readScriptFile(fileOf(new Uint8Array(MAX_FILE_BYTES).fill(0x41)))

    expect(result.ok).toBe(true)
  })

  it('rejectsNulBytes', async () => {
    const result = await readScriptFile(fileOf(new Uint8Array([0x41, 0x00, 0x42])))

    expect(result).toEqual({ ok: false, reason: 'CONTAINS_NUL' })
  })

  /** NUL 검사는 디코딩 전 바이트 단계: UTF-16 파일은 NUL 바이트가 있어 텍스트 파일이 아닌 것으로 본다. */
  it('rejectsUtf16FileAsContainingNul', async () => {
    const utf16le = new Uint8Array([0xff, 0xfe, 0x41, 0x00, 0x42, 0x00])

    const result = await readScriptFile(fileOf(utf16le))

    expect(result).toEqual({ ok: false, reason: 'CONTAINS_NUL' })
  })

  /** 확장자 검사가 크기 검사보다 먼저다. */
  it('checksExtensionBeforeSize', async () => {
    const result = await readScriptFile(fileOf(new Uint8Array(MAX_FILE_BYTES + 1).fill(0x41), 'meeting.docx'))

    expect(result).toEqual({ ok: false, reason: 'NOT_TXT' })
  })

  /** 크기를 넘는 파일은 내용을 읽지 않는다 (NUL 검사보다 먼저, 큰 파일을 메모리에 올리지 않음). */
  it('checksSizeBeforeReadingContent', async () => {
    const bytes = new Uint8Array(MAX_FILE_BYTES + 1).fill(0x41)
    bytes[0] = 0x00

    const result = await readScriptFile(fileOf(bytes))

    expect(result).toEqual({ ok: false, reason: 'TOO_LARGE' })
  })

  it('limitsMatchServer', () => {
    // SPEC S3: 200KB = 204,800바이트 (백엔드 app.max-request-bytes 기본값과 같다)
    expect(MAX_FILE_BYTES).toBe(204_800)
  })
})
