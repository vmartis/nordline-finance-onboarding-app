export type TimeLeftStatus = 'ok' | 'overdue' | 'unknown'

/** Minutes left until the SLA deadline (negative = overdue, null = unknown) as a label and a status. */
export function timeLeft(minutes: number | null): { label: string; status: TimeLeftStatus } {
  if (minutes === null) return { label: 'unknown (data problem)', status: 'unknown' }
  const m = Math.abs(minutes)
  const text = `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`
  return minutes < 0 ? { label: `overdue ${text}`, status: 'overdue' } : { label: text, status: 'ok' }
}
