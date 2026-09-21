package com.kmarko.nasdrive

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.Properties

data class NasEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * Talks SMB2/3 to the NAS. The NAS just needs to be reachable at [SmbConfig.host] -
 * when that's a Tailscale IP or MagicDNS name, the Tailscale app on the phone is
 * what makes the address routable; this class only speaks plain SMB over it.
 */
class SmbRepository(private val config: SmbConfig) {

    private val cifsContext: CIFSContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
        }
        val base: CIFSContext = BaseContext(PropertyConfiguration(props))
        base.withCredentials(NtlmPasswordAuthenticator(config.domain, config.username, config.password))
    }

    private var streamServer: NasStreamServer? = null

    private fun buildUrl(path: String, isDirectory: Boolean): String {
        // jcifs-ng's SmbFile(String, CIFSContext) does not URL-decode the path - it
        // wants the literal component names, not percent-encoded ones. Encoding here
        // (e.g. spaces as %20) makes jcifs search for a file literally named "%20",
        // which doesn't exist, and fails with "the system cannot find the file/path
        // specified" for any nested folder whose name needs encoding.
        val portPart = if (config.port != 445) ":${config.port}" else ""
        val cleanPath = path.split("/")
            .filter { it.isNotBlank() }
            .joinToString("/")
        val base = "smb://${config.host}$portPart/${config.shareName}/"
        return if (cleanPath.isBlank()) base else base + cleanPath + if (isDirectory) "/" else ""
    }

    suspend fun testConnection() = withContext(Dispatchers.IO) {
        val root = SmbFile(buildUrl("", true), cifsContext)
        root.connect()
    }

    // Returned in whatever order the server lists them - the ViewModel applies the
    // user's chosen sort (name/date/size) so switching sort order doesn't need a
    // fresh listing.
    suspend fun list(path: String): List<NasEntry> = withContext(Dispatchers.IO) {
        val dir = SmbFile(buildUrl(path, true), cifsContext)
        dir.listFiles()
            .filter { it.name.isNotBlank() }
            .map { f ->
                NasEntry(
                    name = f.name.trimEnd('/'),
                    isDirectory = f.isDirectory,
                    size = if (f.isDirectory) 0L else f.length(),
                    lastModified = f.lastModified()
                )
            }
    }

    suspend fun download(remotePath: String, destUri: Uri, resolver: ContentResolver, onProgress: (Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val file = SmbFile(buildUrl(remotePath, false), cifsContext)
            file.inputStream.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    copyStream(input, output, onProgress)
                }
            }
        }

    suspend fun upload(localUri: Uri, remotePath: String, resolver: ContentResolver, onProgress: (Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val file = SmbFile(buildUrl(remotePath, false), cifsContext)
            file.outputStream.use { output ->
                resolver.openInputStream(localUri)?.use { input ->
                    copyStream(input, output, onProgress)
                }
            }
        }

    suspend fun delete(path: String, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        // jcifs-ng's SmbFile.delete() on a directory recurses and removes its
        // contents too - there's no separate "empty directories only" mode.
        val file = SmbFile(buildUrl(path, isDirectory), cifsContext)
        file.delete()
    }

    suspend fun move(sourcePath: String, destPath: String, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        val source = SmbFile(buildUrl(sourcePath, isDirectory), cifsContext)
        val dest = SmbFile(buildUrl(destPath, isDirectory), cifsContext)
        source.renameTo(dest)
    }

    suspend fun copy(sourcePath: String, destPath: String, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        // SmbFile.copyTo() streams the data client-side (there's no server-side
        // remote-copy call wired up here) but handles directories recursively on
        // its own, same as delete().
        val source = SmbFile(buildUrl(sourcePath, isDirectory), cifsContext)
        val dest = SmbFile(buildUrl(destPath, isDirectory), cifsContext)
        source.copyTo(dest)
    }

    suspend fun createFolder(path: String) = withContext(Dispatchers.IO) {
        val dir = SmbFile(buildUrl(path, true), cifsContext)
        dir.mkdir()
    }

    /**
     * Downsampled preview for the file browser's thumbnail grid cells. Skips files
     * above [MAX_THUMBNAIL_SOURCE_BYTES] rather than pulling a huge original over SMB
     * just to shrink it, and returns null (falls back to the generic file icon) on
     * any decode failure instead of surfacing an error - a missing thumbnail isn't
     * worth interrupting browsing over.
     */
    suspend fun loadThumbnail(remotePath: String, maxDimension: Int = 256): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = SmbFile(buildUrl(remotePath, false), cifsContext)
            if (file.length() > MAX_THUMBNAIL_SOURCE_BYTES) return@withContext null
            val bytes = file.inputStream.use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
                sample *= 2
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    /** Called from NasStreamServer's own request-handling threads, one fresh handle per request. */
    internal fun openRandomAccess(remotePath: String): SmbRandomAccessFile {
        val file = SmbFile(buildUrl(remotePath, false), cifsContext)
        return SmbRandomAccessFile(file, "r")
    }

    /** Starts the loopback streaming proxy on first use and returns a playable URL for [remotePath]. */
    suspend fun streamUrl(remotePath: String): String = withContext(Dispatchers.IO) {
        val server = streamServer ?: NasStreamServer(this@SmbRepository).also {
            it.start()
            streamServer = it
        }
        server.urlFor(remotePath)
    }

    private fun copyStream(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(256 * 1024)
        var total = 0L
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            total += read
            onProgress(total)
        }
    }

    fun close() {
        // jcifs-ng pools its own connections internally; nothing to release per-session.
        streamServer?.stop()
        streamServer = null
    }

    private companion object {
        const val MAX_THUMBNAIL_SOURCE_BYTES = 8L * 1024 * 1024
    }
}
