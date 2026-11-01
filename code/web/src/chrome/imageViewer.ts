// A tapped image in rendered markdown, alone on the screen (docs/skiffcode.spec/markdown-images.md):
// fitted to it at its own proportions, larger or smaller, over the top menu and the command button. Two
// fingers zoom it and one drags it while it is zoomed. The X closes it, and so does back, from either
// edge, as with the other things that float over the page. A tap closes it too, unless it is zoomed,
// when a tap only fits it again: a drag let go of should not throw away the image.
import type { Overlay } from '../back'
import { svg } from './topbar'

/** How far past fitting the screen two fingers can take an image. */
export const MAX_IMAGE_ZOOM = 8
// A finger that moves further than this is dragging, not tapping.
const TAP_SLOP = 10
const TAP_MS = 500

const CLOSE_ICON = svg('<path d="M6 6l12 12M18 6L6 18"/>')

export interface Size {
  width: number
  height: number
}

/** A spot on the screen, measured from its centre. */
export interface Point {
  x: number
  y: number
}

/** How the image is drawn: [scale] times the fitted size, its centre moved by [x], [y] from the screen's. */
export interface ImageZoom {
  scale: number
  x: number
  y: number
}

export const FITTED: ImageZoom = { scale: 1, x: 0, y: 0 }

/** The size an image of [natural] size is drawn at to fit whole in [box], as `object-fit: contain` does. */
export function containSize(natural: Size, box: Size): Size {
  if (natural.width <= 0 || natural.height <= 0) return { width: 0, height: 0 }
  const ratio = Math.min(box.width / natural.width, box.height / natural.height)
  return { width: natural.width * ratio, height: natural.height * ratio }
}

/**
 * Where two fingers that came down [startDistance] apart around [startMid], on an image drawn as
 * [from], have taken it now that they are [distance] apart around [mid]: the part of the image that was
 * between them stays between them. Not smaller than fitted, nor larger than [MAX_IMAGE_ZOOM].
 */
export function pinchZoom(from: ImageZoom, startMid: Point, startDistance: number, mid: Point, distance: number): ImageZoom {
  const scale = Math.min(Math.max((from.scale * distance) / startDistance, 1), MAX_IMAGE_ZOOM)
  // That part of the image, in the fitted image's own pixels from its centre.
  const x = (startMid.x - from.x) / from.scale
  const y = (startMid.y - from.y) / from.scale
  return { scale, x: mid.x - x * scale, y: mid.y - y * scale }
}

/**
 * [zoom] kept from pulling an edge of the image, drawn [fitted] at 1x, in past the same edge of
 * [screen]: on an axis it does not fill, it stays in the middle.
 */
export function clampZoom(zoom: ImageZoom, fitted: Size, screen: Size): ImageZoom {
  const scale = Math.min(Math.max(zoom.scale, 1), MAX_IMAGE_ZOOM)
  const keep = (offset: number, drawn: number, room: number) => {
    const most = Math.max(0, (drawn * scale - room) / 2)
    return Math.min(Math.max(offset, -most), most)
  }
  return { scale, x: keep(zoom.x, fitted.width, screen.width), y: keep(zoom.y, fitted.height, screen.height) }
}

export interface ImageViewer extends Overlay {
  /** Shows the image at [src], the address the document already loaded it from, so it is not read again. */
  show(src: string, alt: string): void
  /** Names the X, with the open files menu's word for closing. */
  label(labels: { close: string }): void
}

export function createImageViewer(): ImageViewer {
  const element = document.createElement('div')
  element.id = 'image-viewer'
  element.hidden = true
  const image = document.createElement('img')
  const close = document.createElement('button')
  close.type = 'button'
  close.className = 'close'
  close.innerHTML = CLOSE_ICON
  element.append(image, close)
  document.body.append(element)

  let zoom = FITTED
  // What the fingers on the image are doing: two pinching, or one that drags it from where it was.
  let gesture: { from: ImageZoom; mid: Point; distance: number } | { from: ImageZoom; start: Point } | null = null
  // A finger that may still turn out to be a tap: where and when it came down.
  let tap: { x: number; y: number; time: number } | null = null

  const draw = (animate = false) => {
    image.classList.toggle('settling', animate)
    image.style.transform = zoom === FITTED ? '' : `translate(${zoom.x}px, ${zoom.y}px) scale(${zoom.scale})`
  }
  const fit = (animate = false) => {
    zoom = FITTED
    draw(animate)
  }
  const kept = (next: ImageZoom) => {
    const box = { width: image.clientWidth, height: image.clientHeight }
    const fitted = containSize({ width: image.naturalWidth, height: image.naturalHeight }, box)
    return clampZoom(next, fitted, { width: element.clientWidth, height: element.clientHeight })
  }
  const point = (touch: Touch): Point => {
    const rect = element.getBoundingClientRect()
    return { x: touch.clientX - rect.left - rect.width / 2, y: touch.clientY - rect.top - rect.height / 2 }
  }
  const between = (touches: TouchList) => {
    const a = point(touches[0])
    const b = point(touches[1])
    return { mid: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }, distance: Math.hypot(a.x - b.x, a.y - b.y) }
  }

  const hide = () => {
    element.hidden = true
    image.removeAttribute('src')
    gesture = null
    tap = null
    fit()
  }
  const tapped = () => (zoom.scale > 1 ? fit(true) : hide())

  const begin = (touches: TouchList) => {
    if (touches.length === 2) gesture = { from: zoom, ...between(touches) }
    else if (touches.length === 1) gesture = { from: zoom, start: point(touches[0]) }
    else gesture = null
  }

  element.addEventListener('touchstart', (event) => {
    if (close.contains(event.target as Node)) return
    const touch = event.touches[0]
    tap = event.touches.length === 1 ? { x: touch.clientX, y: touch.clientY, time: event.timeStamp } : null
    begin(event.touches)
  }, { passive: true })

  element.addEventListener('touchmove', (event) => {
    event.preventDefault()
    const touch = event.touches[0]
    if (tap && Math.hypot(touch.clientX - tap.x, touch.clientY - tap.y) > TAP_SLOP) tap = null
    if (!gesture) return
    if ('distance' in gesture && event.touches.length === 2) {
      const now = between(event.touches)
      zoom = kept(pinchZoom(gesture.from, gesture.mid, gesture.distance, now.mid, now.distance))
    } else if ('start' in gesture && event.touches.length === 1 && gesture.from.scale > 1) {
      const now = point(touch)
      zoom = kept({ ...gesture.from, x: gesture.from.x + now.x - gesture.start.x, y: gesture.from.y + now.y - gesture.start.y })
    } else {
      return
    }
    draw()
  }, { passive: false })

  element.addEventListener('touchend', (event) => {
    // One finger left of a pinch drags on from where the pinch put the image.
    begin(event.touches)
    if (event.touches.length > 0 || !tap) return
    const quick = event.timeStamp - tap.time < TAP_MS
    tap = null
    if (!quick) return
    // The tap is handled here, so the click it would also make does not do it twice.
    event.preventDefault()
    tapped()
  })

  element.addEventListener('touchcancel', () => {
    gesture = null
    tap = null
  }, { passive: true })

  // A mouse, which makes no touches; a finger's click is the touchend's above, which cancels it.
  element.addEventListener('click', (event) => {
    if (!close.contains(event.target as Node) && (event as PointerEvent).pointerType === 'mouse') tapped()
  })
  close.addEventListener('click', hide)

  // Unfolding or turning the device changes what fitting is.
  window.addEventListener('resize', () => {
    if (!element.hidden) fit()
  })

  return {
    show(src, alt) {
      fit()
      image.src = src
      image.alt = alt
      element.hidden = false
    },
    label(labels) {
      close.title = labels.close
      close.setAttribute('aria-label', labels.close)
    },
    hide,
    isOpen: () => !element.hidden,
  }
}
