import { createElement } from 'react'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('smoke', () => {
  it('앱이 렌더링된다', () => {
    render(createElement(App))

    expect(screen.getByRole('heading', { name: '회의 정리 노트' })).toBeInTheDocument()
  })
})
