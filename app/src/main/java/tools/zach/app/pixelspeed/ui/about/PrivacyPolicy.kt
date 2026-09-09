package tools.zach.app.pixelspeed.ui.about

/** One `## ` heading and the blocks under it, or a null title for text before any heading. */
data class PolicySection(val title: String?, val blocks: List<PolicyBlock>)

/** One unit of policy prose under a section: a paragraph, or a run of `- ` items. */
sealed interface PolicyBlock {
  data class Paragraph(val text: String) : PolicyBlock

  data class Items(val items: List<PolicyItem>) : PolicyBlock
}

/** One `- ` line. [note] is the parenthesized constant on a `Name (CONSTANT): detail` line. */
data class PolicyItem(val title: String, val detail: String?, val note: String?)

/** Parses the privacy policy's markdown subset: headings, paragraphs, and permission bullets. */
object PrivacyPolicy {
  private val MARKDOWN_LINK: Regex = Regex("""\[([^]]+)]\([^)]*\)""")
  // The colon after the constant is spaced in French and full width in Chinese.
  private val ITEM_WITH_NOTE: Regex = Regex("""^([^(]+?)\s*\(([^)]+)\)\s*[:\uFF1A]\s*(.*)$""")

  fun parse(text: String): List<PolicySection> {
    val sections: MutableList<MutableSection> = mutableListOf()
    var current: MutableSection? = null
    val paragraphLines: MutableList<String> = mutableListOf()
    val itemLines: MutableList<String> = mutableListOf()

    fun section(): MutableSection =
      current
        ?: MutableSection(null).also {
          current = it
          sections += it
        }

    fun flushParagraph() {
      if (paragraphLines.isEmpty()) return
      section().blocks += PolicyBlock.Paragraph(paragraphLines.joinToString(" "))
      paragraphLines.clear()
    }

    fun flushItems() {
      if (itemLines.isEmpty()) return
      section().blocks += PolicyBlock.Items(itemLines.map(::parseItem))
      itemLines.clear()
    }

    for (rawLine in text.lineSequence()) {
      val line: String = rawLine.trim()
      when {
        line.startsWith("# ") -> {
          flushParagraph()
          flushItems()
        }
        line.startsWith("## ") -> {
          flushParagraph()
          flushItems()
          val heading = MutableSection(cleanInline(line.removePrefix("## ").trim()))
          current = heading
          sections += heading
        }
        line.isEmpty() -> {
          flushParagraph()
          flushItems()
        }
        line.startsWith("- ") -> {
          flushParagraph()
          itemLines += line.removePrefix("- ").trim()
        }
        itemLines.isNotEmpty() -> itemLines[itemLines.lastIndex] += " $line"
        else -> {
          flushItems()
          paragraphLines += cleanInline(line)
        }
      }
    }
    flushParagraph()
    flushItems()
    return sections.map { PolicySection(it.title, it.blocks) }
  }

  private fun parseItem(rawLine: String): PolicyItem {
    val cleaned: String = cleanInline(rawLine)
    val match: MatchResult? = ITEM_WITH_NOTE.find(cleaned)
    if (match == null) return PolicyItem(title = cleaned.trim(), detail = null, note = null)
    val (title, note, detail) = match.destructured
    return PolicyItem(
      title = title.trim(),
      detail = detail.trim().ifEmpty { null },
      note = note.trim(),
    )
  }

  private fun cleanInline(text: String): String =
    MARKDOWN_LINK.replace(text) { it.groupValues[1] }.replace("`", "")

  private class MutableSection(val title: String?) {
    val blocks: MutableList<PolicyBlock> = mutableListOf()
  }
}

/**
 * The asset names to try for [tag], most specific first: the full tag, its language alone, then the
 * English policy every locale falls back to.
 */
internal fun policyAssetNames(tag: String): List<String> {
  val parts: List<String> = tag.split('-').filter { it.isNotEmpty() }
  val language: String = parts.firstOrNull().orEmpty()
  // A tag with a script subtag, zh-Hans-CN, also tries the language and region alone, zh-CN.
  val languageAndRegion: String? = if (parts.size >= 3) "$language-${parts.last()}" else null
  return listOfNotNull(
      "PRIVACY.$tag.md".takeIf { tag.isNotEmpty() },
      languageAndRegion?.let { "PRIVACY.$it.md" },
      "PRIVACY.$language.md".takeIf { language.isNotEmpty() },
      "PRIVACY.en.md",
    )
    .distinct()
}
