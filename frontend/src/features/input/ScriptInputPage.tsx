import { useMutation } from '@tanstack/react-query'
import { useRef, useState, type ChangeEvent } from 'react'
import { ApiError, createJob, type ApiErrorCode } from '../../api/client'
import { MAX_SCRIPT_CHARS } from './limits'
import { apiErrorMessage, BLANK_GUIDANCE, fileErrorMessage, JOB_CREATED } from './messages'
import { readScriptFile } from './readScriptFile'
import { scriptLength } from './scriptLength'
import { usePreview } from './usePreview'

/** 미리보기가 이 코드로 거부하면 서버가 작업 생성도 거부하므로 시작을 막는다. */
const BLOCKING_CODES: ReadonlySet<ApiErrorCode> = new Set([
  'BLANK',
  'NO_UTTERANCES',
  'TOO_LONG',
  'CONTAINS_NUL',
  'PAYLOAD_TOO_LARGE',
])

const numberFormat = new Intl.NumberFormat('ko-KR')

function errorCodeOf(error: unknown): ApiErrorCode {
  return error instanceof ApiError ? error.code : 'UNKNOWN'
}

/**
 * 입력 화면 (SPEC F1). 붙여 넣거나 txt 파일을 열고, 500ms 디바운스 미리보기로 요약 한 줄을 보여준 뒤 작업을 만든다.
 * 입력·화자 이름·안내 문구는 모두 일반 텍스트로 렌더링한다 (SPEC S6).
 */
export function ScriptInputPage() {
  const [text, setText] = useState('')
  const [fileError, setFileError] = useState<string | null>(null)

  const length = scriptLength(text)
  const blank = text.trim() === ''
  const overLimit = length > MAX_SCRIPT_CHARS
  const preview = usePreview(text, !blank && !overLimit)
  const previewCode = preview.error ? errorCodeOf(preview.error) : null
  const previewBlocks = previewCode !== null && BLOCKING_CODES.has(previewCode)

  // 화면이 다시 그려지기 전의 연속 클릭도 막도록 진행 중 여부를 동기적으로도 기록한다
  const jobInFlight = useRef(false)
  const job = useMutation({
    mutationFn: createJob,
    onSettled: () => {
      jobInFlight.current = false
    },
  })
  const jobPending = job.isPending

  const showBlankGuidance = blank || previewCode === 'BLANK'
  // 성공한 뒤에는 입력을 바꿔야(job.reset) 다시 시작할 수 있다. 같은 입력으로 작업이 두 번 생기지 않게 한다
  const startDisabled = blank || overLimit || previewBlocks || jobPending || job.isSuccess

  function changeText(next: string) {
    // 요청 중에는 입력을 바꾸지 않는다 (진행 중인 결과를 버리지 않음)
    if (jobInFlight.current) {
      return
    }
    setText(next)
    job.reset()
  }

  function start() {
    if (jobInFlight.current) {
      return
    }
    jobInFlight.current = true
    job.mutate(text)
  }

  async function openFile(event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget
    const file = input.files?.[0]
    // 같은 파일을 다시 골라도 change가 일어나게 비운다
    input.value = ''
    if (!file) {
      return
    }
    const result = await readScriptFile(file)
    if (result.ok) {
      setFileError(null)
      changeText(result.text)
    } else {
      setFileError(fileErrorMessage(result.reason))
    }
  }

  return (
    <section className="flex flex-col gap-3">
      <label className="flex flex-col gap-1">
        <span className="font-medium">회의 스크립트</span>
        <textarea
          className="min-h-64 w-full rounded border border-gray-300 p-2"
          value={text}
          readOnly={jobPending}
          onChange={(event) => changeText(event.target.value)}
        />
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span>txt 파일 열기</span>
        <input type="file" accept=".txt,text/plain" disabled={jobPending} onChange={openFile} />
      </label>

      {fileError && (
        <p role="alert" className="text-sm text-red-700">
          ⚠ {fileError}
        </p>
      )}

      {showBlankGuidance && <p className="text-sm text-gray-700">{BLANK_GUIDANCE}</p>}

      {overLimit && (
        <p className="text-sm text-red-700">
          ⚠ 글자 수 {numberFormat.format(length)} / 최대 {numberFormat.format(MAX_SCRIPT_CHARS)}자
        </p>
      )}

      {previewCode !== null && previewCode !== 'BLANK' && previewBlocks && (
        <p className="text-sm text-red-700">⚠ {apiErrorMessage(previewCode)}</p>
      )}

      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          className="rounded bg-blue-700 px-4 py-2 text-white disabled:bg-gray-400"
          disabled={startDisabled}
          onClick={start}
        >
          정리 시작
        </button>
        {preview.data && (
          <span className="text-sm text-gray-800">
            발언 {preview.data.utteranceCount}개 · 화자 {preview.data.speakers.length}명 (
            {preview.data.speakers.join(', ')})
          </span>
        )}
      </div>

      {job.isError && (
        <p role="alert" className="text-sm text-red-700">
          ⚠ {apiErrorMessage(errorCodeOf(job.error))}
        </p>
      )}
      {job.isSuccess && (
        <p role="status" className="text-sm text-green-800">
          ✓ {JOB_CREATED}
        </p>
      )}
    </section>
  )
}
