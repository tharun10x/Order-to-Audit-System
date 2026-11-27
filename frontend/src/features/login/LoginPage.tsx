import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router'

import { useAuth } from '../../auth/authContext'
import { roleHome } from '../../auth/roleHome'
import { sessionExpired } from '../../auth/session'
import { Button } from '../../components/Button'
import { Field, TextInput } from '../../components/Field'
import { ErrorBox } from '../../components/Messages'

/** Sign in, then go to the role's start page. Works on a phone (52 px controls) and on a desktop. */
export function LoginPage() {
  const { session, signIn } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [missing, setMissing] = useState(false)
  const [busy, setBusy] = useState(false)

  if (session) {
    return <Navigate to={roleHome(session.user.role)} replace />
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!username.trim() || !password) {
      setMissing(true)
      return
    }
    setMissing(false)
    setError(null)
    setBusy(true)
    try {
      const next = await signIn(username.trim(), password)
      navigate(roleHome(next.user.role), { replace: true })
    } catch (failure) {
      setError(failure)
      setBusy(false)
    }
  }

  return (
    <main className="flex min-h-screen items-start justify-center px-4 py-12 sm:items-center">
      <form onSubmit={submit} noValidate className="w-full max-w-sm rounded-panel border border-border bg-surface p-6">
        <h1 className="text-xl leading-7 font-semibold sm:text-2xl sm:leading-8">Order-to-Audit</h1>
        <p className="mt-1 mb-6 text-sm text-muted">Sign in to continue.</p>
        {sessionExpired() ? (
          <p className="mb-4 text-sm text-ink">Your session ended. Sign in again.</p>
        ) : null}
        <div className="flex flex-col gap-4">
          <Field label="Username" error={missing && !username.trim() ? 'Enter your username.' : undefined}>
            {({ id, describedBy, invalid }) => (
              <TextInput
                id={id}
                large
                autoComplete="username"
                autoCapitalize="none"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
                aria-describedby={describedBy}
                aria-invalid={invalid}
              />
            )}
          </Field>
          <Field label="Password" error={missing && !password ? 'Enter your password.' : undefined}>
            {({ id, describedBy, invalid }) => (
              <TextInput
                id={id}
                large
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                aria-describedby={describedBy}
                aria-invalid={invalid}
              />
            )}
          </Field>
          <ErrorBox error={error} />
          <Button type="submit" variant="primary" size="lg" loading={busy}>
            Sign in
          </Button>
        </div>
      </form>
    </main>
  )
}
