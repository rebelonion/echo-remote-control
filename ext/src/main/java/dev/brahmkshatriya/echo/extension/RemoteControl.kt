package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ControllerClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.collections.joinToString

class RemoteControl : ExtensionClient, ControllerClient {

    private var websocket: WebSocket? = null
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
    override suspend fun onExtensionSelected() {
        val url = setting.getString("remote_control_server_url") ?: defaultUrl
        val subPath = setting.getString("remote_control_server_sub_path") ?: defaultSubPath
        val port = setting.getString("remote_control_server_port") ?: defaultPort

        val wsUrl = "ws://$url:$port/$subPath"
        val request = okhttp3.Request.Builder().url(wsUrl).build()
        val client = okhttp3.OkHttpClient()
        client.newWebSocket(request, listener)
        log("Connecting to $wsUrl")
    }

    val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            websocket = webSocket
            val connectMessage = Message.AppConnect(
                existingKey = setting.getString("remote_control_server_channel_key")
            )
            sendMessage(connectMessage)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            log("WebSocket closed: $code $reason")
            websocket = null
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            log("WebSocket failed: $t")
            websocket = null
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            log("WebSocket closing: $code $reason")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                log("Received message: $text")
                val message = json.decodeFromString<Message>(text)
                handleMessage(message)
            } catch (e: Exception) {
                log("Failed to parse message: $e")
            }
        }
    }

    private fun handleMessage(message: Message) {
        CoroutineScope(Dispatchers.IO).launch {
            when (message) {
                is Message.AppConnectResponse -> {
                    if (message.success) {
                        log("Connected to server with key: ${message.key}")
                        setting.putString("remote_control_server_channel_key", message.key)
                    } else {
                        log("Failed to connect to server: ${message.error}")
                    }
                }

                is Message.PlaybackCommand -> {
                    when (message.action) {
                        Message.PlaybackCommand.PlaybackAction.PLAY -> onPlayRequest?.invoke()
                        Message.PlaybackCommand.PlaybackAction.PAUSE -> onPauseRequest?.invoke()
                        Message.PlaybackCommand.PlaybackAction.NEXT -> onNextRequest?.invoke()
                        Message.PlaybackCommand.PlaybackAction.PREVIOUS -> onPreviousRequest?.invoke()
                    }
                }

                is Message.SeekCommand -> {
                    onSeekRequest?.invoke(message.position)
                }

                is Message.PlaylistMoveCommand -> {
                    onMovePlaylistItemRequest?.invoke(message.fromIndex, message.toIndex)
                }

                is Message.PlaylistRemoveCommand -> {
                    onRemovePlaylistItemRequest?.invoke(message.index)
                }

                is Message.ShuffleCommand -> {
                    onShuffleModeRequest?.invoke(message.enabled)
                }

                is Message.RepeatCommand -> {
                    onRepeatModeRequest?.invoke(message.mode.ordinal)
                }

                is Message.VolumeCommand -> {
                    onVolumeRequest?.invoke(message.volume)
                }

                else -> {
                    log("Unhandled message type: ${message::class.simpleName}")
                }
            }
        }
    }

    private fun trackToSTrack(track: Track): STrack {
        val imageHolder = (track.cover as? ImageHolder.UrlRequestImageHolder)?.request?.url ?:
        (track.cover as? ImageHolder.UriImageHolder)?.uri?.toString() ?: ""
        return STrack(
            id = track.id,
            title = track.title,
            artist = track.artists.joinToString { it.name + " " },
            album = track.album?.title ?: "Unknown",
            duration = track.duration?.toDouble() ?: 0.0,
            artworkUrl = imageHolder
        )
    }

    private fun sendMessage(message: Message) {
        val messageJson = json.encodeToString(Message.serializer(), message)
        websocket?.send(messageJson)
    }

    override var onPlayRequest: (suspend () -> Unit)? = null
    override var onPauseRequest: (suspend () -> Unit)? = null
    override var onNextRequest: (suspend () -> Unit)? = null
    override var onPreviousRequest: (suspend () -> Unit)? = null
    override var onSeekRequest: (suspend (Double) -> Unit)? = null
    override var onMovePlaylistItemRequest: (suspend (Int, Int) -> Unit)? = null
    override var onRemovePlaylistItemRequest: (suspend (Int) -> Unit)? = null
    override var onShuffleModeRequest: (suspend (Boolean) -> Unit)? = null
    override var onRepeatModeRequest: (suspend (Int) -> Unit)? = null
    override var onVolumeRequest: (suspend (Double) -> Unit)? = null

    override suspend fun onPlaybackStateChanged(
        isPlaying: Boolean,
        position: Double,
        track: Track
    ) {
        val state = if (isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
        val message = Message.PlaybackStateUpdate(
            state = state,
            currentPosition = position,
            track = trackToSTrack(track)
        )
        sendMessage(message)
    }

    override suspend fun onPlaylistChanged(playlist: List<Track>) {
        val message = Message.PlaylistUpdate(
            tracks = playlist.map { trackToSTrack(it) },
            currentIndex = 0  // You might want to pass the current index as a parameter
        )
        sendMessage(message)
    }

    override suspend fun onPlaybackModeChanged(isShuffle: Boolean, repeatState: Int) {
        val repeatMode = when (repeatState) {
            0 -> RepeatMode.OFF
            1 -> RepeatMode.ALL
            2 -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        val message = Message.PlaybackModeUpdate(
            shuffle = isShuffle,
            repeatMode = repeatMode
        )
        sendMessage(message)
    }

    override suspend fun onPositionChanged(position: Double) {
        val message = Message.PositionUpdate(position = position)
        sendMessage(message)
    }

    override suspend fun onVolumeChanged(volume: Double) {
        val message = Message.VolumeUpdate(volume = volume)
        sendMessage(message)
    }

    val defaultUrl = "100.90.151.30"
    val defaultSubPath = "ws"
    val defaultPort = "8080"
    val defaultChannelKey = ""
    override val settingItems: List<Setting>
        get() = listOf(
            SettingCategory(
                "Server Settings",
                "remote_control_server_settings",
                listOf(
                    SettingTextInput(
                        "Server URL",
                        "remote_control_server_url",
                        "The URL of the remote control server",
                        setting.getString("remote_control_server_url") ?: defaultUrl
                    ),
                    SettingTextInput(
                        "Server Sub Path",
                        "remote_control_server_sub_path",
                        "The sub path of the remote control server (optional)",
                        setting.getString("remote_control_server_sub_path") ?: defaultSubPath
                    ),
                    SettingTextInput(
                        "Server Port",
                        "remote_control_server_port",
                        "The port of the remote control server",
                        setting.getString("remote_control_server_port") ?: defaultPort
                    ),
                    SettingTextInput(
                        "Channel Key",
                        "remote_control_server_channel_key",
                        "The channel key of the remote control server (optional)",
                        setting.getString("remote_control_server_channel_key") ?: defaultChannelKey
                    )
                )
            )
        )

    private lateinit var setting: Settings
    override fun setSettings(settings: Settings) {
        setting = settings
    }

    companion object {
        const val PLUGIN_IDENTIFIER = "Echo-RemoteControl"
    }
}