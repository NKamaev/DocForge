import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { deduplicateByName, buildWsUrl } from './useCollaboration'
import type { CollaborationUser } from './useCollaboration'

describe('buildWsUrl', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { protocol: 'http:', host: 'example.com' },
    })
  })

  afterEach(() => {
    vi.unstubAllEnvs()
  })

  it('returns VITE_WS_URL when set', () => {
    vi.stubEnv('VITE_WS_URL', 'wss://production.example.com')
    expect(buildWsUrl()).toBe('wss://production.example.com')
  })

  it('derives ws:// from http: location when env not set', () => {
    vi.stubEnv('VITE_WS_URL', '')
    expect(buildWsUrl()).toBe('ws://example.com')
  })

  it('derives wss:// from https: location when env not set', () => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { protocol: 'https:', host: 'secure.example.com' },
    })
    vi.stubEnv('VITE_WS_URL', '')
    expect(buildWsUrl()).toBe('wss://secure.example.com')
  })
})

describe('deduplicateByName', () => {
  it('returns empty array for empty input', () => {
    expect(deduplicateByName([])).toEqual([])
  })

  it('keeps single entry unchanged', () => {
    const users: CollaborationUser[] = [{ clientId: 1, name: 'Alice', color: '#f00' }]
    expect(deduplicateByName(users)).toEqual(users)
  })

  it('deduplicates two entries with the same name, keeping highest clientId', () => {
    const users: CollaborationUser[] = [
      { clientId: 10, name: 'Bob', color: '#00f' },
      { clientId: 20, name: 'Bob', color: '#00f' },
    ]
    const result = deduplicateByName(users)
    expect(result).toHaveLength(1)
    expect(result[0].clientId).toBe(20)
  })

  it('keeps distinct names separately', () => {
    const users: CollaborationUser[] = [
      { clientId: 1, name: 'Alice', color: '#f00' },
      { clientId: 2, name: 'Bob',   color: '#00f' },
    ]
    expect(deduplicateByName(users)).toHaveLength(2)
  })

  it('handles multiple duplicates across different names', () => {
    const users: CollaborationUser[] = [
      { clientId: 5,  name: 'A', color: '#f00' },
      { clientId: 15, name: 'A', color: '#f00' },
      { clientId: 3,  name: 'B', color: '#0f0' },
      { clientId: 9,  name: 'B', color: '#0f0' },
    ]
    const result = deduplicateByName(users)
    expect(result).toHaveLength(2)
    expect(result.find(u => u.name === 'A')?.clientId).toBe(15)
    expect(result.find(u => u.name === 'B')?.clientId).toBe(9)
  })
})
