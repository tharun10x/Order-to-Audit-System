import { ApiError } from '../api/client'

/**
 * UI guide section 6, Messages. ErrorBox: danger-tint background, danger text and border, with the backend's
 * detail lines (one per item) kept as it sent them. SuccessLine: one line of success text near the action.
 */
export function ErrorBox({ error, message }: { error?: unknown; message?: string }) {
  if (!error && !message) {
    return null
  }
  const text = message ?? (error instanceof Error ? error.message : 'Something went wrong. Try again.')
  const details = error instanceof ApiError ? error.details : []
  return (
    <div role="alert" className="rounded-control border border-danger bg-danger-tint px-4 py-3 text-sm text-danger">
      <p>{text}</p>
      {details.length > 0 ? (
        <ul className="mt-1 list-disc pl-5">
          {details.map((detail) => (
            <li key={detail}>{detail}</li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

export function SuccessLine({ children }: { children: string }) {
  return (
    <p role="status" className="text-sm font-medium text-success">
      {children}
    </p>
  )
}

/** One sentence: what will appear here and how it gets created. */
export function EmptyState({ children }: { children: string }) {
  return <p className="py-6 text-sm text-muted">{children}</p>
}
