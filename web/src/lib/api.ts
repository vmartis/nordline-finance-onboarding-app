/** One row of GET /api/queue. No name, document number or birth date. */
export type QueueItem = {
  caseId: string
  documentType: string | null
  flags: string[]
  attempt: number
  deadline: string | null
  minutesLeft: number | null
}

export async function fetchQueue(): Promise<QueueItem[]> {
  const response = await fetch('/api/queue')
  if (!response.ok) throw new Error(`Queue request failed (${response.status})`)
  return response.json()
}
