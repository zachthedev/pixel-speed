package tools.zach.app.pixelspeed.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow

/** The read and read-modify-write surface for the persisted [Settings]. */
interface SettingsStore {
  val settings: Flow<Settings>

  /** Applies [transform] to the current value and persists the result, returning it. */
  suspend fun update(transform: (Settings) -> Settings): Settings
}

/** [SettingsStore] backed by a Jetpack DataStore keyed on the [Settings] serializer. */
class DataStoreSettingsStore(private val dataStore: DataStore<Settings>) : SettingsStore {
  override val settings: Flow<Settings> = dataStore.data

  override suspend fun update(transform: (Settings) -> Settings): Settings =
    dataStore.updateData(transform)
}
