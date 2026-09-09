package tools.zach.app.pixelspeed.signals

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** A fake platform value with a registration the test controls. */
@OptIn(ExperimentalCoroutinesApi::class)
class WatchedValueTest {

  private class Platform {
    var value: Boolean = true
    var onChange: (() -> Unit)? = null
    var registrations: Int = 0

    fun change(to: Boolean) {
      value = to
      onChange?.invoke()
    }

    fun watch(onChange: () -> Unit): () -> Unit {
      registrations++
      this.onChange = onChange
      return { this.onChange = null }
    }
  }

  private fun TestScope.source(platform: Platform) =
    watchedValue(
      scope = backgroundScope,
      stopTimeoutMillis = STOP_MS,
      read = { platform.value },
      watch = platform::watch,
    )

  @Test
  fun `the first value a collector sees is read after the watcher is registered`() = runTest {
    // The platform flips right as the watcher registers, before any read.
    val flipping = Platform().apply { value = true }
    val source =
      watchedValue(
        scope = backgroundScope,
        stopTimeoutMillis = STOP_MS,
        read = { flipping.value },
        watch = { onChange ->
          val unregister = flipping.watch(onChange)
          flipping.value = false
          unregister
        },
      )

    val first = source.first()

    assertEquals(false, first, "the value after registration, not before")
  }

  @Test
  fun `a change while collecting reaches the collector`() = runTest {
    val platform = Platform()
    val seen = mutableListOf<Boolean>()

    source(platform).take(2).collect { value ->
      seen += value
      if (seen.size == 1) platform.change(false)
    }

    assertEquals(listOf(true, false), seen)
  }

  @Test
  fun `a collector arriving after the watcher stopped gets a fresh read, not the last value`() =
    runTest {
      val platform = Platform()
      val source = source(platform)
      val first = launch { source.toList(mutableListOf()) }
      advanceUntilIdle()
      platform.change(false)
      advanceUntilIdle()
      first.cancel()
      advanceTimeBy(STOP_MS + 1)
      advanceUntilIdle()
      assertNull(platform.onChange, "the watcher stopped after the timeout")

      // The value flips back while nobody watches; the next collector must not see false.
      platform.value = true
      val second = source.first()

      assertEquals(true, second)
      assertEquals(2, platform.registrations, "the watcher registered again")
    }

  @Test
  fun `a collector joining a live watcher gets the live value at once`() = runTest {
    val platform = Platform()
    val source = source(platform)
    val first = launch { source.toList(mutableListOf()) }
    advanceUntilIdle()
    platform.change(false)
    advanceUntilIdle()

    val joined = source.first()

    assertEquals(false, joined)
    assertEquals(1, platform.registrations, "one watcher serves both collectors")
    first.cancel()
  }

  @Test
  fun `a change delivered on another thread between a read and its send is not overwritten`() {
    // Real threads: the producer reads true and is held before its send; a revoke lands from
    // another thread meanwhile. The last value the collector sees must be the platform's.
    val producerRead = CountDownLatch(1)
    val revokeDone = CountDownLatch(1)
    val platform = Platform()
    var reads = 0
    val read: () -> Boolean = {
      val value = platform.value
      if (++reads == 1) {
        producerRead.countDown()
        revokeDone.await(5, TimeUnit.SECONDS)
      }
      value
    }
    val scope = CoroutineScope(Dispatchers.Default)
    val source = watchedValue(scope, STOP_MS, read, platform::watch)
    val seen = mutableListOf<Boolean>()
    val collector = scope.launch { source.collect { seen += it } }
    producerRead.await(5, TimeUnit.SECONDS)
    val revoker = thread {
      platform.value = false
      platform.onChange?.invoke()
      revokeDone.countDown()
    }
    revoker.join(5_000)
    runBlocking { withTimeout(5_000) { while (seen.lastOrNull() != false) delay(10) } }
    assertEquals(false, seen.last(), "the collector ends on the platform's value")
    collector.cancel()
    scope.cancel()
  }

  @Test
  fun `a watcher the platform refuses still yields one read`() = runTest {
    val platform = Platform()
    val source =
      watchedValue(
        scope = backgroundScope,
        stopTimeoutMillis = STOP_MS,
        read = { platform.value },
        watch = { throw SecurityException("refused") },
      )

    assertEquals(true, source.first())
  }

  private companion object {
    const val STOP_MS: Long = 5_000L
  }
}
