import { timeLeft } from './time-left'

export type BacklogSummary = { overdue: number; soon: number; later: number; unknown: number }

/** Counts for the strip above the queue, using the same rules as the time badges. */
export function backlogSummary(items: { minutesLeft: number | null }[]): BacklogSummary {
  const summary: BacklogSummary = { overdue: 0, soon: 0, later: 0, unknown: 0 }
  for (const item of items) {
    const { status } = timeLeft(item.minutesLeft)
    if (status === 'ok') summary.later++
    else summary[status]++
  }
  return summary
}
