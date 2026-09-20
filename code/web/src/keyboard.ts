// Whether a hardware keyboard is attached, which only Kotlin can see (`Configuration.keyboard`).
//
// It decides whether entering the editor layer takes focus. With a hardware keyboard focus is what
// lets typing start; without one it only raises the soft keyboard over a document the user has not
// asked to type in. Kotlin pushes the value again on every configuration change, so a keyboard
// plugged in while a file is open is noticed.
import { onNotify, rpc } from './bridge'

let present = false

export function hardwareKeyboard(): boolean {
  return present
}

const received = (params: { present: boolean }) => {
  present = params.present
}

rpc<{ present: boolean }>('hardwareKeyboard').then(received).catch((error) => console.log(`hardwareKeyboard: ${error}`))
onNotify('hardwareKeyboardChanged', received)
