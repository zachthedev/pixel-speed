package tools.zach.app.pixelspeed.ui.overlay

import android.graphics.Path
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import tools.zach.app.pixelspeed.R

/** Whether the readout's own motion runs; false under animator scale 0 and Battery Saver. */
val LocalReadoutMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

/**
 * Three dots where the digits will go, on a row as tall as the digits' line, each walking
 * Material's own loading indicator shapes and turning as it morphs, a third of the cycle apart so
 * no two look alike. The progress is read only while drawing, so a cycle redraws the dots and never
 * recomposes them or the layout around them. With motion off the first frame holds still. A
 * [spoken] row is one node naming the wait; a pill with two rows speaks it from one of them.
 */
@Composable
internal fun LoadingDots(
  color: Color,
  size: Dp,
  lineHeight: Dp,
  modifier: Modifier = Modifier,
  spoken: Boolean = true,
) {
  val progress: State<Float> =
    if (LocalReadoutMotion.current) {
      rememberInfiniteTransition(label = "loading")
        .animateFloat(
          initialValue = 0f,
          targetValue = SHAPES.size.toFloat(),
          animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing)),
          label = "morph",
        )
    } else {
      remember { mutableFloatStateOf(0f) }
    }
  val waiting: String = stringResource(R.string.overlay_loading)
  val row: Modifier = modifier.height(lineHeight)
  Row(
    modifier = if (spoken) row.semantics { contentDescription = waiting } else row,
    horizontalArrangement = Arrangement.spacedBy(size / 3),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(DOTS) { index ->
      val path = remember { Path() }
      val outline = remember(path) { path.asComposePath() }
      Canvas(Modifier.size(size)) {
        val phase = (progress.value + index * (SHAPES.size.toFloat() / DOTS)) % SHAPES.size
        val from = phase.toInt().coerceIn(0, MORPHS.lastIndex)
        path.rewind()
        MORPHS[from].toPath(phase - from, path)
        // The polygons are normalized to a unit square, so the canvas scales them to the dot; the
        // turn runs with the morph, as the indicator's own does.
        rotate(degrees = phase * DEGREES_PER_SHAPE) {
          scale(this.size.width, this.size.height, pivot = Offset.Zero) { drawPath(outline, color) }
        }
      }
    }
  }
}

/** The indicator's own shape sequence, each normalized to the unit square the dots draw in. */
private val SHAPES: List<RoundedPolygon> =
  LoadingIndicatorDefaults.IndeterminateIndicatorPolygons.map { it.normalized() }

/** Each shape into the next, the last back into the first. */
private val MORPHS: List<Morph> =
  SHAPES.indices.map { Morph(SHAPES[it], SHAPES[(it + 1) % SHAPES.size]) }

private const val DOTS: Int = 3

/** One pass through every shape. */
private const val CYCLE_MS: Int = 3_500

/** One whole turn per cycle, spread over the shapes, so the wrap back to the first is seamless. */
private val DEGREES_PER_SHAPE: Float = 360f / SHAPES.size
