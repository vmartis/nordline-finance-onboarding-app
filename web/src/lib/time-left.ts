export type TimeLeftStatus = 'ok' | 'soon' | 'overdue' | 'unknown'

/** Minutes left until the SLA deadline (negative = overdue, null = unknown) as a label and a status; soon = due within 1 h. */
export function timeLeft(minutes: number | null): { label: string; status: TimeLeftStatus } {
  if (minutes === null) return { label: 'unknown (data problem)', status: 'unknown' }
  const m = Math.abs(minutes)
  const text = `${Math.floor(m / 60)} h ${String(m % 60).padStart(2, '0')} min`
  if (minutes < 0) return { label: `overdue ${text}`, status: 'overdue' }
  return { label: text, status: minutes < 60 ? 'soon' : 'ok' }
}

export type RowTone = 'danger' | 'warning' | 'none'

/** Whole-row highlight in the queue: danger under 1 h (or overdue), warning under 2 h. */
export function rowTone(minutes: number | null): RowTone {
  if (minutes === null) return 'none'
  const { status } = timeLeft(minutes) // same 1 h rule as the badges and the backlog strip
  if (status === 'overdue' || status === 'soon') return 'danger'
  return minutes < 120 ? 'warning' : 'none'
}
