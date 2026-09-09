package tools.zach.app.pixelspeed.settings

import kotlinx.serialization.Serializable

/** How the two directions arrange on the overlay. */
@Serializable
enum class Layout {
  ONE_LINE,
  TWO_LINES,
  DOWNLOAD_ONLY,
  COMBINED,
}

/** Which direction reads first when both are shown. */
@Serializable
enum class DirectionOrder {
  DOWNLOAD_FIRST,
  UPLOAD_FIRST,
}

/** How a direction is marked next to its number. */
@Serializable
enum class DirectionMarker {
  ARROWS,
  LETTERS,
  NONE,
}

/** DYNAMIC follows the system theme; the others fix a palette regardless of theme. */
@Serializable
enum class ColorMode {
  DYNAMIC,
  CUSTOM,
  MONOCHROME_LIGHT,
  MONOCHROME_DARK,
}

/** SYSTEM uses the platform default typeface; MONOSPACE keeps digit widths aligned. */
@Serializable
enum class FontFamilyChoice {
  SYSTEM,
  MONOSPACE,
}

/** The readout's font weight. */
@Serializable
enum class FontWeightChoice {
  REGULAR,
  MEDIUM,
  BOLD,
}

/** What the overlay shows when the counter source reports no traffic. */
@Serializable
enum class OfflineMode {
  HIDE,
  DASHES,
}

/** Where a paused-state readout surfaces besides the overlay itself. */
@Serializable
enum class ReadoutSurface {
  OVERLAY,
  LIVE_UPDATE,
  BOTH,
}
