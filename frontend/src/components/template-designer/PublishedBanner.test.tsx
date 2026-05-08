import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import PublishedBanner from './PublishedBanner'

describe('PublishedBanner', () => {
  it('renders the read-only warning message', () => {
    render(<PublishedBanner />)
    expect(screen.getByText(/шаблон в режиме только для чтения/i)).toBeInTheDocument()
  })

  it('renders the lock icon (role img)', () => {
    render(<PublishedBanner />)
    expect(document.querySelector('.anticon-lock')).not.toBeNull()
  })
})
