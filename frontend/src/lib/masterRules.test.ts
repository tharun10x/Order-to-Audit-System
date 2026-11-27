import { describe, expect, it } from 'vitest'

import { codeError, gstinError, hsnError, outputQtyError, packetsPerCartonError, priceError } from './masterRules'

describe('master checks (R5.1, R5.3, M1)', () => {
  it('accepts a GSTIN in the layout that starts with the state code, or none at all', () => {
    expect(gstinError('33AAACO1234A1Z5', '33')).toBeNull()
    expect(gstinError('33aaaco1234a1z5', '33')).toBeNull()     // saved in capitals
    expect(gstinError('', '')).toBeNull()                      // optional
  })

  it('says what is wrong with a GSTIN', () => {
    expect(gstinError('33AAACO1234', '33')).toBe('A GSTIN has 15 characters; this one has 11.')
    expect(gstinError('33AAACO1234A1X5', '33')).toMatch(/^Not in the GSTIN format/)
    expect(gstinError('29AAACD7654B1Z2', '')).toBe('Pick the state as well: a GSTIN starts with its state code.')
    expect(gstinError('29AAACD7654B1Z2', '33'))
      .toBe('This GSTIN starts with 29, but the state code is 33. Check the GSTIN or the state.')
  })

  it('checks codes as the backend does, after upper-casing', () => {
    expect(codeError('murukku500', 'item')).toBeNull()
    expect(codeError('', 'item')).toBe('Enter the item code, for example MURUKKU500.')
    expect(codeError('-X', 'item')).toMatch(/^Use up to 30 capital letters/)
    expect(codeError('A B', 'customer')).toMatch(/^Use up to 30 capital letters/)
  })

  it('checks the finished-good fields', () => {
    expect(hsnError('21069099')).toBeNull()
    expect(hsnError('2106909')).toMatch(/4, 6 or 8 digits/)
    expect(priceError('110')).toBeNull()
    expect(priceError('0')).toMatch(/above 0/)
    expect(priceError('1.005')).toMatch(/up to 2 decimals/)
    expect(packetsPerCartonError('')).toBeNull()
    expect(packetsPerCartonError('2.5')).toMatch(/whole number/)
    expect(outputQtyError('80')).toBeNull()
    expect(outputQtyError('80.5')).toMatch(/whole number/)
  })
})
