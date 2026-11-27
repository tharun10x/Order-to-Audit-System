/** ISO dates (yyyy-mm-dd), handled as text so no time zone can shift them. */

/** The first day of the month of an ISO date: "2026-11-28" → "2026-11-01". */
export function firstOfMonth(isoDate: string): string {
  return `${isoDate.slice(0, 7)}-01`
}

/** The last day of the month of an ISO date: "2026-11-28" → "2026-11-30". */
export function lastOfMonth(isoDate: string): string {
  const [year, month] = isoDate.split('-').map(Number)
  const days = new Date(Date.UTC(year, month, 0)).getUTCDate()
  return `${isoDate.slice(0, 7)}-${String(days).padStart(2, '0')}`
}

/** An instant from the API ("2026-11-02T18:30:00Z") as the factory's calendar date (IST). */
export function istDate(instant: string): string {
  return new Date(instant).toLocaleDateString('en-CA', { timeZone: 'Asia/Kolkata' })
}
