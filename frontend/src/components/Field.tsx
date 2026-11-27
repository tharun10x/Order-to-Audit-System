import { useId, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'

/** UI guide section 6, Forms: label above, input 44 px (52 on the salesman's phone), error below in danger. */
const INPUT = 'w-full rounded-control border border-border bg-surface px-3 text-ink placeholder:text-muted'

interface FieldProps {
  label: string
  error?: string
  hint?: string
  children: (props: { id: string; describedBy?: string; invalid: boolean }) => ReactNode
}

export function Field({ label, error, hint, children }: FieldProps) {
  const id = useId()
  const messageId = `${id}-message`
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm text-muted">
        {label}
      </label>
      {children({ id, describedBy: error || hint ? messageId : undefined, invalid: Boolean(error) })}
      {error ? (
        <p id={messageId} className="text-[13px] leading-[18px] text-danger">
          {error}
        </p>
      ) : hint ? (
        <p id={messageId} className="text-[13px] leading-[18px] text-muted">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  large?: boolean
}

export function TextInput({ large = false, className = '', ...rest }: InputProps) {
  return <input {...rest} className={`${INPUT} ${large ? 'min-h-13 text-base' : 'min-h-11 text-sm'} ${className}`} />
}

export function TextArea({ className = '', ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...rest} className={`${INPUT} min-h-20 py-2 text-sm ${className}`} />
}
