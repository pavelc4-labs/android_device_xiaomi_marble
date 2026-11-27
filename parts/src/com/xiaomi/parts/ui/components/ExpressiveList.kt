package com.xiaomi.parts.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun expressiveItemShape(
    index: Int,
    total: Int,
    large: Dp = 20.dp,
    small: Dp = 4.dp,
): RoundedCornerShape = when {
    total <= 1 -> RoundedCornerShape(large)
    index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomStart = small, bottomEnd = small)
    index == total - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = large, bottomEnd = large)
    else -> RoundedCornerShape(small)
}

@Composable
fun animatedItemShapeFor(
    index: Int,
    total: Int,
    isSelected: Boolean = false,
    outerCorner: Dp = 24.dp,
    innerCorner: Dp = 6.dp,
): RoundedCornerShape {
    val animatedMiddleCorner by animateDpAsState(
        targetValue = if (isSelected) outerCorner else innerCorner,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "MiddleCornerAnimation_$index",
    )

    return when {
        total <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner,
        )
        index == total - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner,
        )
        else -> RoundedCornerShape(animatedMiddleCorner)
    }
}

@Composable
fun SectionLabel(
    label: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (count != null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ExpressiveList(
    count: Int,
    modifier: Modifier = Modifier,
    cornerLarge: Dp = 24.dp,
    cornerSmall: Dp = 6.dp,
    gap: Dp = 2.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    item: @Composable (index: Int) -> Unit,
) {
    if (count == 0) return

    val singleShape = remember(cornerLarge) { RoundedCornerShape(cornerLarge) }
    val topShape = remember(cornerLarge, cornerSmall) {
        RoundedCornerShape(
            topStart = cornerLarge,
            topEnd = cornerLarge,
            bottomStart = cornerSmall,
            bottomEnd = cornerSmall,
        )
    }
    val bottomShape = remember(cornerLarge, cornerSmall) {
        RoundedCornerShape(
            topStart = cornerSmall,
            topEnd = cornerSmall,
            bottomStart = cornerLarge,
            bottomEnd = cornerLarge,
        )
    }
    val middleShape = remember(cornerSmall) { RoundedCornerShape(cornerSmall) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (i in 0 until count) {
            val shape = when {
                count == 1 -> singleShape
                i == 0 -> topShape
                i == count - 1 -> bottomShape
                else -> middleShape
            }
            Surface(
                shape = shape,
                color = containerColor,
            ) {
                item(i)
            }
        }
    }
}
