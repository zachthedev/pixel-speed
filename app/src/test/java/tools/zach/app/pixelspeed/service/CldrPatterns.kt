package tools.zach.app.pixelspeed.service

import java.lang.reflect.Method
import java.time.chrono.Chronology
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * The JDK's CLDR pattern generator as a [DeadlineFormatter] pattern source. It stands in for ICU on
 * device: both read CLDR, but from different releases and with different choices (ICU gives zh-TW
 * `ah:mm` where the JDK gives `Bh:mm`, and orders a time and a date differently in a few locales),
 * so a row here exercises a real CLDR pattern, not the device's exact one. Unit tests compile
 * against android.jar, whose java.time has no template overload, while the JDK running them does,
 * so the call goes through reflection.
 */
object CldrPatterns {
  private val method: Method =
    try {
      DateTimeFormatterBuilder::class
        .java
        .getMethod(
          "getLocalizedDateTimePattern",
          String::class.java,
          Chronology::class.java,
          Locale::class.java,
        )
    } catch (e: NoSuchMethodException) {
      throw IllegalStateException("CldrPatterns needs the JDK 19+ template overload", e)
    }

  val bestPattern: (Locale, String) -> String = { locale, skeleton ->
    method.invoke(null, skeleton, IsoChronology.INSTANCE, locale) as String
  }
}
