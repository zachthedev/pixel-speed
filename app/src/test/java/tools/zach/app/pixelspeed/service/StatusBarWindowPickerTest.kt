package tools.zach.app.pixelspeed.service

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.overlay.NodeBox

class StatusBarWindowPickerTest {

  companion object {
    private const val STATUS_BAR_HEIGHT_PX = 68
    private val appWindow =
      WindowCandidate(
        title = "App",
        isSystemWindow = false,
        packageName = "tools.zach.app.pixelspeed",
        leftPx = 0,
        topPx = 68,
        rightPx = 427,
        bottomPx = 952,
      )
    private val barByTitle =
      WindowCandidate(
        title = "StatusBar",
        isSystemWindow = true,
        packageName = "com.android.systemui",
        leftPx = 0,
        topPx = 0,
        rightPx = 427,
        bottomPx = 200,
      )
    private val barByBounds =
      WindowCandidate(
        title = null,
        isSystemWindow = true,
        packageName = "com.android.systemui",
        leftPx = 0,
        topPx = 0,
        rightPx = 427,
        bottomPx = 68,
      )
    private val navigationBar =
      WindowCandidate(
        title = null,
        isSystemWindow = true,
        packageName = "com.android.systemui",
        leftPx = 0,
        topPx = 916,
        rightPx = 427,
        bottomPx = 952,
      )

    @JvmStatic
    fun rows(): Stream<Arguments> =
      Stream.of(
        Arguments.of(
          "a titled window taller than the inset is not the bar, whatever it is called",
          listOf(appWindow, barByTitle, navigationBar),
          null,
        ),
        Arguments.of(
          "the titled window wins over a system UI window of the same shape",
          listOf(appWindow, barByBounds, barByTitle.copy(bottomPx = 68)),
          2,
        ),
        Arguments.of(
          "with no matching title, the system window at the top no taller than the inset wins",
          listOf(appWindow, barByBounds, navigationBar),
          1,
        ),
        Arguments.of("no window matches either rule", listOf(appWindow, navigationBar), null),
        Arguments.of(
          "a window titled like the bar but rooted in another package never wins",
          listOf(appWindow, barByTitle.copy(bottomPx = 68, packageName = "com.example.overlay")),
          null,
        ),
        Arguments.of(
          "a titled window whose root could not be read is not the bar",
          listOf(appWindow, barByTitle.copy(bottomPx = 68, packageName = null)),
          null,
        ),
        Arguments.of(
          "an untitled window whose root could not be read is not the bar",
          listOf(appWindow, barByBounds.copy(packageName = null)),
          null,
        ),
        Arguments.of(
          "a bar-shaped System UI window above the display is not the bar",
          listOf(appWindow, barByBounds.copy(topPx = -500, bottomPx = -450)),
          null,
        ),
        Arguments.of(
          "a System UI window with no width is not the bar",
          listOf(appWindow, barByBounds.copy(leftPx = 200, rightPx = 200)),
          null,
        ),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  internal fun `pickIndex finds the status bar by title or by bounds`(
    name: String,
    windows: List<WindowCandidate>,
    expected: Int?,
  ) {
    assertEquals(expected, StatusBarWindowPicker.pickIndex(windows, STATUS_BAR_HEIGHT_PX), name)
  }

  @Test
  fun `pickIndex ignores a system window from another package`() {
    val otherPackage = barByBounds.copy(packageName = "com.android.launcher")
    assertNull(StatusBarWindowPicker.pickIndex(listOf(otherPackage), STATUS_BAR_HEIGHT_PX))
  }
}

private class FakeNode(
  private val label: Boolean,
  private val box: NodeBox,
  private val children: List<FakeNode> = emptyList(),
) : AccessibilityNodeAdapter {
  override val childCount: Int = children.size

  override fun childAt(index: Int): AccessibilityNodeAdapter? = children.getOrNull(index)

  override val hasLabel: Boolean = label

  override val boundsInScreen: NodeBox = box
}

class StatusBarLeafBoxesTest {

  @Test
  fun `a labeled leaf with no children contributes its own box`() {
    val leaf = FakeNode(label = true, box = NodeBox(0, 0, 10, 10))
    assertEquals(listOf(NodeBox(0, 0, 10, 10)), collectLeafBoxes(leaf))
  }

  @Test
  fun `an unlabeled leaf with no children contributes nothing`() {
    val leaf = FakeNode(label = false, box = NodeBox(0, 0, 10, 10))
    assertEquals(emptyList<NodeBox>(), collectLeafBoxes(leaf))
  }

  @Test
  fun `only the labeled leaves under a branch are collected`() {
    val labeled = FakeNode(label = true, box = NodeBox(0, 0, 10, 10))
    val unlabeled = FakeNode(label = false, box = NodeBox(20, 0, 30, 10))
    val root =
      FakeNode(label = false, box = NodeBox(0, 0, 30, 10), children = listOf(labeled, unlabeled))
    assertEquals(listOf(NodeBox(0, 0, 10, 10)), collectLeafBoxes(root))
  }

  @Test
  fun `a branch node with a label is not collected, only its labeled leaves are`() {
    val labeled = FakeNode(label = true, box = NodeBox(0, 0, 10, 10))
    val root = FakeNode(label = true, box = NodeBox(0, 0, 10, 10), children = listOf(labeled))
    assertEquals(listOf(NodeBox(0, 0, 10, 10)), collectLeafBoxes(root))
  }

  @Test
  fun `every visited child is reported to onVisited in postorder`() {
    val leaf = FakeNode(label = true, box = NodeBox(0, 0, 10, 10))
    val root = FakeNode(label = false, box = NodeBox(0, 0, 10, 10), children = listOf(leaf))
    val visited = mutableListOf<AccessibilityNodeAdapter>()
    collectLeafBoxes(root, onVisited = { visited += it })
    assertEquals(listOf(leaf), visited)
  }
}
