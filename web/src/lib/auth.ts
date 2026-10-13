import { useSyncExternalStore } from 'react'

/** The login token lives only in memory: closing or reloading the tab logs out, and no script can read it from storage. */
export type Session = { token: string; username: string; expiresAt: string }

let session: Session | null = null
let ended: 'expired' | null = null
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
  ended = null
  notify()
}

/** Pass 'expired' when the server refused the token, so the login page can explain why. */
export function clearSession(reason: 'expired' | null = null) {
  session = null
  ended = reason
  notify()
}

/** Why the last session ended on its own, or null after a normal logout or a new login. */
export function endedBecause(): 'expired' | null {
  return ended
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
