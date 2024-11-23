package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.CloseableClient
import dev.brahmkshatriya.echo.common.clients.ControllerClient
import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.MessagePostClient
import dev.brahmkshatriya.echo.common.clients.SettingsChangeListenerClient
import dev.brahmkshatriya.echo.common.models.ImageHolder
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingCategory
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.joinToString

class RemoteControl : CloseableClient, ExtensionClient, SettingsChangeListenerClient,
    MessagePostClient, ControllerClient() {
    override var runsDuringPause: Boolean = true

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val connecting: AtomicBoolean = AtomicBoolean(false)

    private var websocket: WebSocket? = null
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    private var messageHandler: ((String) -> Unit)? = null
    override fun setMessageHandler(handler: (String) -> Unit) {
        messageHandler = handler
    }

    private var lastMessageTime = 0L
    private val minMessageInterval = 5000L
    private fun postMessage(message: String, prioritized: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        if (prioritized || currentTime - lastMessageTime > minMessageInterval) {
            messageHandler?.invoke(message)
            lastMessageTime = currentTime
        }
    }
    override fun postMessage(message: String) {
        postMessage(message, prioritized = false)
    }

    private var stopped = false
    override fun close() {
        stopped = true
        log("Closing extension")
        scope.cancel()
        websocket?.let {
            it.close(1000, "Extension closed")
            websocket = null
        }
        connecting.set(false)
    }

    override suspend fun onExtensionSelected() {
        connect(reconnect = false)
    }

    override suspend fun onSettingsChanged(settings: Settings, key: String?) {
        connect(reconnect = true)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            websocket = webSocket
            connecting.set(false)
            val connectMessage = Message.AppConnect(
                existingKey = setting.getString("remote_control_server_channel_key")
            )
            sendMessage(connectMessage)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            log("WebSocket closed: $code $reason")
            postMessage("Disconnected from server")
            websocket = null
            connecting.set(false)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            log("WebSocket failed: $t")
            postMessage("Failed to connect to server")
            websocket = null
            connecting.set(false)
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

    private fun connect(reconnect: Boolean) {
        if (connecting.getAndSet(true)) {
            return
        }
        if ((websocket != null && !reconnect) || stopped) {
            return
        }
        websocket?.close(1000, "Reconnecting")
        val url = setting.getString("remote_control_server_url") ?: defaultUrl
        val subPath = setting.getString("remote_control_server_sub_path") ?: defaultSubPath
        val port = setting.getString("remote_control_server_port") ?: defaultPort
        val useWss = setting.getBoolean("remote_control_server_secure") ?: defaultUseWss
        val shouldShowPort = when {
            useWss && port != "443" -> true
            !useWss && port != "80" -> true
            else -> false
        }
        val wsUrl = buildString {
            append("ws")
            if (useWss) append("s")
            append("://${url.trim()}")
            if (shouldShowPort && port.isNotEmpty()) append(":${port.trim()}")
            if (subPath.isNotEmpty()) append("/${subPath.trimStart('/')}")
        }
        val request = okhttp3.Request.Builder().url(wsUrl).build()
        val client = okhttp3.OkHttpClient()
        client.newWebSocket(request, listener)
        log("connecting to $wsUrl")
    }

    private fun handleMessage(message: Message) {
        scope.launch(Dispatchers.IO) {
            when (message) {
                is Message.AppConnectResponse -> {
                    if (message.success) {
                        log("Connected to server with key: ${message.key}")
                        if (message.key != setting.getString("remote_control_server_channel_key")) {
                            log("Saving new key")
                            setting.putString("remote_control_server_channel_key", message.key)
                            postMessage("Received new key: ${message.key}", prioritized = true)
                        }
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
                    onRepeatModeRequest?.invoke(message.mode)
                }

                is Message.VolumeCommand -> {
                    onVolumeRequest?.invoke(message.volume)
                }

                is Message.RequestCurrentState -> {
                    val state = onRequestState?.invoke()
                    if (state != null) {
                        val playerState = Message.PlayerState(
                            isPlaying = state.isPlaying,
                            currentTrack = trackToSTrack(state.currentTrack),
                            currentPosition = state.currentPosition,
                            playlist = state.playlist.map { trackToSTrack(it) },
                            currentIndex = state.currentIndex,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            volume = state.volume
                        )
                        sendMessage(playerState)
                    }
                }

                else -> {
                    log("Unhandled message type: ${message::class.simpleName}")
                }
            }
        }
    }

    private fun trackToSTrack(track: Track?): STrack {
        if (track == null) {
            return STrack(
                id = "",
                title = "",
                artist = "",
                album = "",
                duration = 0.0,
                artworkUrl = ""
            )
        }
        val imageHolder = (track.cover as? ImageHolder.UrlRequestImageHolder)?.request?.url
            ?: (track.cover as? ImageHolder.UriImageHolder)?.uri ?: ""
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
        if (stopped) return
        if (websocket == null) {
            connect(reconnect = true)
            log("WebSocket not connected, reconnecting")
        } else {
            val messageJson = json.encodeToString(Message.serializer(), message)
            websocket?.send(messageJson)
        }
    }

    override suspend fun onPlaybackStateChanged(
        isPlaying: Boolean,
        position: Long,
        track: Track?
    ) {
        val message = Message.PlaybackStateUpdate(
            isPlaying = isPlaying,
            currentPosition = position,
            track = trackToSTrack(track)
        )
        sendMessage(message)
    }

    override suspend fun onPlaylistChanged(playlist: List<Track>) {
        val message = Message.PlaylistUpdate(
            tracks = playlist.map { trackToSTrack(it) },
            currentIndex = 0
        )
        sendMessage(message)
    }

    override suspend fun onPlaybackModeChanged(isShuffle: Boolean, repeatMode: RepeatMode) {
        val message = Message.PlaybackModeUpdate(
            shuffle = isShuffle,
            repeatMode = repeatMode
        )
        sendMessage(message)
    }

    override suspend fun onPositionChanged(position: Long) {
        val message = Message.PositionUpdate(position = position)
        sendMessage(message)
    }

    override suspend fun onVolumeChanged(volume: Double) {
        val message = Message.VolumeUpdate(volume = volume)
        sendMessage(message)
    }

    private val defaultUrl = "ws.rebelonion.dev"
    private val defaultSubPath = "ws"
    private val defaultPort = "443"
    private val defaultUseWss = false
    private val defaultChannelKey = ""
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
                        defaultUrl
                    ),
                    SettingTextInput(
                        "Server Sub Path",
                        "remote_control_server_sub_path",
                        "The sub path of the remote control server (optional)",
                        defaultSubPath
                    ),
                    SettingTextInput(
                        "Server Port",
                        "remote_control_server_port",
                        "The port of the remote control server",
                        defaultPort
                    ),
                    SettingSwitch(
                        "Use secure connection",
                        "remote_control_server_secure",
                        "Use a secure connection (wss)",
                        defaultUseWss
                    ),
                    SettingTextInput(
                        "Channel Key",
                        "remote_control_server_channel_key",
                        "The channel key of the remote control server (optional)",
                        defaultChannelKey
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