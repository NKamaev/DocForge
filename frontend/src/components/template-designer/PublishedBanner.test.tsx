import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import PublishedBanner from './PublishedBanner'

describe('PublishedBanner', () => {
  it('renders the read-only warning message', () => {
    render(<PublishedBanner />)
    expect(screen.getByText(/шаблон в режиме только для чтения/i)).toBeInTheDocument()
  })

  it('renders the lock icon', () => {
    render(<PublishedBanner />)
    // lucide-react Lock renders as an SVG
    expect(document.querySelector('svg.lucide.lucide-lock')).not.toBeNull()
  })
})
