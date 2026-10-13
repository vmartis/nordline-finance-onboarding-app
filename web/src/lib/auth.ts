import { useSyncExternalStore } from 'react'

/** The login token lives only in memory: closing or reloading the tab logs out, and no script can read it from storage. */
export type Session = { token: string; username: string; expiresAt: string }

let session: Session | null = null
const listeners = new Set<() => void>()

function notify() {
  listeners.forEach((listener) => listener())
}

export function getSession(): Session | null {
  if (session && Date.parse(session.expiresAt) <= Date.now()) session = null
  return session
}

export function setSession(next: Session) {
  session = next
  notify()
}

export function clearSession() {
  session = null
  notify()
}

export function authHeader(): Record<string, string> {
  const current = getSession()
  return current ? { Authorization: `Bearer ${current.token}` } : {}
}

export function useSession(): Session | null {
  return useSyncExternalStore((listener) => {
    listeners.add(listener)
    return () => listeners.delete(listener)
  }, getSession)
}
