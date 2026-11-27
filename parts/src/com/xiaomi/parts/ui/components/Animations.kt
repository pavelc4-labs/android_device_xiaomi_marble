package com.xiaomi.parts.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val BounceSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow,
)

/**
 * Adds a responsive spring bouncy scale effect and Material 3 ripple on click,
 * backed by Modifier.Node to avoid Modifier.composed subcomposition overhead.
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit,
): Modifier = this then BouncyClickableElement(enabled, scaleDown, ripple(), onClick)

/**
 * Adds bouncy scale animation linked to an external InteractionSource via Modifier.Node.
 */
fun Modifier.bouncyScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
): Modifier = this then BouncyScaleElement(interactionSource, enabled, scaleDown)

private data class BouncyScaleElement(
    val interactionSource: InteractionSource,
    val enabled: Boolean,
    val scaleDown: Float,
) : ModifierNodeElement<BouncyScaleNode>() {
    override fun create() = BouncyScaleNode(interactionSource, enabled, scaleDown)

    override fun update(node: BouncyScaleNode) {
        node.updateParams(interactionSource, enabled, scaleDown)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bouncyScale"
        properties["enabled"] = enabled
        properties["scaleDown"] = scaleDown
    }
}

private class BouncyScaleNode(
    private var interactionSource: InteractionSource,
    private var enabled: Boolean,
    private var scaleDown: Float,
) : DrawModifierNode, Modifier.Node() {

    private val scaleAnim = Animatable(1f)
    private var collectJob: Job? = null

    override fun onAttach() {
        startCollecting()
    }

    override fun onDetach() {
        collectJob?.cancel()
        collectJob = null
    }

    fun updateParams(
        newSource: InteractionSource,
        newEnabled: Boolean,
        newScaleDown: Float,
    ) {
        val sourceChanged = interactionSource !== newSource
        interactionSource = newSource
        enabled = newEnabled
        scaleDown = newScaleDown
        if (!newEnabled && scaleAnim.targetValue != 1f) {
            coroutineScope.launch { scaleAnim.animateTo(1f, BounceSpring) }
        }
        if (sourceChanged && isAttached) {
            startCollecting()
        }
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val target = when (interaction) {
                    is PressInteraction.Press -> if (enabled) scaleDown else 1f
                    is PressInteraction.Release, is PressInteraction.Cancel -> 1f
                    else -> return@collect
                }
                scaleAnim.animateTo(target, BounceSpring) {
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = scaleAnim.value
        if (s == 1f) {
            drawContent()
        } else {
            scale(scaleX = s, scaleY = s) {
                this@draw.drawContent()
            }
        }
    }
}

private data class BouncyClickableElement(
    val enabled: Boolean,
    val scaleDown: Float,
    val indicationFactory: IndicationNodeFactory,
    val onClick: () -> Unit,
) : ModifierNodeElement<BouncyClickableNode>() {
    override fun create() = BouncyClickableNode(enabled, scaleDown, indicationFactory, onClick)

    override fun update(node: BouncyClickableNode) {
        node.enabled = enabled
        node.scaleDown = scaleDown
        node.onClick = onClick
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "bouncyClickable"
        properties["enabled"] = enabled
        properties["scaleDown"] = scaleDown
    }
}

private class BouncyClickableNode(
    var enabled: Boolean,
    var scaleDown: Float,
    indicationFactory: IndicationNodeFactory,
    var onClick: () -> Unit,
) : DelegatingNode(), DrawModifierNode, PointerInputModifierNode, SemanticsModifierNode {

    private val interactionSource = MutableInteractionSource()
    private val scaleAnim = Animatable(1f)
    private var collectJob: Job? = null

    @Suppress("unused")
    private val indicationNode: DelegatableNode = delegate(indicationFactory.create(interactionSource))

    private val pointerNode = delegate(
        SuspendingPointerInputModifierNode {
            detectTapGestures(
                onPress = { offset ->
                    if (!enabled) return@detectTapGestures
                    val press = PressInteraction.Press(offset)
                    interactionSource.emit(press)
                    val released = tryAwaitRelease()
                    interactionSource.emit(
                        if (released) PressInteraction.Release(press)
                        else PressInteraction.Cancel(press)
                    )
                },
                onTap = {
                    if (enabled) onClick()
                },
            )
        }
    )

    override fun onAttach() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val target = when (interaction) {
                    is PressInteraction.Press -> if (enabled) scaleDown else 1f
                    is PressInteraction.Release, is PressInteraction.Cancel -> 1f
                    else -> return@collect
                }
                scaleAnim.animateTo(target, BounceSpring) {
                    invalidateDraw()
                }
            }
        }
    }

    override fun onDetach() {
        collectJob?.cancel()
        collectJob = null
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        pointerNode.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        pointerNode.onCancelPointerInput()
    }

    override fun SemanticsPropertyReceiver.applySemantics() {
        role = Role.Button
        if (enabled) {
            onClick {
                onClick()
                true
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val s = scaleAnim.value
        if (s == 1f) {
            drawContent()
        } else {
            scale(scaleX = s, scaleY = s) {
                this@draw.drawContent()
            }
        }
    }
}
