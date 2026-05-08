export interface LoginRequest {
  login: string
  password: string
}

export interface RegisterRequest {
  login: string
  password: string
  displayName: string
  email?: string
}

export interface LoginResponse {
  token: string
  userId: string
  login: string
  displayName: string
  admin: boolean
}

export interface UserDTO {
  id: string
  login: string
  displayName: string
  email?: string
  admin: boolean
  createdAt: string
}
