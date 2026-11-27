import type { ErrorResponse } from './types'

/**
 * The one way the app talks to the backend. Adds the login token, and turns every failure into an ApiError
 * with a sentence a person can read plus the backend's detail lines (one per item, kept as sent).
 */

export type ApiErrorKind = 'http' | 'network'

export class ApiError extends Error {
  readonly status: number
  readonly details: string[]
  readonly kind: ApiErrorKind

  constructor(message: string, status: number, details: string[], kind: ApiErrorKind) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.details = details
    this.kind = kind
  }

  /** The request never got an answer: nothing is known to have been saved. */
  get isNetwork(): boolean {
    return this.kind === 'network'
  }
}

export const NETWORK_MESSAGE = 'No connection to the server. Nothing was saved. Check the network and try again.'
export const FORBIDDEN_MESSAGE = 'Your role is not allowed to do this.'
const SERVER_MESSAGE = 'The server could not finish this. Try again; if it keeps failing, tell the admin.'
const VALIDATION_MESSAGE = 'Some values need correcting:'

interface ApiConfig {
  getToken: () => string | null
  /** Called on a 401 for any request that sent a token: the session is over. */
  onUnauthorized: () => void
}

let config: ApiConfig = { getToken: () => null, onUnauthorized: () => {} }

export function configureApi(next: ApiConfig): void {
  config = next
}

export interface RequestOptions {
  /** The login call: a 401 there means a wrong password, not an ended session. */
  anonymous?: boolean
}

export async function request<T>(method: string, path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = options.anonymous ? null : config.getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(NETWORK_MESSAGE, 0, [], 'network')
  }

  if (response.ok) {
    if (response.status === 204) {
      return undefined as T
    }
    return (await response.json()) as T
  }

  const error = await toApiError(response)
  if (response.status === 401 && token) {
    config.onUnauthorized()
  }
  throw error
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) => request<T>('POST', path, body, options),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
}

/** Reads the backend's ErrorResponse; a body that is not one (a proxy error page) still gives a sentence. */
async function toApiError(response: Response): Promise<ApiError> {
  let body: Partial<ErrorResponse> = {}
  try {
    body = (await response.json()) as Partial<ErrorResponse>
  } catch {
    // Not JSON: fall through to the general message for the status
  }
  const details = Array.isArray(body.details) ? body.details : []
  return new ApiError(messageFor(response.status, body.message), response.status, details, 'http')
}

function messageFor(status: number, backendMessage: string | undefined): string {
  if (status === 403) {
    // The backend's own 403 sentence is specific ("Customer X is not assigned to you"); keep it when there is one
    return backendMessage && backendMessage !== 'Your role is not allowed to do this' ? backendMessage : FORBIDDEN_MESSAGE
  }
  if (status >= 500 || !backendMessage) {
    return SERVER_MESSAGE
  }
  if (backendMessage === 'Validation failed') {
    return VALIDATION_MESSAGE
  }
  return backendMessage.endsWith('.') ? backendMessage : `${backendMessage}.`
}
