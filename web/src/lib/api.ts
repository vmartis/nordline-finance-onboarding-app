import { errorMessage, type DecisionInput } from './decision'

/** One row of GET /api/queue. No name, document number or birth date. */
export type QueueItem = {
  caseId: string
  documentType: string | null
  flags: string[]
  attempt: number
  deadline: string | null
  minutesLeft: number | null
  /** Plain-English notes on what was wrong in the export. */
  dataProblems: string[]
}

/** GET /api/cases/{id}: the opened case, the only place with personal data. */
export type CaseDetail = {
  caseId: string
  status: string
  createdAt: string | null
  slaHours: number
  fullName: string | null
  documentType: string | null
  documentNumber: string | null
  dateOfBirth: string | null
  attempt: number
  flags: string[]
  scores: { documentAuthenticity: number | null; faceMatch: number | null; liveness: number | null }
  notes: string | null
  dataProblems: string[]
}

export type Reviewer = { id: string; team: string; shift: string }
export type ReasonCode = { code: string; label: string }

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(errorMessage(body, response.status), response.status)
  }
  return response.json()
}

export const fetchQueue = () => request<QueueItem[]>('/api/queue')
export const fetchCase = (caseId: string) => request<CaseDetail>(`/api/cases/${encodeURIComponent(caseId)}`)
export const fetchReviewers = () => request<Reviewer[]>('/api/reviewers')
export const fetchReasonCodes = () => request<ReasonCode[]>('/api/reason-codes')

/** Thrown for a non-OK answer; status lets the screen react (e.g. 409 = already decided). */
export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

export const postDecision = (caseId: string, decision: DecisionInput, idempotencyKey: string) =>
  request<{ caseId: string; status: string }>(`/api/cases/${encodeURIComponent(caseId)}/decision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(decision),
  })
