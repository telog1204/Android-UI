package com.androidui.shade.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.androidui.shade.notification.NotificationActions
import com.androidui.shade.notification.ShadeNotificationListener
import com.androidui.shade.util.startActivitySafely
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 再生中のアプリ（メディアセッション）を読み取り、再生カードの内容と操作を提供する。
 * 「通知へのアクセス」の権限で読み取れる。
 */
class MediaSessionsController(
    private val context: Context,
    /** パネルを閉じる */
    private val collapse: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, ShadeNotificationListener::class.java)

    private val entries = LinkedHashMap<String, Entry>()
    private val artCache = HashMap<String, Pair<String, ImageBitmap?>>()
    private val iconCache = HashMap<String, ImageBitmap?>()
    private val appNames = HashMap<String, String>()
    private var registered = false

    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items.asStateFlow()

    /** セッション1つ分。状態が変わったら作り直す */
    private inner class Entry(val key: String, val controller: MediaController) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = rebuild()

        override fun onMetadataChanged(metadata: MediaMetadata?) = rebuild()

        override fun onSessionDestroyed() {
            entries.remove(key)?.let { runCatching { it.controller.unregisterCallback(it) } }
            rebuild()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers.orEmpty())
    }

    fun start() = tryRegister()

    /** パネルを開くときに呼ぶ（権限が後から許可された場合に備えて登録を試し直す） */
    fun onPanelShown() {
        tryRegister()
        rebuild()
    }

    fun stop() {
        if (registered) runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        registered = false
        entries.values.forEach { runCatching { it.controller.unregisterCallback(it) } }
        entries.clear()
        _items.value = emptyList()
    }

    // ---- 操作 ----

    fun playPause(item: MediaItem) {
        val controls = controlsOf(item) ?: return
        if (item.isPlaying) controls.pause() else controls.play()
    }

    fun previous(item: MediaItem) {
        controlsOf(item)?.skipToPrevious()
    }

    fun next(item: MediaItem) {
        controlsOf(item)?.skipToNext()
    }

    fun seekTo(item: MediaItem, positionMs: Long) {
        controlsOf(item)?.seekTo(positionMs)
    }

    fun sendCustomAction(item: MediaItem, action: String) {
        controlsOf(item)?.sendCustomAction(action, null)
    }

    /** 再生中のアプリを開く */
    fun open(item: MediaItem) {
        val opened = item.sessionActivity?.let { NotificationActions.send(context, it) } == true ||
            context.packageManager.getLaunchIntentForPackage(item.packageName)
                ?.let { context.startActivitySafely(it) } == true
        if (opened) collapse()
    }

    private fun controlsOf(item: MediaItem) = entries[item.key]?.controller?.transportControls

    // ---- セッションの読み取り ----

    private fun tryRegister() {
        if (registered) return
        val manager = sessionManager ?: return
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent, handler)
            registered = true
            updateControllers(manager.getActiveSessions(listenerComponent))
        } catch (e: SecurityException) {
            // 「通知へのアクセス」が未許可。パネルを開いたときに試し直す
        }
    }

    private fun updateControllers(controllers: List<MediaController>) {
        val ordered = LinkedHashMap<String, Entry>()
        controllers.forEach { controller ->
            val key = controller.packageName + "#" + controller.sessionToken.hashCode()
            ordered[key] = entries[key] ?: Entry(key, controller).also {
                runCatching { controller.registerCallback(it, handler) }
            }
        }
        entries.filterKeys { it !in ordered }.values.forEach { runCatching { it.controller.unregisterCallback(it) } }
        entries.clear()
        entries.putAll(ordered)
        rebuild()
    }

    private fun rebuild() {
        artCache.keys.retainAll(entries.keys)
        _items.value = entries.values.mapNotNull { runCatching { toItem(it) }.getOrNull() }
    }

    private fun toItem(entry: Entry): MediaItem? {
        val controller = entry.controller
        val state = controller.playbackState ?: return null
        if (state.state == PlaybackState.STATE_NONE ||
            state.state == PlaybackState.STATE_STOPPED ||
            state.state == PlaybackState.STATE_ERROR
        ) {
            return null
        }
        val metadata = controller.metadata ?: return null
        val title = metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_TITLE)
            ?: return null
        val artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: metadata.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L)
        val actions = state.actions
        val packageName = controller.packageName

        return MediaItem(
            key = entry.key,
            packageName = packageName,
            appName = appName(packageName),
            appIcon = appIcon(packageName),
            title = title,
            artist = artist,
            art = art(entry.key, metadata, "$title|$artist|${metadata.text(MediaMetadata.METADATA_KEY_ALBUM)}"),
            isPlaying = state.state in PLAYING_STATES,
            durationMs = duration,
            positionMs = state.position,
            positionUpdateTime = state.lastPositionUpdateTime,
            playbackSpeed = state.playbackSpeed,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L && duration > 0,
            customActions = state.customActions.orEmpty().take(2).map { action ->
                MediaCustomAction(
                    action = action.action,
                    name = action.name?.toString().orEmpty(),
                    icon = packageIcon(packageName, action.icon),
                )
            },
            sessionActivity = controller.sessionActivity,
        )
    }

    private fun MediaMetadata.text(key: String): String? = getString(key)?.takeIf { it.isNotBlank() }

    /** アルバムアート（曲が変わったときだけ読み込み直す） */
    private fun art(key: String, metadata: MediaMetadata, id: String): ImageBitmap? {
        val cached = artCache[key]
        if (cached != null && cached.first == id) return cached.second
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val image = bitmap?.let { scaleDown(it).asImageBitmap() }
        artCache[key] = id to image
        return image
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_ART_SIZE) return bitmap
        val scale = MAX_ART_SIZE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun appName(packageName: String): String = appNames.getOrPut(packageName) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
    }

    private fun appIcon(packageName: String): ImageBitmap? = iconCache.getOrPut("app:$packageName") {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }

    private fun packageIcon(packageName: String, resId: Int): ImageBitmap? = iconCache.getOrPut("$packageName:$resId") {
        runCatching {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            ResourcesCompat.getDrawable(resources, resId, null)?.toBitmap(72, 72)?.asImageBitmap()
        }.getOrNull()
    }

    private companion object {
        const val MAX_ART_SIZE = 512
        val PLAYING_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING,
        )
    }
}
