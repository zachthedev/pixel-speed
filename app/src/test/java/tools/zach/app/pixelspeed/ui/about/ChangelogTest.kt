package tools.zach.app.pixelspeed.ui.about

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ChangelogTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  fun `parse follows the release-please shape`(
    label: String,
    input: String,
    expected: List<Release>,
  ) {
    assertEquals(expected, Changelog.parse(input), label)
  }

  companion object {
    @JvmStatic
    fun rows(): List<Arguments> =
      listOf(
        Arguments.of(
          "rule 1: version and date come from the bracketed link and trailing parens",
          "## [1.1.0](https://github.com/zachthedev/pixel-speed/compare/v1.0.0...v1.1.0) (2026-09-10)\n" +
            "\n" +
            "* an item\n",
          listOf(
            Release(
              version = "1.1.0",
              date = "2026-09-10",
              sections = listOf(Section("Changes", listOf("an item"))),
            )
          ),
        ),
        Arguments.of(
          "rule 1: no brackets falls back to the heading text up to the first space, no date",
          "## Unreleased\n\n* an item\n",
          listOf(
            Release(
              version = "Unreleased",
              date = null,
              sections = listOf(Section("Changes", listOf("an item"))),
            )
          ),
        ),
        Arguments.of(
          "rule 2: items before any section land in a section titled Changes",
          "## 1.0.0 (2026-08-01)\n\n* first\n* second\n",
          listOf(
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections = listOf(Section("Changes", listOf("first", "second"))),
            )
          ),
        ),
        Arguments.of(
          "rule 2: a heading after items starts a new named section",
          "## 1.0.0 (2026-08-01)\n\n### Features\n\n* a feature\n\n### Bug Fixes\n\n* a fix\n",
          listOf(
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections =
                listOf(
                  Section("Features", listOf("a feature")),
                  Section("Bug Fixes", listOf("a fix")),
                ),
            )
          ),
        ),
        Arguments.of(
          "rule 3: a markdown link collapses to its text",
          "## 1.0.0 (2026-08-01)\n\n* see [docs](https://example.com/docs) for details\n",
          listOf(
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections = listOf(Section("Changes", listOf("see docs for details"))),
            )
          ),
        ),
        Arguments.of(
          "rule 3: a trailing commit link is removed entirely",
          "## 1.0.0 (2026-08-01)\n\n* slim status bar fit ([a1b2c3d](https://github.com/zachthedev/pixel-speed/commit/a1b2c3d))\n",
          listOf(
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections = listOf(Section("Changes", listOf("slim status bar fit"))),
            )
          ),
        ),
        Arguments.of(
          "rule 3: a bare trailing hash with no brackets is left alone",
          "## 1.0.0 (2026-08-01)\n\n* keep the pause deadline (a1b2c3d)\n",
          listOf(
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections = listOf(Section("Changes", listOf("keep the pause deadline (a1b2c3d)"))),
            )
          ),
        ),
        Arguments.of(
          "rule 3: a dash-prefixed item parses the same as an asterisk item",
          "## 1.0.0 (2026-08-01)\n\n- a dash item\n",
          listOf(
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections = listOf(Section("Changes", listOf("a dash item"))),
            )
          ),
        ),
        Arguments.of(
          "rule 4: the top heading and blank lines are ignored",
          "# Changelog\n\n## 1.0.0 (2026-08-01)\n\n* an item\n",
          listOf(
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections = listOf(Section("Changes", listOf("an item"))),
            )
          ),
        ),
        Arguments.of("rule 5: empty input gives an empty list", "", emptyList<Release>()),
        Arguments.of(
          "rule 5: a release with no items still appears with empty sections removed",
          "## 1.0.0 (2026-08-01)\n\n### Features\n",
          listOf(Release(version = "1.0.0", date = "2026-08-01", sections = emptyList())),
        ),
        Arguments.of(
          "rule 6: releases keep file order, newest first",
          "## 2.0.0 (2026-09-10)\n\n* newest\n\n## 1.0.0 (2026-08-01)\n\n* oldest\n",
          listOf(
            Release(
              version = "2.0.0",
              date = "2026-09-10",
              sections = listOf(Section("Changes", listOf("newest"))),
            ),
            Release(
              version = "1.0.0",
              date = "2026-08-01",
              sections = listOf(Section("Changes", listOf("oldest"))),
            ),
          ),
        ),
      )
  }
}
