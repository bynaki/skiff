// Going back and forward through where the reader has been.
import { describe, expect, test } from 'vitest'
import { JUMP_LIMIT, createJumps } from '../src/jumps'

const spot = (file: number, line: number) => ({ file, line })

describe('going back', () => {
  test('from the end writes down where the reader is, so going forward comes back to it', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    expect(jumps.back(spot(2, 40))).toEqual(spot(1, 10))
    expect(jumps.forward()).toEqual(spot(2, 40))
    expect(jumps.forward()).toBeNull()
  })

  test('walks the spots in reverse, one at a time, and stops at the first', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(2, 20))
    jumps.record(spot(1, 30))
    expect(jumps.back(spot(3, 1))).toEqual(spot(1, 30))
    expect(jumps.back(spot(1, 30))).toEqual(spot(2, 20))
    expect(jumps.back(spot(2, 20))).toEqual(spot(1, 10))
    expect(jumps.back(spot(1, 10))).toBeNull()
    expect(jumps.forward()).toEqual(spot(2, 20))
    expect(jumps.forward()).toEqual(spot(1, 30))
    expect(jumps.forward()).toEqual(spot(3, 1))
  })

  test('from the end, standing on the last spot, goes to the one before it', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(1, 50))
    expect(jumps.back(spot(1, 50))).toEqual(spot(1, 10))
    expect(jumps.forward()).toEqual(spot(1, 50))
    expect(jumps.forward()).toBeNull()
  })

  test('with nowhere to go writes nothing down', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    expect(jumps.back(spot(1, 10))).toBeNull()
    expect(jumps.forward()).toBeNull()
    expect(jumps.back(spot(2, 5))).toEqual(spot(1, 10))
  })
})

describe('moving somewhere new after going back', () => {
  test('drops the spots that were ahead', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(2, 20))
    jumps.back(spot(3, 30))
    jumps.back(spot(2, 20))
    // On (1, 10), scrolled to 15, and off to somewhere new.
    jumps.record(spot(1, 15))
    expect(jumps.canForward).toBe(false)
    expect(jumps.forward()).toBeNull()
    expect(jumps.back(spot(4, 1))).toEqual(spot(1, 15))
    expect(jumps.back(spot(1, 15))).toBeNull()
  })
})

describe('the same spot twice in a row', () => {
  test('is one spot', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(1, 10))
    expect(jumps.back(spot(2, 1))).toEqual(spot(1, 10))
    expect(jumps.back(spot(1, 10))).toBeNull()
  })

  test('is two when the line differs, or the file does', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(1, 11))
    jumps.record(spot(2, 11))
    expect(jumps.back(spot(3, 1))).toEqual(spot(2, 11))
    expect(jumps.back(spot(2, 11))).toEqual(spot(1, 11))
    expect(jumps.back(spot(1, 11))).toEqual(spot(1, 10))
  })
})

describe('the limit', () => {
  test('lets the oldest spots go first', () => {
    const jumps = createJumps()
    for (let line = 1; line <= JUMP_LIMIT + 5; line++) jumps.record(spot(1, line))
    // Coming back from the end, where the reader stands takes a place too.
    const reached = []
    for (let next = jumps.back(spot(2, 1)); next; next = jumps.back(next)) reached.push(next)
    expect(reached.length + 1).toBe(JUMP_LIMIT)
    expect(reached[reached.length - 1]).toEqual(spot(1, 7))
  })
})

describe('closing a file', () => {
  test('takes its spots out, and the ones either side meet', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(2, 20))
    jumps.record(spot(3, 30))
    jumps.forget(2)
    expect(jumps.back(spot(4, 1))).toEqual(spot(3, 30))
    expect(jumps.back(spot(3, 30))).toEqual(spot(1, 10))
    expect(jumps.back(spot(1, 10))).toBeNull()
  })

  test('makes one spot of the same spot that ends up twice in a row', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(2, 20))
    jumps.record(spot(1, 10))
    jumps.forget(2)
    expect(jumps.back(spot(3, 1))).toEqual(spot(1, 10))
    expect(jumps.back(spot(1, 10))).toBeNull()
  })

  test('keeps where going back had reached, counted without the closed file', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(2, 20))
    jumps.record(spot(3, 30))
    jumps.back(spot(4, 40))
    // On (3, 30), with (4, 40) ahead.
    jumps.forget(2)
    expect(jumps.forward()).toEqual(spot(4, 40))
    expect(jumps.back(spot(4, 40))).toEqual(spot(3, 30))
    expect(jumps.back(spot(3, 30))).toEqual(spot(1, 10))
  })

  test('of the spot going back had reached leaves the ones either side of it to go to', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(2, 20))
    jumps.back(spot(3, 30))
    // On (2, 20), and that file closes.
    jumps.forget(2)
    expect(jumps.back(spot(3, 30))).toEqual(spot(1, 10))
    expect(jumps.forward()).toEqual(spot(3, 30))
  })

  test('of every file leaves nothing', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    jumps.record(spot(1, 20))
    jumps.forget(1)
    expect(jumps.canBack).toBe(false)
    expect(jumps.back(spot(2, 1))).toBeNull()
  })
})

describe('an empty list', () => {
  test('goes nowhere either way', () => {
    const jumps = createJumps()
    expect(jumps.canBack).toBe(false)
    expect(jumps.canForward).toBe(false)
    expect(jumps.back(spot(1, 1))).toBeNull()
    expect(jumps.forward()).toBeNull()
    expect(jumps.canBack).toBe(false)
  })
})

describe('whether there is somewhere to go', () => {
  test('follows going back and forward', () => {
    const jumps = createJumps()
    jumps.record(spot(1, 10))
    expect([jumps.canBack, jumps.canForward]).toEqual([true, false])
    jumps.back(spot(2, 20))
    expect([jumps.canBack, jumps.canForward]).toEqual([false, true])
    jumps.forward()
    expect([jumps.canBack, jumps.canForward]).toEqual([true, false])
  })
})
