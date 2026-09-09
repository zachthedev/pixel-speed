package tools.zach.app.pixelspeed.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import tools.zach.app.pixelspeed.state.ReadoutSink

/** Declares the readout sink set so it exists even before any module binds a sink into it. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SinkModule {
  @Multibinds abstract fun readoutSinks(): Set<ReadoutSink>
}
