import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './authStore'
import type { LoginResponse, UserDTO } from '../types/user'

const LOGIN_RESPONSE: LoginResponse = {
  token: 'jwt-token',
  userId: 'u-1',
  login: 'ivan',
  displayName: 'Иван Иванов',
  admin: false,
}

const PROFILE: UserDTO = {
  id: 'u-1',
  login: 'ivan',
  displayName: 'Иван Иванов',
  admin: true,
  createdAt: '2026-01-01T00:00:00Z',
}

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({
      token: null,
      user: null,
      isAuthenticated: false,
      isUserResolved: false,
    })
  })

  it('marks the user as resolved right after login', () => {
    useAuthStore.getState().setAuth(LOGIN_RESPONSE)
    const state = useAuthStore.getState()
    expect(state.isUserResolved).toBe(true)
    expect(state.user?.id).toBe('u-1')
    expect(state.isAuthenticated).toBe(true)
  })

  it('marks the user as resolved when the profile is loaded', () => {
    useAuthStore.getState().setUser(PROFILE)
    const state = useAuthStore.getState()
    expect(state.isUserResolved).toBe(true)
    expect(state.user?.admin).toBe(true)
  })

  it('markUserResolved resolves the identity without inventing a user', () => {
    useAuthStore.getState().markUserResolved()
    const state = useAuthStore.getState()
    expect(state.isUserResolved).toBe(true)
    expect(state.user).toBeNull()
  })

  it('forgets the resolved identity on logout', () => {
    useAuthStore.getState().setAuth(LOGIN_RESPONSE)
    useAuthStore.getState().logout()
    const state = useAuthStore.getState()
    expect(state.isUserResolved).toBe(false)
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
    expect(localStorage.getItem('token')).toBeNull()
  })
})
