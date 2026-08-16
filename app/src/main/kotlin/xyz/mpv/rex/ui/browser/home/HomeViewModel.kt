package xyz.mpv.rex.ui.browser.home

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import xyz.mpv.rex.ui.browser.videolist.VideoWithPlaybackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
  application: Application,
) : ViewModel() {
  private val manager = HomeManager(application.applicationContext)

  private val _continueWatching = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val continueWatching: StateFlow<List<VideoWithPlaybackInfo>> = _continueWatching.asStateFlow()

  private val _recentlyAdded = MutableStateFlow<List<VideoWithPlaybackInfo>>(emptyList())
  val recentlyAdded: StateFlow<List<VideoWithPlaybackInfo>> = _recentlyAdded.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      _isLoading.value = true
      runCatching {
        _continueWatching.value = manager.getContinueWatching()
        _recentlyAdded.value = manager.getRecentlyAdded()
      }
      _isLoading.value = false
    }
  }

  companion object {
    fun factory(application: Application): ViewModelProvider.Factory =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
          HomeViewModel(application) as T
      }
  }
}
