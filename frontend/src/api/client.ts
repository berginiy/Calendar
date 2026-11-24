import axios, { AxiosError } from 'axios'
import type { ApiError } from '../types/api'

const TOKEN_KEY = 'office-booking.token'

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export const api = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '/api' })

let onUnauthorized: (() => void) | null = null
export const setUnauthorizedHandler = (fn: (() => void) | null) => {
  onUnauthorized = fn
}

api.interceptors.request.use((config) => {
  const token = tokenStorage.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (r) => r,
  (error: AxiosError<ApiError>) => {
    // 401 на странице входа — это неверный пароль, а не истёкшая сессия
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      onUnauthorized?.()
    }
    return Promise.reject(error)
  },
)

/** Человекочитаемое сообщение об ошибке запроса (на русском, без технических деталей). */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    if (!error.response) return 'Нет связи с сервером. Проверьте подключение и повторите попытку'
    const data = error.response.data
    if (data?.message) return data.message
    if (error.response.status === 403) return 'Недостаточно прав для этого действия'
    if (error.response.status >= 500) return 'Внутренняя ошибка сервера. Повторите попытку позже'
  }
  return 'Не удалось выполнить действие'
}
