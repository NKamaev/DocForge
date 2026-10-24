/**
 * Единый разбор ошибок, приходящих от backend.
 *
 * Backend отдаёт ошибки в формате RFC-7807 `ProblemDetail`, поэтому текст
 * лежит в `response.data.detail`; `response.data.message` — запасной вариант
 * (например, для нестандартных ответов). Если ничего нет — возвращается
 * `null`, вызывающий код подставит собственное сообщение по умолчанию.
 */

/** HTTP-статус ответа backend (`null`, если ответа не было — сетевая ошибка). */
export function getErrorStatus(error: unknown): number | null {
  if (!error || typeof error !== 'object' || !('response' in error)) return null
  const response = (error as { response?: { status?: unknown } }).response
  const status = response?.status
  return typeof status === 'number' ? status : null
}

/** Текст ошибки от backend: `response.data.detail`, затем `response.data.message`. */
export function extractServerMessage(error: unknown): string | null {
  if (!error || typeof error !== 'object' || !('response' in error)) return null
  const response = (error as { response?: { data?: unknown } }).response
  const data = response?.data
  if (!data || typeof data !== 'object') return null
  const payload = data as { detail?: unknown; message?: unknown }
  for (const key of ['detail', 'message'] as const) {
    const value = payload[key]
    if (typeof value === 'string' && value.trim()) return value
  }
  return null
}

/** Ответил ли backend `403 Forbidden` (нет доступа к ресурсу). */
export function isForbiddenError(error: unknown): boolean {
  return getErrorStatus(error) === 403
}

/** Ответил ли backend `404 Not Found`. */
export function isNotFoundError(error: unknown): boolean {
  return getErrorStatus(error) === 404
}
