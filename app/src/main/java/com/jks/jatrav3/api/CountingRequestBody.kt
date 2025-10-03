package com.jks.jatrav3.api

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException

/**
 * RequestBody that counts bytes written and reports progress via a listener.
 *
 * Use like:
 * val countingBody = CountingRequestBody(file = file, contentType = mime, listener = ...)
 * val part = MultipartBody.Part.createFormData("ar_file", file.name, countingBody)
 */
class CountingRequestBody(
    private val file: File,
    private val contentType: String,
    private val listener: ProgressListener,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : RequestBody() {

    interface ProgressListener {
        /**
         * Called frequently while uploading.
         * bytesWritten: number of bytes written so far
         * contentLength: total bytes to write (may be 0 if unknown)
         */
        fun onProgress(bytesWritten: Long, contentLength: Long)
    }

    override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()

    override fun contentLength(): Long = try {
        file.length()
    } catch (e: Exception) {
        0L
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var uploaded: Long = 0

        // Use okio Source for efficient streaming
        file.source().use { source ->
            val buffer = Buffer()
            while (true) {
                val read = source.read(buffer, chunkSize.toLong())
                if (read == -1L) break
                sink.write(buffer, read)
                uploaded += read
                listener.onProgress(uploaded, total)
            }
        }
    }

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 8 * 1024 // 8 KB
    }
}