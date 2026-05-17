package com.firstvoice.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "FV.Camera"

object CameraHelper {

    fun bitmapToBase64(bitmap: Bitmap, maxSize: Int = 1024): String {
        Log.d(TAG, "bitmapToBase64() ${bitmap.width}x${bitmap.height} maxSize=$maxSize")
        val resized = resizeBitmap(bitmap, maxSize)
        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        Log.d(TAG, "bitmapToBase64() → ${resized.width}x${resized.height} base64Len=${b64.length}")
        return b64
    }

    fun uriToBase64(context: Context, uri: Uri, maxSize: Int = 1024): String? {
        Log.d(TAG, "uriToBase64() uri=$uri")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: run {
                Log.e(TAG, "uriToBase64() cannot open input stream")
                return null
            }
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) {
                Log.e(TAG, "uriToBase64() bitmap decode returned null")
                return null
            }
            Log.d(TAG, "uriToBase64() decoded ${bitmap.width}x${bitmap.height}")
            bitmapToBase64(bitmap, maxSize)
        } catch (e: Exception) {
            Log.e(TAG, "uriToBase64() FAILED", e)
            null
        }
    }

    /**
     * Create a temporary file for camera capture.
     */
    fun createTempImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(context.filesDir, "photos")
        storageDir.mkdirs()
        return File.createTempFile("FV_${timeStamp}_", ".jpg", storageDir)
    }

    /**
     * Save a bitmap to the app's photo directory.
     */
    fun saveBitmap(context: Context, bitmap: Bitmap, filename: String): File {
        val dir = File(context.filesDir, "photos")
        dir.mkdirs()
        val file = File(dir, filename)
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
