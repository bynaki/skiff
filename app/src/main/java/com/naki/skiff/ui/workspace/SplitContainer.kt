package com.naki.skiff.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val DIVIDER_THICKNESS = 10.dp

/**
 * Two panes with a draggable divider. Direction and ratio are the user's choice, so this
 * takes both as parameters rather than deciding from the window size.
 */
@Composable
fun SplitContainer(
    direction: SplitDirection,
    ratio: Float,
    onRatioChange: (Float) -> Unit,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalPx = with(density) {
            if (direction == SplitDirection.HORIZONTAL) maxWidth.toPx() else maxHeight.toPx()
        }

        val dragModifier = Modifier.pointerInput(direction, totalPx) {
            detectDragGestures { _, dragAmount ->
                val delta = if (direction == SplitDirection.HORIZONTAL) dragAmount.x else dragAmount.y
                if (totalPx > 0f) onRatioChange(ratio + delta / totalPx)
            }
        }

        if (direction == SplitDirection.HORIZONTAL) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(ratio).fillMaxHeight()) { first() }
                Box(
                    Modifier
                        .width(DIVIDER_THICKNESS)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(dragModifier),
                )
                Box(Modifier.weight(1f - ratio).fillMaxHeight()) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(ratio).fillMaxWidth()) { first() }
                Box(
                    Modifier
                        .height(DIVIDER_THICKNESS)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(dragModifier),
                )
                Box(Modifier.weight(1f - ratio).fillMaxWidth()) { second() }
            }
        }
    }
}
