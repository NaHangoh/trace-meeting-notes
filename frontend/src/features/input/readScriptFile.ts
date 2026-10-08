import { MAX_FILE_BYTES } from './limits'

// SPEC F1 파일 읽기 (프론트). 파일은 브라우저에서 읽어 입력란에 채운다. 업로드 API는 없다.

export type ReadFailure = 'NOT_TXT' | 'TOO_LARGE' | 'CONTAINS_NUL' | 'UNDECODABLE'

export type ReadResult = { ok: true; text: string } | { ok: false; reason: ReadFailure }

/**
 * 검사 순서: 확장자(.txt, 대소문자 무시) → 크기(200KB 이하) → NUL 바이트(디코딩 전) →
 * UTF-8(fatal, BOM 제거) → euc-kr(fatal). 둘 다 실패하면 UNDECODABLE.
 */
export async function readScriptFile(file: File): Promise<ReadResult> {
  if (!file.name.toLowerCase().endsWith('.txt')) {
    return { ok: false, reason: 'NOT_TXT' }
  }
  if (file.size > MAX_FILE_BYTES) {
    return { ok: false, reason: 'TOO_LARGE' }
  }
  const bytes = new Uint8Array(await file.arrayBuffer())
  // NUL 바이트가 있으면 텍스트 파일이 아닌 것으로 본다 (UTF-16 파일 포함)
  if (bytes.includes(0)) {
    return { ok: false, reason: 'CONTAINS_NUL' }
  }
  const text = decode(bytes, 'utf-8') ?? decode(bytes, 'euc-kr')
  return text === null ? { ok: false, reason: 'UNDECODABLE' } : { ok: true, text }
}

/** fatal 디코더로 읽는다. 잘못된 바이트가 있으면 null. UTF-8 BOM은 기본 설정(ignoreBOM: false)으로 제거된다. */
function decode(bytes: Uint8Array, encoding: 'utf-8' | 'euc-kr'): string | null {
  try {
    return new TextDecoder(encoding, { fatal: true }).decode(bytes)
  } catch {
    return null
  }
}
