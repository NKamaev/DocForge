import { create } from 'zustand'
import type { LoginResponse, UserDTO } from '../types/user'

interface AuthState {
  token: string | null
  user: UserDTO | null
  isAuthenticated: boolean
  /**
   * Известна ли личность текущего пользователя.
   *
   * `false` — профиль ещё не загружен (после перезагрузки страницы известен
   * только токен, а `GET /users/me` ещё не пришёл). Пока это так, права на
   * ресурс определить нельзя: нельзя ни показать действия владельца, ни
   * объявить «вы не владелец» — иначе у владельца мигали бы кнопки
   * «Сохранить»/«Опубликовать» (см. `TemplateDesigner`).
   */
  isUserResolved: boolean
  setAuth: (response: LoginResponse) => void
  setUser: (user: UserDTO) => void
  /**
   * Пометить профиль как «выяснен» без смены его значения.
   *
   * Нужен, когда запрос профиля завершился неуспехом (временная сетевая
   * ошибка): личность остаётся неизвестной, но ждать её бесконечно нельзя —
   * иначе интерфейс зависнет. Права при этом остаются закрытыми
   * (fail-closed): `user == null` → действий владельца не предлагаем.
   */
  markUserResolved: () => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('token'),
  user: null,
  isAuthenticated: !!localStorage.getItem('token'),
  isUserResolved: false,

  setAuth: (response: LoginResponse) => {
    localStorage.setItem('token', response.token)
    set({
      token: response.token,
      isAuthenticated: true,
      isUserResolved: true,
      user: {
        id: response.userId,
        login: response.login,
        displayName: response.displayName,
        admin: response.admin,
        createdAt: new Date().toISOString(),
      },
    })
  },

  setUser: (user: UserDTO) => {
    set({ user, isUserResolved: true })
  },

  markUserResolved: () => {
    set({ isUserResolved: true })
  },

  logout: () => {
    localStorage.removeItem('token')
    set({ token: null, user: null, isAuthenticated: false, isUserResolved: false })
  },
}))
