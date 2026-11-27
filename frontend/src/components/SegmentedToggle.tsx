/** Two or three choices side by side, one selected (packets / cartons). A radio group, so keyboards work. */
interface Option<T extends string> {
  value: T
  label: string
}

interface SegmentedToggleProps<T extends string> {
  label: string
  options: Option<T>[]
  value: T
  onChange: (value: T) => void
}

export function SegmentedToggle<T extends string>({ label, options, value, onChange }: SegmentedToggleProps<T>) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex rounded-control border border-border">
      {options.map((option, index) => {
        const selected = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(option.value)}
            className={`min-h-13 min-w-24 px-4 text-base ${index > 0 ? 'border-l border-border' : ''} ${
              selected ? 'bg-primary-tint font-semibold text-primary' : 'bg-surface text-ink'
            }`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
