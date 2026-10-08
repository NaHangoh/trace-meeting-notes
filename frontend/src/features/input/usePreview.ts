import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { fetchPreview } from '../../api/client'

export const PREVIEW_DEBOUNCE_MS = 500

/**
 * 입력이 멈춘 뒤 500ms가 지나면 미리보기 API를 한 번 부른다 (SPEC F1).
 * enabled가 false인 입력(공백뿐, 글자 수 초과)은 부르지 않는다. 400·429를 다시 시도하지 않는다.
 */
export function usePreview(text: string, enabled: boolean) {
  const [debounced, setDebounced] = useState(text)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(text), PREVIEW_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [text])

  const settled = debounced === text
  const query = useQuery({
    queryKey: ['preview', debounced],
    queryFn: () => fetchPreview(debounced),
    enabled: enabled && settled,
    retry: false,
    staleTime: Infinity,
  })

  // 입력이 바뀐 뒤 아직 디바운스 중이면 이전 결과를 보여주지 않는다
  return {
    data: enabled && settled ? query.data : undefined,
    error: enabled && settled ? query.error : null,
  }
}
