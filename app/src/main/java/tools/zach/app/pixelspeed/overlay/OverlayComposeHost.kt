package tools.zach.app.pixelspeed.overlay

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * The overlay root. Edit mode attaches a touch listener for dragging, and a view that takes touches
 * must answer [performClick] so accessibility services can reach the same path.
 */
class OverlayView(context: Context) : FrameLayout(context) {
  override fun performClick(): Boolean {
    super.performClick()
    return true
  }
}

/**
 * The owner graph a ComposeView needs when no Activity provides one. The recomposer looks these up
 * through the view tree, so every setter runs before the view is attached to a window. The frame
 * clock only advances at STARTED or above, which is why [show] and [hide] move the lifecycle.
 */
class OverlayComposeHost(context: Context) :
  LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

  private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
  private val savedStateController: SavedStateRegistryController =
    SavedStateRegistryController.create(this)
  private val store: ViewModelStore = ViewModelStore()

  override val lifecycle: Lifecycle
    get() = lifecycleRegistry

  override val savedStateRegistry: SavedStateRegistry
    get() = savedStateController.savedStateRegistry

  override val viewModelStore: ViewModelStore
    get() = store

  private val composeView: ComposeView = ComposeView(context)

  /** The window root wraps the composition so the drag listener lands on a view of our own. */
  val view: OverlayView =
    OverlayView(context).also { root ->
      savedStateController.performAttach()
      savedStateController.performRestore(null)
      root.setViewTreeLifecycleOwner(this)
      root.setViewTreeSavedStateRegistryOwner(this)
      root.setViewTreeViewModelStoreOwner(this)
      composeView.setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
      )
      root.addView(composeView)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

  fun setContent(content: @Composable () -> Unit) {
    composeView.setContent(content)
  }

  fun show() {
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
  }

  fun hide() {
    if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
      lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }
  }

  /** Runs before the view leaves the window; the store is cleared after removal by the caller. */
  fun destroy() {
    hide()
    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
  }

  fun clearViewModels() {
    store.clear()
  }
}
