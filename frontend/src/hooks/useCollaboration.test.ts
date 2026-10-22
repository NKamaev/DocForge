import { describe, it, expect } from 'vitest'
import { deduplicateByName, buildWsUrl } from './useCollaboration'
import type { CollaborationUser } from './useCollaboration'

describe('buildWsUrl', () => {
  it('returns VITE_WS_URL when set', () => {
    expect(
      buildWsUrl(
        { VITE_WS_URL: 'wss://production.example.com' },
        { protocol: 'http:', host: 'example.com' },
      ),
    ).toBe('wss://production.example.com')
  })

  it('derives ws:// from http: location when env not set', () => {
    expect(
      buildWsUrl({ VITE_WS_URL: '', DEV: false }, { protocol: 'http:', host: 'example.com' }),
    ).toBe('ws://example.com')
  })

  it('derives wss:// from https: location when env not set', () => {
    expect(
      buildWsUrl(
        { VITE_WS_URL: '', DEV: false },
        { protocol: 'https:', host: 'secure.example.com' },
      ),
    ).toBe('wss://secure.example.com')
  })

  it('returns dev server URL when DEV is true, ignoring location', () => {
    expect(
      buildWsUrl({ VITE_WS_URL: '', DEV: true }, { protocol: 'https:', host: 'secure.example.com' }),
    ).toBe('ws://localhost:8080')
  })

  it('prefers VITE_WS_URL over DEV mode', () => {
    expect(
      buildWsUrl(
        { VITE_WS_URL: 'wss://production.example.com', DEV: true },
        { protocol: 'http:', host: 'example.com' },
      ),
    ).toBe('wss://production.example.com')
  })

  it('defaults to the dev backend in the test environment', () => {
    expect(buildWsUrl()).toBe('ws://localhost:8080')
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
