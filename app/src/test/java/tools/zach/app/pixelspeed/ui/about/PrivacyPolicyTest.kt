package tools.zach.app.pixelspeed.ui.about

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PrivacyPolicyTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  fun `parse follows the privacy policy markdown subset`(
    label: String,
    input: String,
    expected: List<PolicySection>,
  ) {
    assertEquals(expected, PrivacyPolicy.parse(input), label)
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("policyFiles")
  fun `every shipped policy parses into four sections with a note on every permission`(
    name: String,
    file: File,
  ) {
    val sections = PrivacyPolicy.parse(file.readText())

    assertEquals(4, sections.count { it.title != null }, "$name: four titled sections")
    val permissions = sections.first {
      it.title != null && it.blocks.any { b -> b is PolicyBlock.Items }
    }
    val items = permissions.blocks.filterIsInstance<PolicyBlock.Items>().single().items
    assertEquals(7, items.size, "$name: seven permissions")
    items.forEach { item -> assertTrue(item.note != null, "$name: no note on ${item.title}") }
  }

  companion object {
    @JvmStatic
    fun rows(): List<Arguments> =
      listOf(
        Arguments.of(
          "a `## ` heading starts a section",
          "## What the app reads\n\nno data leaves the phone\n",
          listOf(
            PolicySection(
              "What the app reads",
              listOf(PolicyBlock.Paragraph("no data leaves the phone")),
            )
          ),
        ),
        Arguments.of(
          "wrapped lines join into one paragraph with a space",
          "## Section\n\nfirst line\nsecond line\nthird line\n",
          listOf(
            PolicySection(
              "Section",
              listOf(PolicyBlock.Paragraph("first line second line third line")),
            )
          ),
        ),
        Arguments.of(
          "a blank line starts a new paragraph in the same section",
          "## Section\n\nfirst paragraph\n\nsecond paragraph\n",
          listOf(
            PolicySection(
              "Section",
              listOf(
                PolicyBlock.Paragraph("first paragraph"),
                PolicyBlock.Paragraph("second paragraph"),
              ),
            )
          ),
        ),
        Arguments.of(
          "an item with a constant splits into title, detail, and note",
          "## Permissions\n\n- Display over other apps (SYSTEM_ALERT_WINDOW): draws the readout.\n",
          listOf(
            PolicySection(
              "Permissions",
              listOf(
                PolicyBlock.Items(
                  listOf(
                    PolicyItem(
                      title = "Display over other apps",
                      detail = "draws the readout.",
                      note = "SYSTEM_ALERT_WINDOW",
                    )
                  )
                )
              ),
            )
          ),
        ),
        Arguments.of(
          "a spaced or full width colon after the constant splits the same way",
          "## Permissions\n\n- Afficher (SYSTEM_ALERT_WINDOW) : dessine.\n- 通知 (POST_NOTIFICATIONS)：通知。\n",
          listOf(
            PolicySection(
              "Permissions",
              listOf(
                PolicyBlock.Items(
                  listOf(
                    PolicyItem(
                      title = "Afficher",
                      detail = "dessine.",
                      note = "SYSTEM_ALERT_WINDOW",
                    ),
                    PolicyItem(title = "通知", detail = "通知。", note = "POST_NOTIFICATIONS"),
                  )
                )
              ),
            )
          ),
        ),
        Arguments.of(
          "an item without a colon is title only",
          "## Permissions\n\n- No network permission\n",
          listOf(
            PolicySection(
              "Permissions",
              listOf(
                PolicyBlock.Items(
                  listOf(PolicyItem(title = "No network permission", detail = null, note = null))
                )
              ),
            )
          ),
        ),
        Arguments.of(
          "a wrapped item joins its continuation line before splitting",
          "## Permissions\n\n- Notifications (POST_NOTIFICATIONS): the service notification with\n  pause and resume.\n",
          listOf(
            PolicySection(
              "Permissions",
              listOf(
                PolicyBlock.Items(
                  listOf(
                    PolicyItem(
                      title = "Notifications",
                      detail = "the service notification with pause and resume.",
                      note = "POST_NOTIFICATIONS",
                    )
                  )
                )
              ),
            )
          ),
        ),
        Arguments.of(
          "a markdown link collapses to its text and backticks are stripped",
          "## Section\n\nsee [the repo](https://example.com) and `TrafficStats`\n",
          listOf(
            PolicySection(
              "Section",
              listOf(PolicyBlock.Paragraph("see the repo and TrafficStats")),
            )
          ),
        ),
        Arguments.of(
          "a bare URL is left alone",
          "## Contact\n\nopen an issue at https://example.com/issues\n",
          listOf(
            PolicySection(
              "Contact",
              listOf(PolicyBlock.Paragraph("open an issue at https://example.com/issues")),
            )
          ),
        ),
        Arguments.of(
          "the top `# ` heading is dropped, and text before any `## ` lands in a null-titled section",
          "# Privacy policy\n\nPixel Speed collects no data.\n\n## Contact\n\nan email\n",
          listOf(
            PolicySection(null, listOf(PolicyBlock.Paragraph("Pixel Speed collects no data."))),
            PolicySection("Contact", listOf(PolicyBlock.Paragraph("an email"))),
          ),
        ),
        Arguments.of(
          "a document with no headings is one null-titled section",
          "just a paragraph\n",
          listOf(PolicySection(null, listOf(PolicyBlock.Paragraph("just a paragraph")))),
        ),
        Arguments.of("an empty document gives an empty list", "", emptyList<PolicySection>()),
      )

    /** The unit test runs with the module directory as its working directory. */
    @JvmStatic
    fun policyFiles(): List<Arguments> =
      File("..")
        .listFiles { file -> file.name.startsWith("PRIVACY") && file.name.endsWith(".md") }
        .orEmpty()
        .sortedBy { it.name }
        .map { Arguments.of(it.name, it) }

    @JvmStatic
    fun assetNameRows(): List<Arguments> =
      listOf(
        Arguments.of("pt-BR", listOf("PRIVACY.pt-BR.md", "PRIVACY.pt.md", "PRIVACY.en.md")),
        Arguments.of("zh-CN", listOf("PRIVACY.zh-CN.md", "PRIVACY.zh.md", "PRIVACY.en.md")),
        Arguments.of(
          "zh-Hans-CN",
          listOf("PRIVACY.zh-Hans-CN.md", "PRIVACY.zh-CN.md", "PRIVACY.zh.md", "PRIVACY.en.md"),
        ),
        Arguments.of("de-DE", listOf("PRIVACY.de-DE.md", "PRIVACY.de.md", "PRIVACY.en.md")),
        Arguments.of("en-US", listOf("PRIVACY.en-US.md", "PRIVACY.en.md")),
      )
  }
}

class PolicyAssetNamesTest {
  @ParameterizedTest(name = "{0}")
  @MethodSource("tools.zach.app.pixelspeed.ui.about.PrivacyPolicyTest#assetNameRows")
  fun `policyAssetNames tries the full tag, then the language, then the base file`(
    tag: String,
    expected: List<String>,
  ) {
    assertEquals(expected, policyAssetNames(tag))
  }
}
