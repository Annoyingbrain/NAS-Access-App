package com.kmarko.nasdrive

import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal loopback-only HTTP server that proxies a single SMB file as a
 * range-seekable HTTP resource. This lets a normal ACTION_VIEW target (video
 * player, image viewer, PDF reader, ...) play/scrub NAS content directly via
 * SmbRandomAccessFile.seek(), instead of requiring the whole file downloaded
 * first. Only ever bound to 127.0.0.1, never reachable off-device.
 *
 * Caveat worth remembering if this needs revisiting: apps that route HTTP
 * through Android's platform network stack (java.net/OkHttp) are subject to
 * the OS's cleartext-traffic policy, which is host-based and has no built-in
 * loopback exemption - some ACTION_VIEW targets may refuse a plain http://
 * URL even to 127.0.0.1. Players with their own native network stack (e.g.
 * VLC/libVLC) aren't subject to that policy and are unaffected.
 */
class NasStreamServer(private val repository: SmbRepository) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)

    val port: Int
        get() = serverSocket?.localPort ?: -1

    fun start() {
        if (running.getAndSet(true)) return
        // Bind explicitly to the IPv4 loopback literal - urlFor() hands out "127.0.0.1"
        // URLs, and InetAddress.getLoopbackAddress() can resolve to the IPv6 "::1"
        // loopback instead, which is a different socket and refuses the IPv4 connection.
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        Thread {
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (e: Exception) {
                    if (running.get()) continue else break
                }
                executor.execute { handleClient(client) }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        executor.shutdownNow()
    }

    fun urlFor(remotePath: String): String {
        val encoded = URLEncoder.encode(remotePath, "UTF-8")
        return "http://127.0.0.1:$port/stream?path=$encoded"
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            try {
                val input = sock.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                val requestLine = input.readLine() ?: return
                var rangeHeader: String? = null
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                }
                val path = parsePath(requestLine)
                if (path == null) {
                    writeStatus(sock.getOutputStream(), 400, "Bad Request")
                    return
                }
                serveFile(sock, path, rangeHeader)
            } catch (e: Exception) {
                // Client closed/seeked away mid-stream, or the SMB read failed -
                // nothing to clean up beyond the socket, which .use{} already closes.
            }
        }
    }

    private fun parsePath(requestLine: String): String? {
        // e.g. "GET /stream?path=Media%2FMovies%2Ffoo.mkv HTTP/1.1"
        val target = requestLine.split(" ").getOrNull(1) ?: return null
        val query = target.substringAfter("?path=", "")
        if (query.isBlank()) return null
        return URLDecoder.decode(query, "UTF-8")
    }

    private fun serveFile(sock: Socket, remotePath: String, rangeHeader: String?) {
        val raf = repository.openRandomAccess(remotePath)
        try {
            val out = BufferedOutputStream(sock.getOutputStream())
            val length = raf.length()
            var start = 0L
            var end = length - 1

            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                val spec = rangeHeader.removePrefix("bytes=").split("-")
                spec.getOrNull(0)?.takeIf { it.isNotBlank() }?.let { start = it.toLong() }
                spec.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { end = it.toLong() }
                if (end >= length) end = length - 1
                writeStatus(out, 206, "Partial Content")
                writeHeader(out, "Content-Range", "bytes $start-$end/$length")
            } else {
                writeStatus(out, 200, "OK")
            }
            writeHeader(out, "Accept-Ranges", "bytes")
            writeHeader(out, "Content-Type", guessMimeType(remotePath))
            writeHeader(out, "Content-Length", "${end - start + 1}")
            writeHeader(out, "Connection", "close")
            out.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.flush()

            raf.seek(start)
            val buffer = ByteArray(256 * 1024)
            var remaining = end - start + 1
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            out.flush()
        } finally {
            raf.close()
        }
    }

    private fun writeStatus(out: OutputStream, code: Int, message: String) {
        out.write("HTTP/1.1 $code $message\r\n".toByteArray(StandardCharsets.ISO_8859_1))
        if (code != 200 && code != 206) out.flush()
    }

    private fun writeHeader(out: OutputStream, key: String, value: String) {
        out.write("$key: $value\r\n".toByteArray(StandardCharsets.ISO_8859_1))
    }
}
