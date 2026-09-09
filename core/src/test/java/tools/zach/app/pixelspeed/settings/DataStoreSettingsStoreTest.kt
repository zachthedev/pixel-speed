package tools.zach.app.pixelspeed.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A [DataStore] double backed by a state flow, with no filesystem or Android classes. */
private class FakeDataStore(initial: Settings) : DataStore<Settings> {
  private val state = MutableStateFlow(initial)
  override val data: Flow<Settings> = state

  override suspend fun updateData(transform: suspend (Settings) -> Settings): Settings {
    val updated = transform(state.value)
    state.value = updated
    return updated
  }
}

class DataStoreSettingsStoreTest {
  @Test
  fun `settings exposes the current value from the data store`() = runBlocking {
    val dataStore = FakeDataStore(Settings(enabled = true))
    val store = DataStoreSettingsStore(dataStore)

    assertEquals(true, store.settings.first().enabled)
  }

  @Test
  fun `update applies the transform and returns the persisted result`() = runBlocking {
    val dataStore = FakeDataStore(Settings())
    val store = DataStoreSettingsStore(dataStore)

    val result = store.update { it.copy(enabled = true) }

    assertEquals(true, result.enabled)
    assertEquals(true, store.settings.first().enabled)
  }
}
