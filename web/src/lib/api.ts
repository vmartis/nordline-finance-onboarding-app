import { authHeader, clearSession, getSession, type Session } from './auth'
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
  /** Reviewer who took the case with "Take next". */
  claimedBy: string | null
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
  /** Other cases with the same document number, in attempt order. */
  otherAttempts: { caseId: string; attempt: number; status: string; createdAt: string | null; flags: string[] }[]
}

export type ReasonCode = { code: string; label: string }

async function send(url: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(url, { ...init, headers: { ...init?.headers, ...authHeader() } })
  // Token missing or expired: back to the login screen.
  if (response.status === 401 && getSession()) clearSession('expired')
  return response
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await send(url, init)
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(errorMessage(body, response.status), response.status)
  }
  return response.json()
}

export const fetchQueue = () => request<QueueItem[]>('/api/queue')
export const fetchCase = (caseId: string) => request<CaseDetail>(`/api/cases/${encodeURIComponent(caseId)}`)
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

/** The case to work on next for the logged-in reviewer, or null when every pending case is taken (204). */
export async function takeNext(): Promise<string | null> {
  const response = await send('/api/claims/next', { method: 'POST' })
  if (response.status === 204) return null
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(errorMessage(body, response.status), response.status)
  }
  return ((await response.json()) as { caseId: string }).caseId
}

/** Wrong name or password comes back as an ApiError with a plain message. */
export async function login(username: string, password: string): Promise<Session> {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(errorMessage(body, response.status), response.status)
  return body as Session
}
