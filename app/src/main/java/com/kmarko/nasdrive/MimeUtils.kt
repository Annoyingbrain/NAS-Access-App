package com.kmarko.nasdrive

import android.webkit.MimeTypeMap

fun guessMimeType(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

fun isImageFile(fileName: String): Boolean = guessMimeType(fileName).startsWith("image/")
