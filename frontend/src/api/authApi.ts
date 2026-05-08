import api from './axiosInstance'
import type { LoginRequest, LoginResponse, RegisterRequest, UserDTO } from '../types/user'

export const authApi = {
  login: (data: LoginRequest) =>
    api.post<LoginResponse>('/users/login', data).then((r) => r.data),

  register: (data: RegisterRequest) =>
    api.post<LoginResponse>('/users/register', data).then((r) => r.data),

  me: () =>
    api.get<UserDTO>('/users/me').then((r) => r.data),
}
