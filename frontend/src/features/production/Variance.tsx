import { formatPercent } from '../../lib/format'

/**
 * R8.9: a variance, signed. Beyond the tolerance it is flagged: danger colour, weight 600, and the word "Flagged"
 * (status is never colour alone, UI guide section 2).
 */
export function Variance({ pct, flagged }: { pct: number | null; flagged: boolean }) {
  if (pct === null) {
    return flagged ? <span className="font-semibold text-danger">Flagged</span> : null
  }
  if (!flagged) {
    return <span className="num">{formatPercent(pct)}</span>
  }
  return <span className="num font-semibold text-danger">{`${formatPercent(pct)} Flagged`}</span>
}
