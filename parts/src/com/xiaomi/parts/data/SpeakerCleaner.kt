package com.xiaomi.parts.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import com.xiaomi.parts.R

class SpeakerCleaner(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var mediaPlayer: MediaPlayer? = null

    fun start(): Boolean {
        stop()
        runCatching { audioManager?.setParameters("status_earpiece_clean=on") }
        return runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f)
                context.resources.openRawResourceFd(R.raw.clear_speaker_sound).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                prepare()
                start()
            }
            mediaPlayer = player
            true
        }.getOrElse {
            stop()
            false
        }
    }

    fun stop() {
        runCatching {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        }
        mediaPlayer = null
        runCatching { audioManager?.setParameters("status_earpiece_clean=off") }
    }
}
