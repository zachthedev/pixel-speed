package tools.zach.app.pixelspeed.ui.about

/** One release-please heading and the sections found under it, in file order. */
data class Release(val version: String, val date: String?, val sections: List<Section>)

/** One `### ` heading inside a release, or the "Changes" bucket for items before any heading. */
data class Section(val title: String, val items: List<String>)

/** Parses the release-please changelog format into [Release] entries. */
object Changelog {
  private const val DEFAULT_SECTION_TITLE: String = "Changes"
  private val VERSION_PATTERN: Regex = Regex("""\[([^]]+)]""")
  private val DATE_PATTERN: Regex = Regex("""\((\d{4}-\d{2}-\d{2})\)\s*$""")
  private val TRAILING_COMMIT_LINK: Regex = Regex("""\s*\(\[[^]]+]\([^)]*\)\)\s*$""")
  private val MARKDOWN_LINK: Regex = Regex("""\[([^]]+)]\([^)]*\)""")

  fun parse(text: String): List<Release> {
    val releases: MutableList<MutableRelease> = mutableListOf()
    var current: MutableRelease? = null
    var sectionTitle: String = DEFAULT_SECTION_TITLE
    var items: MutableList<String> = mutableListOf()

    fun flushSection() {
      val release: MutableRelease = current ?: return
      if (items.isNotEmpty()) {
        release.sections += Section(sectionTitle, items.toList())
      }
      items = mutableListOf()
    }

    for (rawLine in text.lineSequence()) {
      val line: String = rawLine.trimEnd()
      when {
        line.startsWith("## ") -> {
          flushSection()
          sectionTitle = DEFAULT_SECTION_TITLE
          val release = MutableRelease(parseVersion(line), parseDate(line))
          current = release
          releases += release
        }
        line.startsWith("### ") -> {
          flushSection()
          sectionTitle = line.removePrefix("### ").trim()
        }
        line.startsWith("* ") || line.startsWith("- ") -> {
          if (current != null) items += parseItem(line.drop(2))
        }
      }
    }
    flushSection()
    return releases.map { Release(it.version, it.date, it.sections) }
  }

  private fun parseVersion(heading: String): String {
    val bracketed: MatchResult? = VERSION_PATTERN.find(heading)
    if (bracketed != null) return bracketed.groupValues[1]
    return heading.removePrefix("## ").trim().substringBefore(" ")
  }

  private fun parseDate(heading: String): String? = DATE_PATTERN.find(heading)?.groupValues?.get(1)

  private fun parseItem(text: String): String {
    val withoutCommitLink: String = TRAILING_COMMIT_LINK.replace(text, "")
    return MARKDOWN_LINK.replace(withoutCommitLink) { it.groupValues[1] }
  }

  private class MutableRelease(val version: String, val date: String?) {
    val sections: MutableList<Section> = mutableListOf()
  }
}
