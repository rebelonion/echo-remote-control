package dev.brahmkshatriya.echo.extension

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Basic track information model
@Serializable
data class STrack(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("artist")
    val artist: String,
    @SerialName("album")
    val album: String,
    @SerialName("duration")
    val duration: Double, // in milliseconds
    @SerialName("artworkUrl")
    val artworkUrl: String? = null
)

// Playback state enums
@Serializable
enum class PlaybackState {
    PLAYING,
    PAUSED
}

@Serializable
enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

@Serializable
sealed interface Message {
    @Serializable
    @SerialName("AppConnect")
    data class AppConnect(
        @SerialName("existingKey")
        val existingKey: String? = null
    ) : Message

    @Serializable
    @SerialName("AppConnectResponse")
    data class AppConnectResponse(
        @SerialName("key")
        val key: String,
        @SerialName("success")
        val success: Boolean,
        @SerialName("error")
        val error: String? = null
    ) : Message

    @Serializable
    @SerialName("ControllerConnect")
    data class ControllerConnect(
        @SerialName("key")
        val key: String
    ) : Message

    // App -> Controller: State Updates
    @Serializable
    @SerialName("PlaybackStateUpdate")
    data class PlaybackStateUpdate(
        @SerialName("state")
        val state: PlaybackState,
        @SerialName("currentPosition")
        val currentPosition: Double,  // in milliseconds
        @SerialName("track")
        val track: STrack
    ) : Message

    @Serializable
    @SerialName("PlaylistUpdate")
    data class PlaylistUpdate(
        @SerialName("tracks")
        val tracks: List<STrack>,
        @SerialName("currentIndex")
        val currentIndex: Int
    ) : Message

    @Serializable
    @SerialName("PlaybackModeUpdate")
    data class PlaybackModeUpdate(
        @SerialName("shuffle")
        val shuffle: Boolean,
        @SerialName("repeatMode")
        val repeatMode: RepeatMode
    ) : Message

    @Serializable
    @SerialName("PositionUpdate")
    data class PositionUpdate(
        @SerialName("position")
        val position: Double  // in milliseconds
    ) : Message

    @Serializable
    @SerialName("VolumeUpdate")
    data class VolumeUpdate(
        @SerialName("volume")
        val volume: Double
    ) : Message

    // Controller -> App: Playback Commands
    @Serializable
    @SerialName("PlaybackCommand")
    data class PlaybackCommand(
        @SerialName("action")
        val action: PlaybackAction
    ) : Message {
        @Serializable
        enum class PlaybackAction {
            PLAY,
            PAUSE,
            NEXT,
            PREVIOUS
        }
    }

    @Serializable
    @SerialName("SeekCommand")
    data class SeekCommand(
        @SerialName("position")
        val position: Double  // in milliseconds
    ) : Message

    // Controller -> App: Playlist Commands
    @Serializable
    @SerialName("PlaylistMoveCommand")
    data class PlaylistMoveCommand(
        @SerialName("fromIndex")
        val fromIndex: Int,
        @SerialName("toIndex")
        val toIndex: Int
    ) : Message

    @Serializable
    @SerialName("PlaylistRemoveCommand")
    data class PlaylistRemoveCommand(
        @SerialName("index")
        val index: Int
    ) : Message

    // Controller -> App: Playback Mode Commands
    @Serializable
    @SerialName("ShuffleCommand")
    data class ShuffleCommand(
        @SerialName("enabled")
        val enabled: Boolean
    ) : Message

    @Serializable
    @SerialName("RepeatCommand")
    data class RepeatCommand(
        @SerialName("mode")
        val mode: RepeatMode
    ) : Message

    // Controller -> App: Volume Command
    @Serializable
    @SerialName("VolumeCommand")
    data class VolumeCommand(
        @SerialName("volume")
        val volume: Double
    ) : Message

    // Controller -> App: Request States
    @Serializable
    @SerialName("RequestCurrentState")
    object RequestCurrentState : Message

    // Error messages
    @Serializable
    @SerialName("ErrorMessage")
    data class ErrorMessage(
        @SerialName("code")
        val code: ErrorCode,
        @SerialName("message")
        val message: String
    ) : Message {
        @Serializable
        enum class ErrorCode {
            INVALID_COMMAND,
            INVALID_STATE,
            INVALID_TRACK,
            INVALID_INDEX,
            INVALID_POSITION,
            SERVER_ERROR
        }
    }
}

// Current state model (useful for maintaining server-side state)
@Serializable
data class PlayerState(
    val playbackState: PlaybackState = PlaybackState.PAUSED,
    val currentTrack: STrack? = null,
    val currentPosition: Double = 0.0,
    val playlist: List<STrack> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volume: Double = 1.0
)