package com.jp.freedom.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SplashNavy = Color(0xFF0F1B33)
private val SplashLavender = Color(0xFFA99BE8)

/** The main screen composes and loads underneath; startup never waits for network sync. */
@Composable
fun DimkySplashHost(ready: Boolean, enabled: Boolean, content: @Composable () -> Unit) {
    var dismissed by rememberSaveable { mutableStateOf(!enabled) }
    val reducedMotion = rememberReducedMotion()
    val pieces = remember { List(3) { Animatable(0f) } }
    val pulse = remember { Animatable(1f) }
    val opacity = remember { Animatable(1f) }
    var assembled by remember { mutableStateOf(false) }

    LaunchedEffect(reducedMotion, dismissed) {
        if (dismissed) return@LaunchedEffect
        if (reducedMotion) {
            pieces.forEach { it.snapTo(1f) }
            pulse.snapTo(1f)
            assembled = true
        } else if (!assembled) {
            coroutineScope {
                pieces.forEachIndexed { index, piece ->
                    launch {
                        delay(100L + index * 150L)
                        piece.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 180f))
                    }
                }
            }
            pulse.animateTo(1.035f, tween(120, easing = FastOutSlowInEasing))
            pulse.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            assembled = true
        }
    }
    LaunchedEffect(ready, assembled, reducedMotion, dismissed) {
        if (ready && assembled && !dismissed) {
            if (!reducedMotion) opacity.animateTo(0f, tween(220))
            dismissed = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Hide underlying controls from accessibility and pointer input during the splash.
        Box(if (dismissed) Modifier else Modifier.clearAndSetSemantics {}) { content() }
        if (!dismissed) {
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = opacity.value }
                    .background(SplashNavy)
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                    .clearAndSetSemantics { contentDescription = "Dimky" },
                contentAlignment = Alignment.Center,
            ) {
                val iconSize = minOf(160.dp, maxWidth * 0.42f, maxHeight * 0.34f)
                Canvas(Modifier.size(iconSize)) {
                    val unit = size.width / 100f
                    scale(pulse.value) {
                        scale(unit, unit, pivot = Offset.Zero) {
                            // Final geometry is shared by all frames, with generous visible gaps.
                            fun piece(index: Int, x: Float, y: Float, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
                                val progress = pieces[index].value
                                if (progress <= 0f) return
                                translate(x * (1f - progress), y * (1f - progress)) {
                                    scale(0.8f + progress * 0.2f, pivot = Offset(50f, 50f)) { draw() }
                                }
                            }
                            piece(0, 0f, 110f) {
                                drawRoundRect(SplashLavender, Offset(0f, 28f), Size(31f, 72f), CornerRadius(11f))
                            }
                            piece(1, 0f, -110f) {
                                val block = Path().apply {
                                    moveTo(46f, 0f); lineTo(87f, 0f)
                                    quadraticTo(100f, 0f, 100f, 13f)
                                    lineTo(100f, 47f); quadraticTo(100f, 60f, 87f, 60f)
                                    lineTo(61f, 60f); quadraticTo(57f, 60f, 57f, 56f)
                                    lineTo(57f, 42f); lineTo(46f, 42f)
                                    quadraticTo(33f, 42f, 33f, 29f)
                                    lineTo(33f, 13f); quadraticTo(33f, 0f, 46f, 0f)
                                    close()
                                }
                                drawPath(block, SplashLavender)
                            }
                            piece(2, 110f, 0f) {
                                drawRoundRect(SplashLavender, Offset(36f, 65f), Size(64f, 35f), CornerRadius(11f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Android's Remove animations setting is the native equivalent of prefers-reduced-motion. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun disabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var disabled by remember(resolver) { mutableStateOf(disabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { disabled = disabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return disabled
}
