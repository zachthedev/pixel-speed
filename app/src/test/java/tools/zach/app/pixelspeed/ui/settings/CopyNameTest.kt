package tools.zach.app.pixelspeed.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class CopyNameTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  fun `a copy name is unique and within the bound`(
    name: String,
    original: String,
    taken: List<String>,
    expected: String,
  ) {
    val result = copyName(original, taken, " copy")
    assertEquals(expected, result, name)
    assertTrue(result.length <= MAX_PROFILE_NAME_LENGTH, "over the bound: ${result.length}")
    assertTrue(taken.none { it.equals(result, ignoreCase = true) }, "collides with a taken name")
  }

  companion object {
    @JvmStatic
    fun rows(): List<Arguments> =
      listOf(
        Arguments.of("free suffix", "Work", listOf("Work"), "Work copy"),
        Arguments.of("second copy is numbered", "Work", listOf("Work", "Work copy"), "Work copy 2"),
        Arguments.of(
          "numbering continues past a gap",
          "Work",
          listOf("Work", "Work copy", "Work copy 2"),
          "Work copy 3",
        ),
        Arguments.of(
          "case does not make a name free",
          "Work",
          listOf("Work", "work COPY"),
          "Work copy 2",
        ),
        Arguments.of(
          "a full-length name is cut at the front to fit the suffix",
          "n".repeat(64),
          listOf("n".repeat(64)),
          "n".repeat(59) + " copy",
        ),
        Arguments.of(
          "a numbered copy of a full-length name still fits",
          "n".repeat(64),
          listOf("n".repeat(64), "n".repeat(59) + " copy"),
          "n".repeat(57) + " copy 2",
        ),
      )
  }
}
