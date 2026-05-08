import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import ParticipantsPanel from './ParticipantsPanel'
import type { CollaborationUser } from '../../hooks/useCollaboration'

function makeUsers(count: number): CollaborationUser[] {
  return Array.from({ length: count }, (_, i) => ({
    clientId: i + 1,
    name: `User${i + 1}`,
    color: '#888',
  }))
}

describe('ParticipantsPanel', () => {
  it('renders all avatars when ≤3 remote users, no overflow badge', () => {
    render(
      <ParticipantsPanel
        isConnected
        connectedUsers={makeUsers(2)}
        currentUserName="Me"
      />
    )
    expect(screen.queryByText(/^\+\d/)).toBeNull()
  })

  it('renders exactly 3 avatars and +2 badge for 5 remote users', () => {
    render(
      <ParticipantsPanel
        isConnected
        connectedUsers={makeUsers(5)}
        currentUserName="Me"
      />
    )
    expect(screen.getByText('+2')).toBeTruthy()
  })

  it('does not render overflow badge for exactly 3 remote users', () => {
    render(
      <ParticipantsPanel
        isConnected
        connectedUsers={makeUsers(3)}
        currentUserName="Me"
      />
    )
    expect(screen.queryByText(/^\+\d/)).toBeNull()
  })

  it('renders +1 badge for 4 remote users', () => {
    render(
      <ParticipantsPanel
        isConnected
        connectedUsers={makeUsers(4)}
        currentUserName="Me"
      />
    )
    expect(screen.getByText('+1')).toBeTruthy()
  })
})
