package com.dualscreen.sync

import android.content.Context
import android.net.Uri
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

class LocalVideoServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val uris = mutableListOf<Uri>()

    fun setVideos(selectedUris: List<Uri>) {
        uris.clear()
        uris.addAll(selectedUris)
    }

    override fun serve(session: IHTTPSession): Response {
        val uriStr = session.uri
        val index = uriStr.removePrefix("/video_").toIntOrNull() ?: 0
        if (index !in uris.indices) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

        val targetUri = uris[index]
        return try {
            val contentResolver = context.contentResolver
            val pfd = contentResolver.openFileDescriptor(targetUri, "r")
                ?: return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "File missing")
            val fileLength = pfd.statSize
            pfd.close()

            val rangeHeader = session.headers["range"]
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                var start: Long = 0
                var end: Long = fileLength - 1
                val rangeValue = rangeHeader.substring(6).trim()
                val dash = rangeValue.indexOf('-')
                if (dash != -1) {
                    val startStr = rangeValue.substring(0, dash)
                    val endStr = rangeValue.substring(dash + 1)
                    if (startStr.isNotEmpty()) start = startStr.toLong()
                    if (endStr.isNotEmpty()) end = endStr.toLong()
                }
                val contentLength = end - start + 1
                val inStream: InputStream = contentResolver.openInputStream(targetUri)!!
                inStream.skip(start)
                val res = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, "video/mp4", inStream, contentLength)
                res.addHeader("Content-Range", "bytes $start-$end/$fileLength")
                res.addHeader("Accept-Ranges", "bytes")
                res.addHeader("Content-Length", contentLength.toString())
                res
            } else {
                val inStream: InputStream = contentResolver.openInputStream(targetUri)!!
                val res = newFixedLengthResponse(Response.Status.OK, "video/mp4", inStream, fileLength)
                res.addHeader("Accept-Ranges", "bytes")
                res.addHeader("Content-Length", fileLength.toString())
                res
            }
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }
}
