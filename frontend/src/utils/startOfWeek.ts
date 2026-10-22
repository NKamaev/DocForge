/**
 * Начало текущей недели (понедельник, 00:00 локального времени).
 * Используется для счётчиков «за неделю» на главной странице.
 */
export function startOfWeek(date: Date = new Date()): Date {
  const result = new Date(date)
  result.setHours(0, 0, 0, 0)
  // getDay(): 0 = воскресенье, 1 = понедельник … 6 = суббота
  const daysSinceMonday = (result.getDay() + 6) % 7
  result.setDate(result.getDate() - daysSinceMonday)
  return result
}
