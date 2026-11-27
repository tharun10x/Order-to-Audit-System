import type { User } from '../api/types'

/**
 * The login session: kept in memory and in sessionStorage, so a page reload keeps you signed in but closing the
 * tab ends it. Sign out clears both.
 */
export interface Session {
  token: string
  expiresAt: string
  businessDate: string
  user: User
}

const KEY = 'ota.session'
/** Set when the server ended the session (a 401), so the login page can say why. */
const EXPIRED_KEY = 'ota.sessionExpired'

let current: Session | null = null
const listeners = new Set<() => void>()

/** Called whenever the session is cleared, so the screens can follow (for example after a 401). */
export function onSessionCleared(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export function loadSession(): Session | null {
  if (current) {
    return current
  }
  const stored = sessionStorage.getItem(KEY)
  if (!stored) {
    return null
  }
  try {
    current = JSON.parse(stored) as Session
  } catch {
    sessionStorage.removeItem(KEY)
    return null
  }
  return current
}

export function saveSession(session: Session): void {
  current = session
  sessionStorage.setItem(KEY, JSON.stringify(session))
  sessionStorage.removeItem(EXPIRED_KEY)
}

/** expired: the server ended the session (as opposed to Sign out). */
export function clearSession(options: { expired?: boolean } = {}): void {
  current = null
  sessionStorage.removeItem(KEY)
  if (options.expired) {
    sessionStorage.setItem(EXPIRED_KEY, '1')
  }
  listeners.forEach((listener) => listener())
}

export function sessionExpired(): boolean {
  return sessionStorage.getItem(EXPIRED_KEY) !== null
}

export function currentToken(): string | null {
  return loadSession()?.token ?? null
}
