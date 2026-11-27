import { Minus, Plus } from 'lucide-react'

/** UI guide section 4: quantity stepper, 52 × 52 buttons, whole numbers only. The number can also be typed. */
interface StepperProps {
  value: number
  onChange: (value: number) => void
  label: string
  unitLabel: string
  max?: number
}

export function Stepper({ value, onChange, label, unitLabel, max = 99999 }: StepperProps) {
  const set = (next: number) => onChange(Math.min(max, Math.max(0, Math.floor(next))))
  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        aria-label={`One less ${unitLabel}`}
        onClick={() => set(value - 1)}
        disabled={value <= 0}
        className="flex size-13 items-center justify-center rounded-control border border-border bg-surface text-ink disabled:opacity-50"
      >
        <Minus size={20} aria-hidden="true" />
      </button>
      <input
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        aria-label={label}
        value={String(value)}
        onChange={(event) => {
          const digits = event.target.value.replace(/\D/g, '')
          set(digits === '' ? 0 : Number(digits))
        }}
        className="num h-13 w-24 rounded-control border border-border bg-surface text-center text-lg font-semibold"
      />
      <button
        type="button"
        aria-label={`One more ${unitLabel}`}
        onClick={() => set(value + 1)}
        className="flex size-13 items-center justify-center rounded-control border border-border bg-surface text-ink"
      >
        <Plus size={20} aria-hidden="true" />
      </button>
    </div>
  )
}
