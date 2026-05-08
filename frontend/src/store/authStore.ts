import { create } from 'zustand'
import type { LoginResponse, UserDTO } from '../types/user'

interface AuthState {
  token: string | null
  user: UserDTO | null
  isAuthenticated: boolean
  setAuth: (response: LoginResponse) => void
  setUser: (user: UserDTO) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('token'),
  user: null,
  isAuthenticated: !!localStorage.getItem('token'),

  setAuth: (response: LoginResponse) => {
    localStorage.setItem('token', response.token)
    set({
      token: response.token,
      isAuthenticated: true,
      user: {
        id: response.userId,
        login: response.login,
        displayName: response.displayName,
        admin: response.admin,
        createdAt: new Date().toISOString(),
      },
    })
  },

  setUser: (user: UserDTO) => set({ user }),

  logout: () => {
    localStorage.removeItem('token')
    set({ token: null, user: null, isAuthenticated: false })
  },
}))
