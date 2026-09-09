package tools.zach.app.pixelspeed.service

import java.util.stream.Stream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class NotificationPostPolicyTest {

  /** One thing that happens to the notification, in order. */
  sealed interface Step {
    /** A post carrying a state or, with [readout], the speed; [allowed] is what the shade sees. */
    data class Post(val readout: Boolean, val allowed: Boolean) : Step

    data object Dismiss : Step

    data object Start : Step
  }

  companion object {
    private fun state(allowed: Boolean): Step = Step.Post(readout = false, allowed = allowed)

    private fun readout(allowed: Boolean): Step = Step.Post(readout = true, allowed = allowed)

    @JvmStatic
    fun sequences(): Stream<Arguments> =
      Stream.of(
        Arguments.of("a fresh policy posts every state", listOf(state(true), state(true))),
        Arguments.of(
          "a swipe silences the states that follow",
          listOf(state(true), Step.Dismiss, state(false), state(false)),
        ),
        Arguments.of(
          "a swipe never silences the readout",
          listOf(Step.Dismiss, readout(true), readout(true)),
        ),
        Arguments.of(
          "the readout surface turning on lifts a swipe for the states after it",
          listOf(Step.Dismiss, state(false), readout(true), state(true)),
        ),
        Arguments.of(
          "a swipe on the readout notification changes nothing and leaves nothing behind",
          listOf(readout(true), Step.Dismiss, readout(true), state(true)),
        ),
        Arguments.of(
          "a start intent lifts a swipe",
          listOf(Step.Dismiss, state(false), Step.Start, state(true)),
        ),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("sequences")
  fun `posts follow the swipe, the start, and the readout surface`(
    name: String,
    steps: List<Step>,
  ) {
    val policy = NotificationPostPolicy()
    steps.forEachIndexed { index, step ->
      when (step) {
        is Step.Post ->
          assertEquals(step.allowed, policy.onPost(step.readout), "$name, step $index")
        Step.Dismiss -> policy.onDismissed()
        Step.Start -> policy.onStarted()
      }
    }
  }
}
