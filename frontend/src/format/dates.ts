// Calendar days as `YYYY-MM-DD` strings, the format of the API (decision #22). The maths goes
// through UTC on purpose: `new Date('2026-10-25')` is UTC midnight, and mixing it with local-time
// getters shifts dates by a day around DST changes or west of Greenwich.

const DAY_MS = 24 * 60 * 60 * 1000

function toUtc(iso: string): number {
  const [year, month, day] = iso.split('-').map(Number) as [number, number, number]
  return Date.UTC(year, month - 1, day)
}

function fromUtc(ms: number): string {
  return new Date(ms).toISOString().slice(0, 10)
}

/** Today in the user's own time zone (the one date that has to be local) */
export function today(): string {
  const now = new Date()
  return fromUtc(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()))
}

export function addDays(iso: string, days: number): string {
  return fromUtc(toUtc(iso) + days * DAY_MS)
}

/** Monday = 0 … Sunday = 6 */
export function weekday(iso: string): number {
  return (new Date(toUtc(iso)).getUTCDay() + 6) % 7
}

export const yearOf = (iso: string) => Number(iso.slice(0, 4))

/** The first day of the month `iso` falls in */
export const monthStart = (iso: string) => `${iso.slice(0, 7)}-01`

/** The first day of the month `months` away from `iso`'s month */
export function addMonths(iso: string, months: number): string {
  const [year, month] = iso.split('-').map(Number) as [number, number]
  return fromUtc(Date.UTC(year, month - 1 + months, 1))
}

export function daysInMonth(iso: string): number {
  const [year, month] = iso.split('-').map(Number) as [number, number]
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

export const dayOfMonth = (iso: string) => Number(iso.slice(8, 10))

const monthYear = new Intl.DateTimeFormat('en-GB', {
  month: 'long',
  year: 'numeric',
  timeZone: 'UTC',
})

/** "October 2026" */
export const formatMonth = (iso: string) => monthYear.format(toUtc(iso))

const dayMonth = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  timeZone: 'UTC',
})
const dayOnly = new Intl.DateTimeFormat('en-GB', { day: 'numeric', timeZone: 'UTC' })

/** "21 Oct", "12–16 Oct", "30 Oct – 3 Nov" */
export function formatRange(from: string, to: string): string {
  if (from === to) {
    return dayMonth.format(toUtc(from))
  }
  const sameMonth = from.slice(0, 7) === to.slice(0, 7)
  return sameMonth
    ? `${dayOnly.format(toUtc(from))}–${dayMonth.format(toUtc(to))}`
    : `${dayMonth.format(toUtc(from))} – ${dayMonth.format(toUtc(to))}`
}

const number = new Intl.NumberFormat('en', { maximumFractionDigits: 1 })

/** 18.5 → "18.5", 3 → "3" */
export const formatNumber = (value: number) => number.format(value)

/** 1 → "1 day", 0.5 → "0.5 day", 5 → "5 days" */
export const formatDays = (days: number) => `${number.format(days)} ${days > 1 ? 'days' : 'day'}`

const fullDate = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

/** "26 Oct 2026" */
export const formatDate = (iso: string) => fullDate.format(toUtc(iso))

/** "26–27 Oct 2026", "30 Oct – 3 Nov 2026", "30 Dec 2026 – 2 Jan 2027" */
export function formatRangeWithYear(from: string, to: string): string {
  return yearOf(from) === yearOf(to)
    ? `${formatRange(from, to)} ${yearOf(to)}`
    : `${formatDate(from)} – ${formatDate(to)}`
}

const WEEKDAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

/** "Mon" */
export const weekdayName = (iso: string) => WEEKDAY_NAMES[weekday(iso)]!

const localDate = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
})

/** The day of an instant such as `createdAt`, in the user's time zone: "25 Sep 2026" */
export const formatInstantDate = (instant: string) => localDate.format(new Date(instant))
