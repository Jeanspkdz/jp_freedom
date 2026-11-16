package com.jp.freedom.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jp.freedom.R
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.sin

/** A quiet companion that hops occasionally and celebrates when touched. */
@Composable
fun RitmyMascot(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    cheerful: Boolean = false,
) {
    var reaction by remember { mutableIntStateOf(0) }
    var smiling by remember { mutableStateOf(false) }
    val hop = remember { Animatable(0f) }

    LaunchedEffect(reaction, cheerful) {
        val motionScale = currentCoroutineContext()[MotionDurationScale]
        snapshotFlow { motionScale?.scaleFactor ?: 1f }.collectLatest { scale ->
            hop.snapTo(0f)
            smiling = cheerful || reaction > 0
            if (scale == 0f) {
                // Expression changes remain available with system animations disabled.
                delay(1800)
                smiling = cheerful
                return@collectLatest
            }
            if (smiling) {
                repeat(2) {
                    hop.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                    hop.snapTo(0f)
                }
                delay(600)
                smiling = cheerful
            }
            while (true) {
                delay(5500)
                hop.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
                hop.snapTo(0f)
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clickable(role = Role.Button, onClickLabel = "Hacer saltar a Ritmy") { reaction++ },
    ) {
        Image(
            painter = painterResource(if (smiling) R.drawable.ritmy_happy else R.drawable.ritmy),
            contentDescription = "Ritmy, tu compañero de enfoque",
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val arc = sin(PI * hop.value).toFloat()
                    val sway = sin(2 * PI * hop.value).toFloat()
                    translationY = -(if (smiling) 14.dp else 6.dp).toPx() * arc
                    rotationZ = sway * (if (smiling) 9f else 4f)
                    scaleX = 1f + sway * 0.04f
                    scaleY = 1f - sway * 0.04f
                },
        )
    }
}
