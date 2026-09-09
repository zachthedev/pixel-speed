package tools.zach.app.pixelspeed.overlay

import kotlinx.serialization.Serializable

/** Where the window sits. Status bar anchors read insets and cutout bounds at runtime. */
@Serializable
enum class Anchor {
  STATUS_BAR_CUTOUT,
  STATUS_BAR_START,
  STATUS_BAR_BEFORE_CUTOUT,
  STATUS_BAR_AFTER_CUTOUT,
  STATUS_BAR_END,
  TOP_START,
  TOP_END,
  BOTTOM_START,
  BOTTOM_END,
  CUSTOM;

  val inStatusBar: Boolean
    get() =
      this == STATUS_BAR_CUTOUT ||
        this == STATUS_BAR_START ||
        this == STATUS_BAR_BEFORE_CUTOUT ||
        this == STATUS_BAR_AFTER_CUTOUT ||
        this == STATUS_BAR_END
}

/** Whether a landscape display keeps the portrait anchor or uses its own. */
@Serializable
enum class LandscapeAnchorMode {
  SAME,
  SEPARATE,
}

/**
 * AUTO picks the first status bar look that fits the free span; MANUAL uses the chosen style; SLIM
 * is one 14 dp ribbon at 9 sp in the strip above the cutout, where nothing else draws.
 */
@Serializable
enum class StatusBarFit {
  AUTO,
  MANUAL,
  SLIM,
}

/** The nine looks. The status bar auto-fit order is COMPACT, COMPACT12, STACK, STACK_NO_UNIT. */
@Serializable
enum class OverlayStyle {
  CHIP,
  COMPACT,
  COMPACT12,
  STACK,
  STACK_NO_UNIT,
  SINGLE,
  SINGLE_NO_UNIT,
  HALO,
  GLYPHS,
}
