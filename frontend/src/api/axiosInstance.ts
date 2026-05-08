import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Only redirect on 401 for authenticated requests.
    // A 401 from /users/login or /users/register means wrong credentials — let
    // the individual handler show its own error message instead of force-navigating.
    const url: string = error.config?.url ?? ''
    const isAuthEndpoint = url.includes('/users/login') || url.includes('/users/register')
    if (error.response?.status === 401 && !isAuthEndpoint) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default api
