package tools.zach.app.pixelspeed.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import tools.zach.app.pixelspeed.settings.Profile

@OptIn(ExperimentalCoroutinesApi::class)
class ReadoutPublisherTest {

  enum class Behavior {
    FAST,
    THROWS_EXCEPTION,
    THROWS_ERROR,
    SUSPENDS_FOREVER,
  }

  /** Records what it receives and how it ended; misbehaves per [behavior]. */
  class FakeSink(private val behavior: Behavior) : ReadoutSink {
    val received: MutableList<Readout> = mutableListOf()
    var cancelled: Boolean = false

    override suspend fun publish(readout: Readout) {
      received += readout
      when (behavior) {
        Behavior.FAST -> Unit
        Behavior.THROWS_EXCEPTION -> throw IllegalStateException("sink failed")
        Behavior.THROWS_ERROR -> throw NoClassDefFoundError("com/example/WearClient")
        Behavior.SUSPENDS_FOREVER ->
          try {
            awaitCancellation()
          } catch (e: CancellationException) {
            cancelled = true
            throw e
          }
      }
    }
  }

  private val readout: Readout = Readout(MonitorState.Off, null, Profile())

  companion object {
    @JvmStatic
    fun rows(): List<Arguments> =
      listOf(
        Arguments.of("a fast sink publishes and reports nothing", Behavior.FAST, null),
        Arguments.of(
          "an exception is reported and the sibling still publishes",
          Behavior.THROWS_EXCEPTION,
          IllegalStateException::class.java,
        ),
        Arguments.of(
          "an error is reported and the sibling still publishes",
          Behavior.THROWS_ERROR,
          NoClassDefFoundError::class.java,
        ),
        Arguments.of(
          "a sink past the timeout is cut off and the sibling still publishes",
          Behavior.SUSPENDS_FOREVER,
          TimeoutCancellationException::class.java,
        ),
      )
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("rows")
  fun `one sink's behavior never reaches its sibling`(
    name: String,
    behavior: Behavior,
    reported: Class<out Throwable>?,
  ) = runTest {
    val failures = mutableListOf<Pair<ReadoutSink, Throwable>>()
    val publisher =
      ReadoutPublisher(StandardTestDispatcher(testScheduler), timeoutMillis = 1_000) { sink, error
        ->
        failures += sink to error
      }
    val subject = FakeSink(behavior)
    val sibling = FakeSink(Behavior.FAST)

    publisher.publish(readout, listOf(subject, sibling))

    assertEquals(listOf(readout), sibling.received, "$name: the sibling published")
    assertSame(readout, subject.received.single(), "$name: the same readout instance")
    if (reported == null) {
      assertTrue(failures.isEmpty(), "$name: nothing reported")
    } else {
      val (sink, error) = failures.single()
      assertSame(subject, sink, "$name: the failing sink is named")
      assertTrue(reported.isInstance(error), "$name: reported ${error::class.simpleName}")
    }
  }

  @Test
  fun `cancelling the caller cancels the sinks and reports no failure`() = runTest {
    val failures = mutableListOf<Throwable>()
    val publisher =
      ReadoutPublisher(StandardTestDispatcher(testScheduler), timeoutMillis = 60_000) { _, error ->
        failures += error
      }
    val sink = FakeSink(Behavior.SUSPENDS_FOREVER)

    val job = launch { publisher.publish(readout, listOf(sink)) }
    testScheduler.runCurrent()
    job.cancel()
    advanceUntilIdle()

    assertTrue(sink.cancelled, "the sink saw the cancellation")
    assertTrue(failures.isEmpty(), "cancellation is not a failure")
  }

  @Test
  fun `an empty target list publishes nothing and returns`() = runTest {
    val publisher =
      ReadoutPublisher(StandardTestDispatcher(testScheduler), timeoutMillis = 1_000) { _, _ ->
        error("no sink to fail")
      }
    publisher.publish(readout, emptyList())
  }

  @Test
  fun `a sink that blocks its thread delays only itself`() {
    // A real multi-thread dispatcher and real time: the blocking sink holds one thread for 300 ms
    // while the fast sink lands well inside that window on another.
    val fastAt = java.util.concurrent.atomic.AtomicLong(0)
    val blockingDone = java.util.concurrent.atomic.AtomicLong(0)
    val blocking =
      object : ReadoutSink {
        override suspend fun publish(readout: Readout) {
          @Suppress("BlockingMethodInNonBlockingContext") Thread.sleep(300)
          blockingDone.set(System.nanoTime())
        }
      }
    val fast =
      object : ReadoutSink {
        override suspend fun publish(readout: Readout) {
          fastAt.set(System.nanoTime())
        }
      }
    val publisher = ReadoutPublisher(Dispatchers.Default, timeoutMillis = 5_000) { _, _ -> }

    val start = System.nanoTime()
    runBlocking { publisher.publish(readout, listOf(blocking, fast)) }

    val fastMillis = (fastAt.get() - start) / 1_000_000
    val blockingMillis = (blockingDone.get() - start) / 1_000_000
    assertTrue(fastMillis < 150, "the fast sink published after $fastMillis ms")
    assertTrue(blockingMillis >= 300, "the blocking sink finished after $blockingMillis ms")
    assertFalse(fastAt.get() > blockingDone.get(), "the fast sink did not wait for the blocker")
  }
}
