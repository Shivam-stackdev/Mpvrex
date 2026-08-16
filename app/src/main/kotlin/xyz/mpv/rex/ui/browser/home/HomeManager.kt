package xyz.mpv.rex.ui.browser.home

import android.content.Context
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.domain.playbackstate.repository.PlaybackStateRepository
import xyz.mpv.rex.domain.recentlyplayed.repository.RecentlyPlayedRepository
import xyz.mpv.rex.repository.MediaFileRepository
import xyz.mpv.rex.ui.browser.videolist.VideoWithPlaybackInfo
import xyz.mpv.rex.preferences.BrowserPreferences
import xyz.mpv.rex.preferences.AppearancePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Coordinates Home content while keeping discovery and history logic outside Compose. */
class HomeManager(
  private val context: Context,
) : KoinComponent {
  private val playbackStateRepository: PlaybackStateRepository by inject()
  private val recentlyPlayedRepository: RecentlyPlayedRepository by inject()
  private val browserPreferences: BrowserPreferences by inject()
  private val appearancePreferences: AppearancePreferences by inject()

  suspend fun getContinueWatching(limit: Int = DEFAULT_LIMIT): List<VideoWithPlaybackInfo> =
    withContext(Dispatchers.IO) {
      val videos = MediaFileRepository.getAllVideos(context)
      val states = playbackStateRepository.getAllPlaybackStates()
      val recentByPath = recentlyPlayedRepository.getRecentlyPlayed(limit = 1000)
        .mapIndexed { index, item -> item.filePath to (item.timestamp to index) }
        .toMap()

      videos.mapNotNull { video ->
        val state = states.firstOrNull { it.mediaTitle == video.path || it.mediaTitle == video.displayName }
          ?: return@mapNotNull null
        val durationSeconds = video.duration / 1000L
        val position = state.lastPosition.toLong()
        if (durationSeconds <= 0L || position <= 0L || position >= durationSeconds || state.timeRemaining <= 0) {
          return@mapNotNull null
        }
        val progress = (position.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
        video to VideoWithPlaybackInfo(
          video = video,
          timeRemaining = state.timeRemaining.toLong(),
          progressPercentage = progress,
          isWatched = false,
          isNeverPlayed = false,
        )
      }
        .sortedWith(compareByDescending<Pair<Video, VideoWithPlaybackInfo>> { recentByPath[it.first.path]?.first ?: 0L }
          .thenBy { recentByPath[it.first.path]?.second ?: Int.MAX_VALUE })
        .take(limit)
        .map { it.second }
    }

  suspend fun getRecentlyAdded(limit: Int = DEFAULT_LIMIT): List<VideoWithPlaybackInfo> =
    withContext(Dispatchers.IO) {
      val states = playbackStateRepository.getAllPlaybackStates()
      val thresholdMillis = appearancePreferences.unplayedOldVideoDays.get() * 24L * 60L * 60L * 1000L
      val watchedThreshold = browserPreferences.watchedThreshold.get()
      val now = System.currentTimeMillis()

      MediaFileRepository.getAllVideos(context)
        .sortedByDescending { it.dateAdded }
        .take(limit)
        .map { video ->
          val state = states.firstOrNull { it.mediaTitle == video.path || it.mediaTitle == video.displayName }
          val durationSeconds = video.duration / 1000L
          val position = state?.lastPosition?.toLong() ?: 0L
          val progress = if (durationSeconds > 0L && position > 0L && position < durationSeconds) {
            (position.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
          } else null
          val watched = state?.hasBeenWatched == true ||
            (durationSeconds > 0L && state != null && state.timeRemaining >= 0 &&
              ((durationSeconds - state.timeRemaining).toFloat() / durationSeconds.toFloat()) >= watchedThreshold / 100f)
          VideoWithPlaybackInfo(
            video = video,
            timeRemaining = state?.timeRemaining?.toLong(),
            progressPercentage = progress,
            isOldAndUnplayed = state == null && now - video.dateModified * 1000L <= thresholdMillis,
            isWatched = watched,
            isNeverPlayed = state == null,
          )
        }
    }

  companion object {
    const val DEFAULT_LIMIT = 12
  }
}
