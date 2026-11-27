import { isAmount, isQuantity } from './decimal'

/**
 * The master-data checks the backend's MasterRules and ItemAdminService make (R5.1, R5.3, M1, M8), run in the
 * browser first so the admin sees what to fix under the field. The backend checks again; its message is shown if
 * it still refuses. Each function returns the message, or null when the value is fine.
 */

const CODE = /^[A-Z0-9][A-Z0-9_-]{0,29}$/
/** M1: state code, PAN (5 letters, 4 digits, 1 letter), entity number, Z, check character. */
const GSTIN = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/
const HSN = /^([0-9]{4}|[0-9]{6}|[0-9]{8})$/
const WHOLE = /^[0-9]+$/

/** Codes are saved in capitals ("murukku500" is MURUKKU500), as the backend does. */
export function normaliseCode(value: string): string {
  return value.trim().toUpperCase()
}

export function codeError(value: string, what: string): string | null {
  const code = normaliseCode(value)
  if (!code) {
    return `Enter the ${what} code, for example ${what === 'item' ? 'MURUKKU500' : 'RET-C'}.`
  }
  return CODE.test(code) ? null : 'Use up to 30 capital letters, digits, "-" or "_", starting with a letter or digit.'
}

export function nameError(value: string): string | null {
  return value.trim() ? null : 'Enter the name as it should appear on documents.'
}

/** M1: optional; if given, the layout and the state code it starts with. */
export function gstinError(value: string, stateCode: string): string | null {
  const gstin = value.trim().toUpperCase()
  if (!gstin) {
    return null
  }
  if (gstin.length !== 15) {
    return `A GSTIN has 15 characters; this one has ${gstin.length}.`
  }
  if (!GSTIN.test(gstin)) {
    return 'Not in the GSTIN format: state code, PAN, entity number, Z, check character (for example 33AAACO1234A1Z5).'
  }
  if (!stateCode) {
    return 'Pick the state as well: a GSTIN starts with its state code.'
  }
  if (!gstin.startsWith(stateCode)) {
    return `This GSTIN starts with ${gstin.slice(0, 2)}, but the state code is ${stateCode}. Check the GSTIN or the state.`
  }
  return null
}

export function hsnError(value: string): string | null {
  return HSN.test(value.trim()) ? null : 'Enter the HSN code: 4, 6 or 8 digits, for example 21069099.'
}

export function gstRateError(value: string): string | null {
  const text = value.trim()
  return isAmount(text) && Number(text) <= 100 ? null : 'Enter the GST rate in percent, from 0 to 100, for example 5.'
}

export function priceError(value: string): string | null {
  const text = value.trim()
  return isAmount(text) && Number(text) > 0 ? null : 'Enter the price per packet before GST, above 0 with up to 2 decimals.'
}

export function packWeightError(value: string): string | null {
  const text = value.trim()
  return isQuantity(text) && Number(text) > 0 ? null : 'Enter the weight of one packet in kg, for example 0.5 for 500 g.'
}

/** Optional on a finished good: empty means the product cannot be ordered in cartons. */
export function packetsPerCartonError(value: string): string | null {
  const text = value.trim()
  if (!text) {
    return null
  }
  return WHOLE.test(text) && Number(text) >= 1 && Number(text) <= 10000
    ? null
    : 'Enter a whole number of packets from 1 to 10,000, or leave it empty.'
}

/** Optional: empty means no reorder alert. */
export function reorderLevelError(value: string): string | null {
  const text = value.trim()
  return !text || isQuantity(text) ? null : 'Enter a quantity of 0 or more (up to 4 decimals), or leave it empty.'
}

/** R5.8: packets per batch is a whole number above 0. */
export function outputQtyError(value: string): string | null {
  const text = value.trim()
  return WHOLE.test(text) && Number(text) > 0 ? null : 'Enter the packets one batch gives, a whole number above 0.'
}

/** R5.9: every recipe quantity is above 0. */
export function lineQtyError(value: string): string | null {
  const text = value.trim()
  return isQuantity(text) && Number(text) > 0 ? null : 'Enter a quantity above 0 (up to 4 decimals).'
}

/** Empty text is sent as null, so an optional field is cleared rather than saved as "". */
export function optional(value: string): string | null {
  const text = value.trim()
  return text ? text : null
}
