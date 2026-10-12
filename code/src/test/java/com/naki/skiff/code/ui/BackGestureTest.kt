package com.naki.skiff.code.ui

import android.window.BackEvent
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackGestureTest {

    @Test
    fun `a swipe in from the right edge goes back`() {
        assertEquals(JumpDirection.BACK, jumpDirection(BackEvent.EDGE_RIGHT))
    }

    @Test
    fun `a swipe in from the left edge goes forward`() {
        assertEquals(JumpDirection.FORWARD, jumpDirection(BackEvent.EDGE_LEFT))
    }

    @Test
    fun `the back button, which has no edge, goes back`() {
        assertEquals(JumpDirection.BACK, jumpDirection(BackEvent.EDGE_NONE))
    }

    @Test
    fun `a back that does not say where it came from goes back`() {
        assertEquals(JumpDirection.BACK, jumpDirection(null))
    }

    @Test
    fun `the page is told the way by name`() {
        assertEquals(listOf("back", "forward"), JumpDirection.entries.map { it.wire })
    }

    @Test
    fun `back is taken from the system when there is anything for it to do, and only then`() {
        for (back in listOf(false, true)) for (forward in listOf(false, true)) for (overlay in listOf(false, true)) {
            val state = NavigationState(back, forward, overlay)
            assertEquals(state.toString(), back || forward || overlay, state.takesBack)
        }
    }

    @Test
    fun `the page's state is read field by field, and what is not there is false`() {
        assertEquals(
            NavigationState(back = true, forward = false, overlay = true),
            NavigationState.of(JSONObject("""{"back": true, "overlay": true}""")),
        )
        assertFalse(NavigationState.of(JSONObject("""{"back": "yes", "forward": 1}""")).takesBack)
        assertTrue(NavigationState.of(JSONObject("""{"forward": true}""")).takesBack)
    }
}
