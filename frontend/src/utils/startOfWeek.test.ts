/**
 * BUG-044 — «Счетчик шаблонов за неделю на главной не работает».
 *
 * `startOfWeek` is the boundary used by `HomePage` for the
 * `createdAfter` query parameter, so it must be Monday 00:00 **local time**
 * for every input date of the current week, regardless of the day-of-week
 * offset (`getDay()` returns 0 for Sunday, which is the classic off-by-one).
 */
import { describe, it, expect } from 'vitest'
import { startOfWeek } from './startOfWeek'

/** Local-time date constructor shorthand (month is 0-based). */
function localDate(
  year: number,
  month: number,
  day: number,
  hours = 0,
  minutes = 0,
  seconds = 0,
  ms = 0,
): Date {
  return new Date(year, month, day, hours, minutes, seconds, ms)
}

function expectMondayMidnight(date: Date) {
  expect(date.getDay()).toBe(1) // Monday
  expect(date.getHours()).toBe(0)
  expect(date.getMinutes()).toBe(0)
  expect(date.getSeconds()).toBe(0)
  expect(date.getMilliseconds()).toBe(0)
}

describe('startOfWeek (BUG-044)', () => {
  it('given Thursday 2026-10-22 15:30, returns Monday 2026-10-19 00:00:00.000 (local)', () => {
    const thursday = localDate(2026, 9, 22, 15, 30)

    const result = startOfWeek(thursday)

    expect(result).toEqual(localDate(2026, 9, 19))
    expectMondayMidnight(result)
  })

  it('given Sunday 2026-10-25 23:59, returns the preceding Monday 2026-10-19 00:00', () => {
    const sunday = localDate(2026, 9, 25, 23, 59)

    const result = startOfWeek(sunday)

    // Sunday belongs to the week that started on the previous Monday —
    // getDay() === 0 must NOT be treated as the start of the week.
    expect(result).toEqual(localDate(2026, 9, 19))
    expectMondayMidnight(result)
  })

  it('given Monday 00:00, returns the same instant', () => {
    const monday = localDate(2026, 9, 19, 0, 0, 0, 0)

    const result = startOfWeek(monday)

    expect(result.getTime()).toBe(monday.getTime())
    expectMondayMidnight(result)
  })

  it('does not mutate the input Date', () => {
    const input = localDate(2026, 9, 22, 15, 30, 45, 123)
    const snapshot = input.getTime()

    const result = startOfWeek(input)

    expect(input.getTime()).toBe(snapshot)
    expect(result).not.toBe(input)
  })
})
