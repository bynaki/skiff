package com.naki.skiff.code.ui

import android.view.KeyEvent
import android.window.BackEvent
import org.json.JSONObject

/** Which way through the page's jump list a back gesture goes (docs/skiffcode.spec/jumps.md). */
enum class JumpDirection(val wire: String) {
    BACK("back"),
    FORWARD("forward"),
}

/**
 * From the edge a back gesture started at, or null for none known. A swipe in from the right edge
 * goes back and one in from the left goes forward; the three-button bar's back button has no edge
 * (`EDGE_NONE`, named only from API 36) and goes back, as does every back before API 34, which does
 * not say where it came from.
 */
fun jumpDirection(swipeEdge: Int?): JumpDirection =
    if (swipeEdge == BackEvent.EDGE_LEFT) JumpDirection.FORWARD else JumpDirection.BACK

/**
 * Ctrl+O goes back and Ctrl+I forward, as in vim; anything else, or either with Alt, Shift or Meta
 * held as well, is not a jump. Read off [keyCode] and [metaState] rather than `KeyEvent`'s own
 * helpers, which the unit test JVM's stub would throw from.
 */
fun jumpKey(keyCode: Int, metaState: Int): JumpDirection? {
    if (metaState and KeyEvent.META_CTRL_ON == 0) return null
    if (metaState and (KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_META_ON) != 0) return null
    return when (keyCode) {
        KeyEvent.KEYCODE_O -> JumpDirection.BACK
        KeyEvent.KEYCODE_I -> JumpDirection.FORWARD
        else -> null
    }
}

/** What the page last said a back gesture would have to do there. */
data class NavigationState(
    /** The jump list has a spot to go back to. */
    val back: Boolean = false,
    /** And one to go forward to. */
    val forward: Boolean = false,
    /** The sidebar, the palette, the open files menu or ④'s menu is out, which a back closes first. */
    val overlay: Boolean = false,
) {
    /**
     * Whether to take back gestures from the system at all. With nothing to do the callback is not
     * there, and back does what it always has, the animation home included. With only somewhere
     * forward, a swipe from the right is taken and goes nowhere.
     */
    val takesBack: Boolean get() = back || forward || overlay

    companion object {
        /** From the page's `navigationState`; anything missing or not a boolean is false. */
        fun of(params: JSONObject) = NavigationState(
            back = params.optBoolean("back"),
            forward = params.optBoolean("forward"),
            overlay = params.optBoolean("overlay"),
        )
    }
}
