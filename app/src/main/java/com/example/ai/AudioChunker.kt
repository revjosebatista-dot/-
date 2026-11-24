package com.example.ai

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID

data class PreparedAudioFile(
    val localFile: File,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long
)

data class AudioChunk(
    val index: Int,
    val totalChunks: Int,
    val bytes: ByteArray,
    val mimeType: String
)

object AudioChunker {
    // Keep inline Gemini payloads well under the 20MB limit
    private const val MAX_INLINE_BYTES = 12 * 1024 * 1024L
    private const val CHUNK_DURATION_US = 8 * 60 * 1_000_000L // 8 minutes per chunk

    suspend fun copyUriToPrivateStorage(
        context: Context,
        bookId: String,
        uri: Uri
    ): PreparedAudioFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawName = queryFileName(context, uri) ?: "gravacao_${System.currentTimeMillis()}.m4a"
        val mimeType = normalizeMimeType(resolver.getType(uri), rawName)

        val dir = File(context.filesDir, "audio/$bookId").apply { mkdirs() }
        val safeName = "${UUID.randomUUID().toString().take(8)}_${rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
        val targetFile = File(dir, safeName)

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: error("Não foi possível ler o arquivo de áudio selecionado.")

        val durationMs = extractDurationMs(targetFile)
        PreparedAudioFile(
            localFile = targetFile,
            fileName = rawName,
            mimeType = mimeType,
            sizeBytes = targetFile.length(),
            durationMs = durationMs
        )
    }

    fun extractDurationMs(file: File): Long {
        if (!file.exists()) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun normalizeMimeType(detected: String?, fileName: String): String {
        val lower = fileName.lowercase()
        return when {
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".wav") -> "audio/wav"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".aac") -> "audio/aac"
            lower.endsWith(".ogg") || lower.endsWith(".oga") -> "audio/ogg"
            lower.endsWith(".webm") -> "audio/webm"
            !detected.isNullOrBlank() && detected.startsWith("audio/") -> detected
            else -> "audio/mp4"
        }
    }

    suspend fun splitIntoChunks(
        context: Context,
        file: File,
        mimeType: String
    ): List<AudioChunk> = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() > 0L) {
            "Arquivo de áudio local não encontrado para processamento."
        }

        if (file.length() <= MAX_INLINE_BYTES) {
            return@withContext listOf(
                AudioChunk(
                    index = 0,
                    totalChunks = 1,
                    bytes = file.readBytes(),
                    mimeType = mimeType
                )
            )
        }

        // Try WAV frame chunking when WAV
        if (mimeType == "audio/wav" || file.name.lowercase().endsWith(".wav")) {
            val wavChunks = runCatching { splitWavFile(file) }.getOrNull()
            if (!wavChunks.isNullOrEmpty()) return@withContext wavChunks
        }

        // Try MediaExtractor + MediaMuxer for M4A / MP4 / AAC containers
        if (mimeType == "audio/mp4" || mimeType == "audio/aac" || file.name.lowercase().endsWith(".m4a")) {
            val muxedChunks = runCatching { splitWithMediaMuxer(context, file) }.getOrNull()
            if (!muxedChunks.isNullOrEmpty()) return@withContext muxedChunks
        }

        // Generic stream byte window fallback for MP3/OGG/WEBM > 12MB
        val totalSize = file.length()
        val chunkSize = MAX_INLINE_BYTES.toInt()
        val totalChunks = ((totalSize + chunkSize - 1) / chunkSize).toInt().coerceAtLeast(1)
        val result = ArrayList<AudioChunk>(totalChunks)
        file.inputStream().use { input ->
            val buffer = ByteArray(chunkSize)
            var idx = 0
            while (true) {
                var offset = 0
                while (offset < chunkSize) {
                    val read = input.read(buffer, offset, chunkSize - offset)
                    if (read <= 0) break
                    offset += read
                }
                if (offset <= 0) break
                result.add(
                    AudioChunk(
                        index = idx,
                        totalChunks = totalChunks,
                        bytes = buffer.copyOf(offset),
                        mimeType = mimeType
                    )
                )
                idx++
            }
        }
        result
    }

    private fun splitWavFile(file: File): List<AudioChunk> {
        val allBytes = file.readBytes()
        if (allBytes.size <= 44) return emptyList()
        val header = allBytes.copyOfRange(0, 44)
        val pcmDataSize = allBytes.size - 44
        val maxPcmPerChunk = (MAX_INLINE_BYTES - 44).toInt()
        val alignedChunk = (maxPcmPerChunk / 4) * 4
        val totalChunks = ((pcmDataSize + alignedChunk - 1) / alignedChunk).coerceAtLeast(1)
        val chunks = mutableListOf<AudioChunk>()

        var offset = 44
        var idx = 0
        while (offset < allBytes.size) {
            val end = minOf(offset + alignedChunk, allBytes.size)
            val dataLen = end - offset
            val chunkHeader = header.copyOf()
            writeIntLittleEndian(chunkHeader, 4, 36 + dataLen)
            writeIntLittleEndian(chunkHeader, 40, dataLen)
            val out = ByteArrayOutputStream(44 + dataLen)
            out.write(chunkHeader)
            out.write(allBytes, offset, dataLen)
            chunks.add(
                AudioChunk(
                    index = idx,
                    totalChunks = totalChunks,
                    bytes = out.toByteArray(),
                    mimeType = "audio/wav"
                )
            )
            offset = end
            idx++
        }
        return chunks
    }

    private fun writeIntLittleEndian(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun splitWithMediaMuxer(context: Context, file: File): List<AudioChunk> {
        val extractor = MediaExtractor()
        extractor.setDataSource(file.absolutePath)
        var audioTrackIdx = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIdx = i
                format = f
                break
            }
        }
        if (audioTrackIdx < 0 || format == null) {
            extractor.release()
            return emptyList()
        }
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION)
        } else 0L
        if (durationUs <= 0L) {
            extractor.release()
            return emptyList()
        }

        extractor.selectTrack(audioTrackIdx)
        val totalChunks = ((durationUs + CHUNK_DURATION_US - 1) / CHUNK_DURATION_US).toInt().coerceAtLeast(1)
        val tempFiles = mutableListOf<File>()
        val buffer = ByteBuffer.allocate(256 * 1024)
        val info = MediaCodec.BufferInfo()

        try {
            for (chunkIdx in 0 until totalChunks) {
                val startUs = chunkIdx * CHUNK_DURATION_US
                val endUs = minOf((chunkIdx + 1) * CHUNK_DURATION_US, durationUs)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val outFile = File(context.cacheDir, "chunk_${UUID.randomUUID()}.m4a")
                tempFiles.add(outFile)
                val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxTrack = muxer.addTrack(format)
                muxer.start()
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    val sampleTime = extractor.sampleTime
                    if (sampleTime >= endUs) break
                    info.offset = 0
                    info.size = sampleSize
                    info.presentationTimeUs = (sampleTime - startUs).coerceAtLeast(0L)
                    info.flags = extractor.sampleFlags
                    muxer.writeSampleData(muxTrack, buffer, info)
                    extractor.advance()
                }
                muxer.stop()
                muxer.release()
            }

            return tempFiles.mapIndexed { idx, temp ->
                val bytes = temp.readBytes()
                temp.delete()
                AudioChunk(
                    index = idx,
                    totalChunks = tempFiles.size,
                    bytes = bytes,
                    mimeType = "audio/mp4"
                )
            }
        } finally {
            extractor.release()
            tempFiles.forEach { if (it.exists()) it.delete() }
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
        }
    }
}
