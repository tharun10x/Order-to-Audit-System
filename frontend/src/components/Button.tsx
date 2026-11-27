import type { ButtonHTMLAttributes } from 'react'

/**
 * UI guide section 6. primary: one per screen area. secondary: white with a border. danger: white with red
 * text (Reject, Cancel). Heights: md 44 (desktop), lg 52 (salesman), xl 56 (salesman's main action).
 * While loading, the label stays and gets "…" (no spinner replacing the text).
 */
type Variant = 'primary' | 'secondary' | 'danger'
type Size = 'md' | 'lg' | 'xl'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-primary text-surface border border-primary hover:bg-primary-hover hover:border-primary-hover',
  secondary: 'bg-surface text-ink border border-border hover:border-ink',
  danger: 'bg-surface text-danger border border-border hover:border-danger',
}

const SIZES: Record<Size, string> = {
  md: 'min-h-11 px-4 text-sm',
  lg: 'min-h-13 px-5 text-base',
  xl: 'min-h-14 px-5 text-base',
}

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export function Button({ variant = 'secondary', size = 'md', loading = false, className = '', children, disabled, ...rest }: ButtonProps) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={`rounded-control font-medium ${VARIANTS[variant]} ${SIZES[size]} disabled:cursor-not-allowed disabled:opacity-50 ${className}`}
    >
      {children}
      {loading ? '…' : null}
    </button>
  )
}
