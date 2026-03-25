package com.aether.player

import android.content.ContentUris
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.getcapacitor.Bridge
import com.getcapacitor.BridgeWebViewClient
import java.io.FileInputStream
import java.io.InputStream

/**
 * Custom WebViewClient that serves audio files at same-origin paths:
 *   http://localhost/_audio/{mediaStoreId}
 *
 * Because the path is on localhost (same origin as the WebView),
 * MediaElementAudioSource (Web Audio API) can read the samples without
 * CORS restrictions — fixing the "outputs zeroes" silent playback bug.
 *
 * Supports HTTP Range requests for seeking.
 * All other requests delegate to Capacitor's BridgeWebViewClient.
 */
class AetherWebViewClient(private val bridge: Bridge) : BridgeWebViewClient(bridge) {

    companion object {
        private const val TAG = "AetherWebViewClient"
        private const val AUDIO_PATH_PREFIX = "/_audio/"
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val path = request.url.path ?: return super.shouldInterceptRequest(view, request)

        if (path.startsWith(AUDIO_PATH_PREFIX)) {
            val idStr = path.removePrefix(AUDIO_PATH_PREFIX)
            val mediaId = idStr.toLongOrNull()
            if (mediaId == null) {
                Log.e(TAG, "Invalid media ID: $idStr")
                return errorResponse(400, "Invalid media ID")
            }
            return serveAudio(mediaId, request)
        }

        // Delegate everything else to Capacitor
        return super.shouldInterceptRequest(view, request)
    }

    private fun serveAudio(mediaId: Long, request: WebResourceRequest): WebResourceResponse {
        val context = bridge.context
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)

        // Open a ParcelFileDescriptor — we hold this reference to prevent GC
        val pfd: ParcelFileDescriptor = try {
            context.contentResolver.openFileDescriptor(uri, "r")
                ?: return errorResponse(404, "File not found")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open file for $mediaId", e)
            return errorResponse(404, "File not found")
        }

        val fileSize = pfd.statSize

        if (fileSize <= 0) {
            pfd.close()
            return errorResponse(404, "File not found or empty")
        }

        // Get MIME type
        val mimeType = getMimeType(mediaId)

        // Check for Range header
        val rangeHeader = request.requestHeaders?.get("Range")
            ?: request.requestHeaders?.get("range")

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return serveRange(pfd, mediaId, fileSize, mimeType, rangeHeader)
        }

        // Full response — PfdInputStream keeps pfd alive and closes it on stream close
        val inputStream = PfdInputStream(pfd, 0, fileSize)

        Log.i(TAG, "Serving full audio mediaId=$mediaId size=$fileSize mime=$mimeType")

        val headers = mutableMapOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to fileSize.toString(),
            "Cache-Control" to "no-cache"
        )

        return WebResourceResponse(mimeType, null, 200, "OK", headers, inputStream)
    }

    private fun serveRange(
        pfd: ParcelFileDescriptor, mediaId: Long, fileSize: Long,
        mimeType: String, rangeHeader: String
    ): WebResourceResponse {
        val rangeSpec = rangeHeader.removePrefix("bytes=")
        val dashIdx = rangeSpec.indexOf('-')
        val startStr = rangeSpec.substring(0, dashIdx)
        val endStr = rangeSpec.substring(dashIdx + 1)

        val start = if (startStr.isNotEmpty()) startStr.toLong() else 0L
        val end = if (endStr.isNotEmpty()) endStr.toLong() else (fileSize - 1)
        val contentLength = end - start + 1

        // PfdInputStream seeks via FileChannel — no skip loop needed
        val inputStream = PfdInputStream(pfd, start, contentLength)

        Log.i(TAG, "Serving range mediaId=$mediaId bytes=$start-$end/$fileSize")

        val headers = mutableMapOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to contentLength.toString(),
            "Content-Range" to "bytes $start-$end/$fileSize",
            "Cache-Control" to "no-cache"
        )

        return WebResourceResponse(mimeType, null, 206, "Partial Content", headers, inputStream)
    }

    private fun getMimeType(mediaId: Long): String {
        val context = bridge.context
        val projection = arrayOf(MediaStore.Audio.Media.MIME_TYPE)
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0) ?: "audio/flac"
            }
        }
        return "audio/flac"
    }

    private fun errorResponse(code: Int, message: String): WebResourceResponse {
        val body = """{"error":"$message"}""".byteInputStream()
        return WebResourceResponse(
            "application/json", "utf-8", code, message,
            mutableMapOf("Cache-Control" to "no-cache"), body
        )
    }

    /**
     * InputStream backed by a ParcelFileDescriptor + FileChannel.
     * - Holds a strong reference to the PFD so GC can't close the fd mid-read
     * - Uses FileChannel.position() for proper seeking (no skip loops)
     * - Limits reads to [remaining] bytes for Range responses
     * - Closes the PFD when the stream is closed
     */
    private class PfdInputStream(
        private val pfd: ParcelFileDescriptor,
        startOffset: Long,
        private var remaining: Long
    ) : InputStream() {

        private val fis = FileInputStream(pfd.fileDescriptor)
        private val channel = fis.channel

        init {
            if (startOffset > 0) {
                channel.position(startOffset)
            }
        }

        override fun read(): Int {
            if (remaining <= 0) return -1
            val b = fis.read()
            if (b >= 0) remaining--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = fis.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }

        override fun available(): Int {
            return minOf(remaining, fis.available().toLong()).toInt()
        }

        override fun close() {
            try { fis.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
    }
}
