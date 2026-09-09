package tools.zach.app.pixelspeed.ui.overlay

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tools.zach.app.pixelspeed.format.FormatOptions
import tools.zach.app.pixelspeed.format.FormattedSpeed
import tools.zach.app.pixelspeed.format.SpeedFormatter
import tools.zach.app.pixelspeed.format.UnitLabel
import tools.zach.app.pixelspeed.format.forStackAndHalves
import tools.zach.app.pixelspeed.overlay.DisplayGeometry
import tools.zach.app.pixelspeed.overlay.OverlayStyle
import tools.zach.app.pixelspeed.overlay.PlacementRequest
import tools.zach.app.pixelspeed.sampling.SpeedSample
import tools.zach.app.pixelspeed.sampling.Transport
import tools.zach.app.pixelspeed.settings.Layout
import tools.zach.app.pixelspeed.settings.Profile
import tools.zach.app.pixelspeed.settings.ReadoutSurface
import tools.zach.app.pixelspeed.ui.theme.PixelSpeedTheme

/** The overlay window's whole composition: formats the latest sample and places the readout. */
@Composable
fun OverlayRoot(
  profile: StateFlow<Profile>,
  sample: StateFlow<SpeedSample?>,
  loading: StateFlow<Boolean>,
  geometry: StateFlow<DisplayGeometry>,
  shown: StateFlow<Boolean>,
  promotedChipAvailable: Boolean,
  onOrigin: (WindowOrigin) -> Unit,
  onFits: (Boolean) -> Unit,
) {
  val activeProfile by profile.collectAsStateWithLifecycle()
  val isShown by shown.collectAsStateWithLifecycle()
  val latest by sample.collectAsStateWithLifecycle()
  val waiting by loading.collectAsStateWithLifecycle()
  val display by geometry.collectAsStateWithLifecycle()
  val animatorsEnabled: Boolean by rememberAnimatorsEnabled()
  val composition =
    rememberOverlayComposition(activeProfile, latest, promotedChipAvailable, loading = waiting)
  PixelSpeedTheme {
    CompositionLocalProvider(LocalReadoutMotion provides animatorsEnabled) {
      OverlayContent(
        values = composition.values,
        measureValues = composition.measureValues,
        request = composition.request,
        geometry = display,
        presentation = composition.presentation,
        onOrigin = onOrigin,
        onFits = onFits,
        modifier = Modifier.visibilityMotion(isShown, animatorsEnabled),
      )
    }
  }
}

/** Everything [OverlayContent] needs from a profile and a sample, derived once per change. */
data class OverlayComposition(
  val values: ReadoutValues,
  val measureValues: ReadoutValues,
  val request: PlacementRequest,
  val presentation: ReadoutPresentation,
)

/**
 * Derives the readout strings, the placement request, and the presentation for [profile] and
 * [sample], the same way for the overlay window and for every in-app preview of it. [loading] marks
 * the wait for a first sample, which the looks draw in place of the dashes a null sample carries.
 */
@Composable
fun rememberOverlayComposition(
  profile: Profile,
  sample: SpeedSample?,
  promotedChipAvailable: Boolean,
  loading: Boolean = false,
): OverlayComposition {
  val configuration = LocalConfiguration.current
  val formatter =
    remember(configuration.locales) {
      SpeedFormatter(configuration.locales[0] ?: Locale.getDefault())
    }
  val layout = profile.content.layout
  val renderStyle =
    remember(profile.appearance.style, layout) { styleForLayout(profile.appearance.style, layout) }
  val values =
    remember(sample, profile, formatter, renderStyle, loading) {
      if (layout == Layout.COMBINED) {
        combinedValues(sample, formatter, profile.content.format, sample?.transport, loading)
      } else {
        sample.toValues(formatter, profile, renderStyle, loading)
      }
    }
  // Looks are measured on the widest strings the format can produce, so the placement never moves
  // when the live digits change tier.
  val measureValues =
    remember(profile.content.format, formatter) { widestValues(formatter, profile.content.format) }
  val effectiveShowUpload =
    when (layout) {
      Layout.DOWNLOAD_ONLY,
      Layout.COMBINED -> false
      Layout.ONE_LINE,
      Layout.TWO_LINES -> profile.content.showUpload
    }
  val effectiveShowDownload = profile.content.showDownload || !effectiveShowUpload
  val request =
    remember(profile, renderStyle, promotedChipAvailable) {
      PlacementRequest(
        anchor = profile.position.anchor,
        landscapeAnchorMode = profile.position.landscapeAnchorMode,
        landscapeAnchor = profile.position.landscapeAnchor,
        style = renderStyle,
        statusBarFit = profile.appearance.statusBarFit,
        offsetXdp = profile.position.offsetXdp,
        offsetYdp = profile.position.offsetYdp,
        customXdp = profile.position.customXdp,
        customYdp = profile.position.customYdp,
        avoidCutout = profile.position.avoidCutout,
        promotedChip = promotedChipAvailable && profile.notification.surface == ReadoutSurface.BOTH,
        ribbonFallback = profile.appearance.ribbonFallback,
      )
    }
  val chipColors = nativeChipColors()
  val readoutStyle =
    remember(profile.appearance, profile.position, sample, chipColors) {
      ReadoutStyles.from(
        profile.appearance,
        profile.position,
        sample,
        chipColors.fill.toArgb(),
        chipColors.text.toArgb(),
      )
    }
  val presentation =
    ReadoutPresentation(
      colors = chipColors,
      textSize = profile.position.textSizeSp.sp,
      readoutStyle = readoutStyle,
      showDownload = effectiveShowDownload,
      showUpload = effectiveShowUpload,
      directionOrder = profile.content.directionOrder,
      directionMarker = profile.content.directionMarker,
      // The glyphs look is the chip with the network glyph, whatever the content toggle says;
      // a layout that maps it to another look draws that look's own prefix.
      showNetworkGlyph = profile.content.showNetworkGlyph || renderStyle == OverlayStyle.GLYPHS,
      combined = layout == Layout.COMBINED,
    )
  return OverlayComposition(values, measureValues, request, presentation)
}

/**
 * Springs the readout in when it is shown and out when it is not, on the graphics layer only, so
 * the window's own layout and origin report never see the animation. The service hides the window
 * after the exit has had time to finish. With animators off the readout is simply visible.
 */
@Composable
private fun Modifier.visibilityMotion(shown: Boolean, animatorsEnabled: Boolean): Modifier {
  val scale = remember { Animatable(if (animatorsEnabled) HIDDEN_SCALE else 1f) }
  val alpha = remember { Animatable(if (animatorsEnabled) 0f else 1f) }
  if (!animatorsEnabled) return this
  val spatialSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatialSpec()
  val effectsSpec: FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultEffectsSpec()
  LaunchedEffect(shown) {
    launch { scale.animateTo(if (shown) 1f else HIDDEN_SCALE, spatialSpec) }
    launch { alpha.animateTo(if (shown) 1f else 0f, effectsSpec) }
  }
  return graphicsLayer {
    scaleX = scale.value
    scaleY = scale.value
    this.alpha = alpha.value
  }
}

private const val HIDDEN_SCALE: Float = 0.9f

/**
 * Whether the platform runs animators, followed live. The animator scale setting and Battery
 * Saver's animation policy both reach the process as its duration scale, which Android 13 and later
 * report through a listener. Older releases give no callback, so the two sources are watched
 * instead; the window manager pushes the new scale into the process a moment after either changes,
 * so each signal reads the platform's answer at once and again shortly after.
 */
@Composable
private fun rememberAnimatorsEnabled(): State<Boolean> {
  val context = LocalContext.current
  return produceState(ValueAnimator.areAnimatorsEnabled(), context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val listener = ValueAnimator.DurationScaleChangeListener { scale -> value = scale != 0f }
      ValueAnimator.registerDurationScaleChangeListener(listener)
      awaitDispose { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
    } else {
      val handler = Handler(Looper.getMainLooper())
      val refresh = Runnable { value = ValueAnimator.areAnimatorsEnabled() }
      fun refreshTwice() {
        refresh.run()
        handler.postDelayed(refresh, SCALE_SETTLE_MS)
      }
      val resolver = context.contentResolver
      val observer =
        object : ContentObserver(handler) {
          override fun onChange(selfChange: Boolean) = refreshTwice()
        }
      resolver.registerContentObserver(
        Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
        false,
        observer,
      )
      val receiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) = refreshTwice()
        }
      ContextCompat.registerReceiver(
        context,
        receiver,
        IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
      awaitDispose {
        resolver.unregisterContentObserver(observer)
        context.unregisterReceiver(receiver)
        handler.removeCallbacks(refresh)
      }
    }
  }
}

/** How long after a scale signal the window manager has pushed the new scale into the process. */
private const val SCALE_SETTLE_MS: Long = 500L

/** Whether a look shows a unit label at all, for the two-line and combined layout mappings. */
private fun hasUnitLabel(style: OverlayStyle): Boolean =
  when (style) {
    OverlayStyle.CHIP,
    OverlayStyle.STACK,
    OverlayStyle.SINGLE,
    OverlayStyle.HALO,
    OverlayStyle.GLYPHS -> true
    OverlayStyle.COMPACT,
    OverlayStyle.COMPACT12,
    OverlayStyle.STACK_NO_UNIT,
    OverlayStyle.SINGLE_NO_UNIT -> false
  }

/**
 * TWO_LINES stacks the five pill looks onto their two-line equivalent; COMBINED always renders as
 * the single-value look. ONE_LINE and DOWNLOAD_ONLY draw the chosen style unchanged, since
 * DOWNLOAD_ONLY only hides a direction rather than changing the look.
 */
internal fun styleForLayout(style: OverlayStyle, layout: Layout): OverlayStyle =
  when (layout) {
    Layout.ONE_LINE,
    Layout.DOWNLOAD_ONLY -> style
    Layout.TWO_LINES ->
      when (style) {
        OverlayStyle.CHIP,
        OverlayStyle.COMPACT,
        OverlayStyle.COMPACT12,
        OverlayStyle.GLYPHS,
        OverlayStyle.HALO ->
          if (hasUnitLabel(style)) OverlayStyle.STACK else OverlayStyle.STACK_NO_UNIT
        OverlayStyle.STACK,
        OverlayStyle.STACK_NO_UNIT,
        OverlayStyle.SINGLE,
        OverlayStyle.SINGLE_NO_UNIT -> style
      }
    Layout.COMBINED -> if (hasUnitLabel(style)) OverlayStyle.SINGLE else OverlayStyle.SINGLE_NO_UNIT
  }

/** The widest number and unit the format can produce, in both directions, for measuring. */
private fun widestValues(formatter: SpeedFormatter, options: FormatOptions): ReadoutValues {
  val number = formatter.widestNumber(options)
  val unit = formatter.widestUnit(options)
  val spokenUnit = formatter.widestUnit(options.copy(unitLabel = UnitLabel.FULL))
  val stacked = options.forStackAndHalves()
  val stackNumber = formatter.widestNumber(stacked)
  val stackUnit = formatter.widestUnit(stacked)
  return ReadoutValues(
    downloadNumber = number,
    downloadUnit = unit,
    uploadNumber = number,
    uploadUnit = unit,
    downloadBytesPerSecond = 1,
    uploadBytesPerSecond = 0,
    // Every network glyph is the same size, so any transport reserves the room the glyph takes.
    transport = Transport.WIFI,
    downloadSpokenUnit = spokenUnit,
    uploadSpokenUnit = spokenUnit,
    downloadStackNumber = stackNumber,
    uploadStackNumber = stackNumber,
    downloadStackUnit = stackUnit,
    uploadStackUnit = stackUnit,
  )
}

/**
 * The sum of both directions rendered through the single-value look, download and upload swap for
 * the dominant-direction choice so the arrow follows the larger side, and the transport glyph.
 */
private fun combinedValues(
  sample: SpeedSample?,
  formatter: SpeedFormatter,
  options: FormatOptions,
  transport: Transport?,
  loading: Boolean,
): ReadoutValues {
  val totalBytesPerSecond =
    if (sample == null || !sample.supported) 0L
    else sample.rxBytesPerSecond + sample.txBytesPerSecond
  val combined =
    if (sample == null || !sample.supported) {
      formatter.format(Long.MIN_VALUE, options)
    } else {
      formatter.format(totalBytesPerSecond, options)
    }
  val spokenUnit =
    if (sample == null || !sample.supported) ""
    else formatter.format(totalBytesPerSecond, options.copy(unitLabel = UnitLabel.FULL)).unit
  return ReadoutValues(
    downloadNumber = combined.number,
    downloadUnit = combined.unit,
    uploadNumber = combined.number,
    uploadUnit = combined.unit,
    downloadBytesPerSecond = totalBytesPerSecond,
    uploadBytesPerSecond = totalBytesPerSecond,
    transport = transport,
    downloadSpokenUnit = spokenUnit,
    uploadSpokenUnit = spokenUnit,
    loading = loading && sample == null,
  )
}

/**
 * Unsupported counters, a stopped source, and the wait for a first sample all carry dashes; only
 * the wait is marked as [loading], so the looks draw it instead. Every look except the two-line
 * stack shows at most one unit label, so both directions share one prefix; the stack labels each
 * line and keeps per-direction prefixes, and the flank halves draw the same label as the stack.
 */
internal fun SpeedSample?.toValues(
  formatter: SpeedFormatter,
  profile: Profile,
  renderStyle: OverlayStyle,
  loading: Boolean = false,
): ReadoutValues {
  val options = profile.content.format
  val sharedPrefix = profile.position.anchor.inStatusBar || renderStyle != OverlayStyle.STACK
  val download: FormattedSpeed
  val upload: FormattedSpeed
  val spoken: Pair<String, String>
  val labeled = options.copy(unitLabel = UnitLabel.FULL)
  val stacked = options.forStackAndHalves()
  val stack: Pair<FormattedSpeed, FormattedSpeed>
  if (this == null || !supported) {
    download = formatter.format(Long.MIN_VALUE, options)
    upload = download
    spoken = "" to ""
    stack = download to download
  } else if (sharedPrefix) {
    val pair = formatter.formatPair(rxBytesPerSecond, txBytesPerSecond, options)
    download = pair.first
    upload = pair.second
    val spokenPair = formatter.formatPair(rxBytesPerSecond, txBytesPerSecond, labeled)
    spoken = spokenPair.first.unit to spokenPair.second.unit
    stack = formatter.formatPair(rxBytesPerSecond, txBytesPerSecond, stacked)
  } else {
    download = formatter.format(rxBytesPerSecond, options)
    upload = formatter.format(txBytesPerSecond, options)
    spoken =
      formatter.format(rxBytesPerSecond, labeled).unit to
        formatter.format(txBytesPerSecond, labeled).unit
    stack =
      formatter.format(rxBytesPerSecond, stacked) to formatter.format(txBytesPerSecond, stacked)
  }
  return ReadoutValues(
    downloadNumber = download.number,
    downloadUnit = download.unit,
    uploadNumber = upload.number,
    uploadUnit = upload.unit,
    downloadBytesPerSecond = if (this != null && supported) rxBytesPerSecond else 0,
    uploadBytesPerSecond = if (this != null && supported) txBytesPerSecond else 0,
    transport = this?.transport,
    downloadSpokenUnit = spoken.first,
    uploadSpokenUnit = spoken.second,
    downloadStackNumber = stack.first.number,
    uploadStackNumber = stack.second.number,
    downloadStackUnit = stack.first.unit,
    uploadStackUnit = stack.second.unit,
    loading = loading && this == null,
  )
}
