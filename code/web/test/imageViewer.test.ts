// The full screen image's zoom and drag (docs/skiffcode.spec/markdown-images.md, "누르기: 전체 화면").
import { describe, expect, test } from 'vitest'
import { FITTED, type ImageZoom, MAX_IMAGE_ZOOM, clampZoom, containSize, pinchZoom } from '../src/chrome/imageViewer'

/** Field by field, so a `-0` from the arithmetic is still 0. */
const same = (actual: ImageZoom, expected: ImageZoom) => {
  expect(actual.scale).toBeCloseTo(expected.scale)
  expect(actual.x).toBeCloseTo(expected.x)
  expect(actual.y).toBeCloseTo(expected.y)
}

describe('fitting the screen', () => {
  test('keeps the proportions, larger or smaller', () => {
    expect(containSize({ width: 800, height: 400 }, { width: 400, height: 600 })).toEqual({ width: 400, height: 200 })
    expect(containSize({ width: 88, height: 20 }, { width: 440, height: 600 })).toEqual({ width: 440, height: 100 })
  })

  test('is nothing for an image with no size', () => {
    expect(containSize({ width: 0, height: 0 }, { width: 400, height: 600 })).toEqual({ width: 0, height: 0 })
  })
})

describe('a pinch', () => {
  test('keeps the part of the image between the fingers between them', () => {
    // Fingers around a spot 100px right of the centre, spread to twice as far apart.
    const zoom = pinchZoom(FITTED, { x: 100, y: 0 }, 50, { x: 100, y: 0 }, 100)
    same(zoom, { scale: 2, x: -100, y: 0 })
    // That spot of the image, 100px from its centre at 1x, is 200px out at 2x, and still under the fingers.
    expect(zoom.x + 100 * zoom.scale).toBeCloseTo(100)
  })

  test('carries that part along when the fingers move together', () => {
    same(pinchZoom(FITTED, { x: 0, y: 0 }, 50, { x: 30, y: -20 }, 50), { scale: 1, x: 30, y: -20 })
  })

  test('goes on from where the image already was', () => {
    const from = { scale: 2, x: -100, y: 0 }
    const zoom = pinchZoom(from, { x: 100, y: 0 }, 100, { x: 100, y: 0 }, 50)
    same(zoom, { scale: 1, x: 0, y: 0 })
  })

  test('does not make it smaller than fitted, nor past the most', () => {
    expect(pinchZoom(FITTED, { x: 0, y: 0 }, 100, { x: 0, y: 0 }, 10).scale).toBe(1)
    expect(pinchZoom(FITTED, { x: 0, y: 0 }, 10, { x: 0, y: 0 }, 1000).scale).toBe(MAX_IMAGE_ZOOM)
  })
})

describe('keeping the image on the screen', () => {
  const screen = { width: 400, height: 800 }
  const fitted = { width: 400, height: 200 }

  test('lets it move only as far as its edge reaches the screen\'s', () => {
    // 2x is 800 by 400: 200px to spare each side across, none up and down.
    same(clampZoom({ scale: 2, x: 500, y: 300 }, fitted, screen), { scale: 2, x: 200, y: 0 })
    same(clampZoom({ scale: 2, x: -500, y: -300 }, fitted, screen), { scale: 2, x: -200, y: 0 })
  })

  test('leaves it where it is inside that', () => {
    same(clampZoom({ scale: 2, x: 50, y: 0 }, fitted, screen), { scale: 2, x: 50, y: 0 })
  })

  test('keeps it in the middle on an axis it does not fill', () => {
    // 3x is 1200 by 600, still short of the height.
    same(clampZoom({ scale: 3, x: 0, y: 100 }, fitted, screen), { scale: 3, x: 0, y: 0 })
    same(clampZoom({ scale: 1, x: 40, y: 40 }, fitted, screen), FITTED)
  })

  test('holds the scale inside fitted and the most', () => {
    expect(clampZoom({ scale: 0.5, x: 0, y: 0 }, fitted, screen).scale).toBe(1)
    expect(clampZoom({ scale: 20, x: 0, y: 0 }, fitted, screen).scale).toBe(MAX_IMAGE_ZOOM)
  })
})
