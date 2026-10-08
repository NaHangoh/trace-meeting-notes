import { createElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('smoke', () => {
  it('앱이 렌더링된다', () => {
    // 입력 화면이 React Query를 쓰므로 main.tsx와 같이 QueryClientProvider로 감싼다
    render(createElement(QueryClientProvider, { client: new QueryClient() }, createElement(App)))

    expect(screen.getByRole('heading', { name: '회의 정리 노트' })).toBeInTheDocument()
  })
})
