/**
 * The running total on the salesman's order screen: an ESTIMATE that is shown, never sent. The invoice the
 * backend raises is final. Same rules as the backend (R10.3, R10.4): per line, taxable = qty × rate rounded to
 * paise; within the state CGST = SGST = taxable × rate/2 %; otherwise IGST = taxable × rate %; each rounded half
 * up. Worked in whole paise so there are no floating-point surprises.
 */

export interface EstimateLine {
  packets: number
  /** Price per packet before GST, in rupees, as the price list sends it. */
  rate: number
  /** GST %, for example 5. */
  gstRate: number
}

export interface Estimate {
  taxable: number
  cgst: number
  sgst: number
  igst: number
  total: number
}

/** a ÷ b rounded half up, for whole numbers a ≥ 0 and b > 0. */
function divideHalfUp(a: number, b: number): number {
  return Math.floor((2 * a + b) / (2 * b))
}

const toPaise = (rupees: number) => Math.round(rupees * 100)
const toBasisPoints = (percent: number) => Math.round(percent * 100)

export function estimateOrder(lines: EstimateLine[], intraState: boolean): Estimate {
  let taxable = 0
  let cgst = 0
  let igst = 0
  for (const line of lines) {
    const lineTaxable = line.packets * toPaise(line.rate)
    taxable += lineTaxable
    const taxTimes10000 = lineTaxable * toBasisPoints(line.gstRate)
    if (intraState) {
      cgst += divideHalfUp(taxTimes10000, 20000) // half the rate
    } else {
      igst += divideHalfUp(taxTimes10000, 10000)
    }
  }
  const sgst = cgst
  return {
    taxable: taxable / 100,
    cgst: cgst / 100,
    sgst: sgst / 100,
    igst: igst / 100,
    total: (taxable + cgst + sgst + igst) / 100,
  }
}
