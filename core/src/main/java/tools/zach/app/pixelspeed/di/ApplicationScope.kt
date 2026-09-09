package tools.zach.app.pixelspeed.di

import javax.inject.Qualifier

/** The process-lifetime coroutine scope for work that outlives any one component. */
@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class ApplicationScope
