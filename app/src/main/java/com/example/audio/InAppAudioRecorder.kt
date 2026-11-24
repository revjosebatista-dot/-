package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.util.UUID

class InAppAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L

    fun startRecording(bookId: String): File {
        stopSilently()
        val dir = File(context.filesDir, "audio/$bookId").apply { mkdirs() }
        val file = File(dir, "voz_${UUID.randomUUID().toString().take(8)}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mr
        currentFile = file
        startTimeMs = System.currentTimeMillis()
        return file
    }

    fun stopRecording(): Pair<File, Long>? {
        val file = currentFile ?: return null
        val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1000L)
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            currentFile = null
            if (file.exists() && file.length() > 0L) file to duration else null
        } catch (_: Exception) {
            stopSilently()
            null
        }
    }

    fun stopSilently() {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        currentFile = null
    }
}
