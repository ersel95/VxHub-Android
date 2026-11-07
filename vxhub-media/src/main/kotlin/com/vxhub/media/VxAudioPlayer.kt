package com.vxhub.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** Playback options — iOS's `VxAudioConfiguration`. */
public class VxAudioConfiguration(
    /** Lower other apps' audio while playing instead of pausing them. */
    public val duckOthers: Boolean = true,
    /** Play alongside other apps' audio without asking for audio focus. */
    public val allowMixing: Boolean = false,
    public val playbackRate: Float = 1f,
    public val volume: Float = 1f,
    /** Extra repetitions after the first play; -1 loops forever. */
    public val numberOfLoops: Int = 0,
)

/**
 * Plays sounds by id and records audio — the Android port of iOS's `VxMP3Manager`. Every method
 * runs on the main thread (Media3's rule); callbacks arrive there too.
 *
 * Recording needs the app to hold `RECORD_AUDIO`.
 */
public class VxAudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val players = mutableMapOf<String, ExoPlayer>()
    private val progressTicks = mutableMapOf<String, Runnable>()
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    public fun play(
        audioId: String,
        uri: Uri,
        configuration: VxAudioConfiguration = VxAudioConfiguration(),
        onProgress: ((Float) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        if (recorder != null) stopRecording(save = false)
        stop(audioId)
        var loopsLeft = configuration.numberOfLoops
        val player = ExoPlayer.Builder(appContext).build().apply {
            val usage = if (configuration.duckOthers) C.USAGE_ASSISTANCE_SONIFICATION else C.USAGE_MEDIA
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(usage).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                !configuration.allowMixing,
            )
            volume = configuration.volume.coerceIn(0f, 1f)
            playbackParameters = PlaybackParameters(configuration.playbackRate)
            repeatMode = if (loopsLeft < 0) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(uri))
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_ENDED) return
                        if (loopsLeft > 0) {
                            loopsLeft--
                            seekTo(0)
                            play()
                        } else {
                            onProgress?.invoke(1f)
                            stop(audioId)
                            onComplete?.invoke()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        stop(audioId)
                        onError?.invoke(error)
                    }
                },
            )
            prepare()
            play()
        }
        players[audioId] = player
        if (onProgress != null) startProgress(audioId, player, onProgress)
    }

    public fun pause(audioId: String) {
        players[audioId]?.pause()
    }

    public fun resume(audioId: String) {
        players[audioId]?.play()
    }

    public fun stop(audioId: String) {
        progressTicks.remove(audioId)?.let(main::removeCallbacks)
        players.remove(audioId)?.release()
    }

    public fun stopAll() {
        players.keys.toList().forEach(::stop)
    }

    public fun setVolume(audioId: String, volume: Float) {
        players[audioId]?.volume = volume.coerceIn(0f, 1f)
    }

    public fun setPlaybackRate(audioId: String, rate: Float) {
        players[audioId]?.playbackParameters = PlaybackParameters(rate)
    }

    /** Position in milliseconds, or null when [audioId] is not playing. */
    public fun currentPositionMs(audioId: String): Long? = players[audioId]?.currentPosition

    public fun durationMs(audioId: String): Long? = players[audioId]?.duration?.takeIf { it != C.TIME_UNSET }

    public fun isPlaying(audioId: String): Boolean = players[audioId]?.isPlaying == true

    // region Recording

    /** Starts recording AAC audio into `recordings/<fileName>.m4a`. Stops any playback first. */
    public fun startRecording(fileName: String, onStart: (() -> Unit)? = null, onError: ((Throwable) -> Unit)? = null) {
        players.values.forEach { it.pause() }
        stopRecording(save = false)
        val file = File(recordingsDir(), "$fileName.m4a")
        try {
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(128_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            recordingFile = file
            onStart?.invoke()
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            file.delete()
            onError?.invoke(e)
        }
    }

    /** Stops recording. Returns the file when [save] is true and the recording succeeded. */
    public fun stopRecording(save: Boolean = false): File? {
        val active = recorder ?: return null
        val file = recordingFile
        recorder = null
        recordingFile = null
        val ok = runCatching { active.stop() }.isSuccess
        active.release()
        if (!save || !ok) {
            file?.delete()
            return null
        }
        return file
    }

    public fun savedRecordings(): List<String> =
        recordingsDir().listFiles()?.filter { it.extension == "m4a" }?.map { it.nameWithoutExtension }?.sorted().orEmpty()

    public fun recordingFile(recordingId: String): File? = File(recordingsDir(), "$recordingId.m4a").takeIf { it.exists() }

    // endregion

    /** Releases every player and the recorder. */
    public fun dispose() {
        stopAll()
        stopRecording(save = false)
    }

    private fun startProgress(audioId: String, player: ExoPlayer, onProgress: (Float) -> Unit) {
        val tick = object : Runnable {
            override fun run() {
                val duration = player.duration
                if (duration > 0 && duration != C.TIME_UNSET) onProgress((player.currentPosition.toFloat() / duration).coerceIn(0f, 1f))
                main.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }
        progressTicks[audioId] = tick
        main.post(tick)
    }

    private fun recordingsDir(): File = File(appContext.noBackupFilesDir, "vxhub/recordings").apply { mkdirs() }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 100L
    }
}
