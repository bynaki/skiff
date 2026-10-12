// What the back gesture does on the page, and what Kotlin is told about it.
import { describe, expect, test } from 'vitest'
import { type Direction, type NavigationState, type Overlay, backGesture, createNavigationReporter } from '../src/back'

/** An overlay that is [open] until it is closed, and says when it was. */
function overlay(name: string, open: boolean, closed: string[]): Overlay {
  let out = open
  return {
    isOpen: () => out,
    hide: () => {
      out = false
      closed.push(name)
    },
  }
}

describe('a back gesture', () => {
  test('with something out closes it and goes nowhere, from either edge', () => {
    for (const direction of ['back', 'forward'] as Direction[]) {
      const closed: string[] = []
      const moved: Direction[] = []
      const overlays = [overlay('sidebar', false, closed), overlay('palette', true, closed), overlay('files', false, closed)]
      backGesture(direction, overlays, (way) => moved.push(way))
      expect(closed).toEqual(['palette'])
      expect(moved).toEqual([])
    }
  })

  test('closes everything that is out at once', () => {
    const closed: string[] = []
    const moved: Direction[] = []
    backGesture('back', [overlay('files', true, closed), overlay('more', true, closed)], (way) => moved.push(way))
    expect(closed).toEqual(['files', 'more'])
    expect(moved).toEqual([])
  })

  test('with nothing out goes the way it came from', () => {
    for (const direction of ['back', 'forward'] as Direction[]) {
      const closed: string[] = []
      const moved: Direction[] = []
      backGesture(direction, [overlay('sidebar', false, closed), overlay('more', false, closed)], (way) => moved.push(way))
      expect(closed).toEqual([])
      expect(moved).toEqual([direction])
    }
  })

  test('the one after it, once the overlay is closed, moves', () => {
    const closed: string[] = []
    const moved: Direction[] = []
    const overlays = [overlay('sidebar', true, closed)]
    backGesture('back', overlays, (way) => moved.push(way))
    backGesture('back', overlays, (way) => moved.push(way))
    expect(closed).toEqual(['sidebar'])
    expect(moved).toEqual(['back'])
  })
})

describe('what Kotlin is told', () => {
  test('is the state the first time, and again only when it has changed', () => {
    let state: NavigationState = { back: false, forward: false, overlay: false }
    const sent: NavigationState[] = []
    const report = createNavigationReporter(() => state, (told) => sent.push(told))
    report()
    report()
    state = { ...state, back: true }
    report()
    state = { ...state }
    report()
    state = { ...state, overlay: true }
    report()
    expect(sent).toEqual([
      { back: false, forward: false, overlay: false },
      { back: true, forward: false, overlay: false },
      { back: true, forward: false, overlay: true },
    ])
  })
})
