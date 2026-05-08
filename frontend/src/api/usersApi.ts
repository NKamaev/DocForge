import api from './axiosInstance'
import type { UserDTO } from '../types/user'

export const usersApi = {
  searchByLogin: (login: string): Promise<UserDTO> =>
    api.get<UserDTO>('/users/search', { params: { login } }).then(r => r.data),
}
