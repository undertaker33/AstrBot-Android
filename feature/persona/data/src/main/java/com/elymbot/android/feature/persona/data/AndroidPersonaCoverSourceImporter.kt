package com.elymbot.android.feature.persona.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject

class AndroidPersonaCoverSourceImporter @Inject constructor(
    private val contentResolver: ContentResolver,
) : PersonaCoverSourceImporter {
    override fun importReadOnly(sourceUriString: String, destination: File): ImportedPersonaCover {
        val parent = requireNotNull(destination.parentFile)
        val sourceCopy = File(parent, "${destination.name}.source")
        parent.mkdirs()
        try {
            contentResolver.openInputStream(Uri.parse(sourceUriString)).use { input ->
                requireNotNull(input) { "Unable to open image source" }
                FileOutputStream(sourceCopy).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_SOURCE_BYTES) { "Image source is too large" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceCopy.path, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image is not decodable" }
            var sample = 1
            while (maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE * 2) sample *= 2
            val decoded = requireNotNull(BitmapFactory.decodeFile(sourceCopy.path, BitmapFactory.Options().apply { inSampleSize = sample })) {
                "Image is not decodable"
            }
            val oriented = decoded.applyOrientation(readOrientation(sourceCopy))
            val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(oriented.width, oriented.height))
            val normalized = if (scale < 1f) Bitmap.createScaledBitmap(
                oriented, (oriented.width * scale).toInt().coerceAtLeast(1), (oriented.height * scale).toInt().coerceAtLeast(1), true,
            ) else oriented
            FileOutputStream(destination).use { output ->
                check(normalized.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Image encoding failed" }
            }
            val normalizedWidth = normalized.width
            val normalizedHeight = normalized.height
            if (normalized !== oriented) normalized.recycle()
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
            return ImportedPersonaCover(normalizedWidth, normalizedHeight, sha256(destination))
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            sourceCopy.delete()
        }
    }

    private fun Bitmap.applyOrientation(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun readOrientation(file: File): Int = FileInputStream(file).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object { const val MAX_EDGE = 2048; const val MAX_SOURCE_BYTES = 64L * 1024 * 1024 }
}
