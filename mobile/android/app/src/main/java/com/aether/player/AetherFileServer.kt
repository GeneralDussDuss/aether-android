package com.aether.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.BufferedOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal localhost HTTP server that streams audio files and album art
 * from Android MediaStore to the WebView's <audio> element.
 *
 * Endpoints:
 *   GET /audio/{mediaStoreId}  — streams audio file (supports Range requests for seeking)
 *   GET /art/{albumId}?size=N  — serves album art from MediaStore
 *
 * Uses raw Java ServerSocket — no extra dependencies.
 */
class AetherFileServer(private val context: Context) {

    companion object {
        private const val TAG = "AetherFileServer"
        private const val PORT_START = 18765
        private const val PORT_END = 18775
        private const val BUFFER_SIZE = 64 * 1024
    }

    var port: Int = 0
        private set

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val threadPool = Executors.newFixedThreadPool(4)

    fun start(): Int {
        if (running.get()) return port

        // Find available port
        for (p in PORT_START..PORT_END) {
            try {
                serverSocket = ServerSocket(p)
                port = p
                break
            } catch (e: Exception) {
                continue
            }
        }

        if (serverSocket == null) {
            throw RuntimeException("Could not bind to any port in $PORT_START-$PORT_END")
        }

        running.set(true)
        Log.i(TAG, "File server started on port $port")

        Thread({
            while (running.get()) {
                try {
                    val client = serverSocket!!.accept()
                    threadPool.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (running.get()) Log.e(TAG, "Accept error", e)
                }
            }
        }, "AetherFileServer-Accept").start()

        return port
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        threadPool.shutdownNow()
        Log.i(TAG, "File server stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 30_000
            val reader = socket.getInputStream().bufferedReader()
            val requestLine = reader.readLine() ?: return

            // Parse headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
            }

            // Parse: GET /path HTTP/1.1
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendError(socket, 400, "Bad Request")
                return
            }
            val method = parts[0]
            val fullPath = parts[1]

            // Handle CORS preflight
            if (method == "OPTIONS") {
                sendCorsOk(socket)
                return
            }

            // Parse path and query
            val queryIdx = fullPath.indexOf('?')
            val path = if (queryIdx >= 0) fullPath.substring(0, queryIdx) else fullPath
            val queryString = if (queryIdx >= 0) fullPath.substring(queryIdx + 1) else ""
            val queryParams = parseQuery(queryString)

            Log.i(TAG, "Request: $method $path")

            when {
                path.startsWith("/audio/") -> {
                    val idStr = path.removePrefix("/audio/")
                    val mediaId = idStr.toLongOrNull()
                    if (mediaId == null) {
                        Log.e(TAG, "Invalid media ID: $idStr")
                        sendError(socket, 400, "Invalid media ID")
                        return
                    }
                    Log.i(TAG, "Serving audio for mediaId=$mediaId range=${headers["range"]}")
                    serveAudio(socket, mediaId, headers["range"])
                }
                path.startsWith("/art/") -> {
                    val albumIdStr = path.removePrefix("/art/")
                    val albumId = albumIdStr.toLongOrNull()
                    val size = queryParams["size"]?.toIntOrNull() ?: 300
                    if (albumId == null) {
                        sendError(socket, 400, "Invalid album ID")
                        return
                    }
                    serveAlbumArt(socket, albumId, size)
                }
                else -> sendError(socket, 404, "Not Found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client handler error", e)
            try { sendError(socket, 500, "Internal Server Error") } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun serveAudio(socket: Socket, mediaId: Long, rangeHeader: String?) {
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)

        // Get file size
        val fileSize = getFileSize(uri) ?: run {
            sendError(socket, 404, "File not found")
            return
        }

        // Determine content type from file extension
        val mimeType = getMimeType(mediaId)

        val out = BufferedOutputStream(socket.getOutputStream())

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            // Range request — critical for seeking
            val rangeSpec = rangeHeader.removePrefix("bytes=")
            val dashIdx = rangeSpec.indexOf('-')
            val startStr = rangeSpec.substring(0, dashIdx)
            val endStr = rangeSpec.substring(dashIdx + 1)

            val start = if (startStr.isNotEmpty()) startStr.toLong() else 0L
            val end = if (endStr.isNotEmpty()) endStr.toLong() else (fileSize - 1)
            val contentLength = end - start + 1

            val statusLine = "HTTP/1.1 206 Partial Content\r\n"
            val headerStr = statusLine +
                "Content-Type: $mimeType\r\n" +
                "Content-Length: $contentLength\r\n" +
                "Content-Range: bytes $start-$end/$fileSize\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Range\r\n" +
                "Access-Control-Expose-Headers: Content-Range, Content-Length\r\n" +
                "Connection: close\r\n\r\n"

            out.write(headerStr.toByteArray())
            streamBytes(uri, out, start, contentLength)
        } else {
            // Full response
            val headerStr = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $mimeType\r\n" +
                "Content-Length: $fileSize\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Range\r\n" +
                "Access-Control-Expose-Headers: Content-Range, Content-Length\r\n" +
                "Connection: close\r\n\r\n"

            out.write(headerStr.toByteArray())
            streamBytes(uri, out, 0, fileSize)
        }
        out.flush()
    }

    private fun serveAlbumArt(socket: Socket, albumId: Long, size: Int) {
        val artUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )

        try {
            val inputStream = context.contentResolver.openInputStream(artUri)
            if (inputStream == null) {
                sendError(socket, 404, "No album art")
                return
            }

            inputStream.use { stream ->
                val bytes = stream.readBytes()
                val out = BufferedOutputStream(socket.getOutputStream())
                val headerStr = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Cache-Control: max-age=86400\r\n" +
                    "Connection: close\r\n\r\n"
                out.write(headerStr.toByteArray())
                out.write(bytes)
                out.flush()
            }
        } catch (e: Exception) {
            // No album art available — send 1x1 transparent PNG
            sendError(socket, 404, "No album art")
        }
    }

    private fun streamBytes(uri: Uri, out: BufferedOutputStream, start: Long, length: Long) {
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return
            // Skip to start position
            var skipped = 0L
            while (skipped < start) {
                val s = inputStream.skip(start - skipped)
                if (s <= 0) break
                skipped += s
            }

            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = length
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = inputStream.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        } catch (e: Exception) {
            // Client probably disconnected (seeking) — this is normal
            Log.d(TAG, "Stream interrupted: ${e.message}")
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    private fun getFileSize(uri: Uri): Long? {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getMimeType(mediaId: Long): String {
        val projection = arrayOf(MediaStore.Audio.Media.MIME_TYPE)
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0) ?: "audio/flac"
            }
        }
        return "audio/flac"
    }

    private fun sendError(socket: Socket, code: Int, message: String) {
        try {
            val body = """{"error":"$message"}"""
            val out = BufferedOutputStream(socket.getOutputStream())
            val headerStr = "HTTP/1.1 $code $message\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
            out.write(headerStr.toByteArray())
            out.write(body.toByteArray())
            out.flush()
        } catch (_: Exception) {}
    }

    private fun sendCorsOk(socket: Socket) {
        try {
            val out = BufferedOutputStream(socket.getOutputStream())
            val headerStr = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Range\r\n" +
                "Access-Control-Max-Age: 86400\r\n" +
                "Connection: close\r\n\r\n"
            out.write(headerStr.toByteArray())
            out.flush()
        } catch (_: Exception) {}
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split("&").mapNotNull { param ->
            val eq = param.indexOf('=')
            if (eq > 0) param.substring(0, eq) to param.substring(eq + 1)
            else null
        }.toMap()
    }
}
