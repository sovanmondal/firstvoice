package com.firstvoice.app.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.pow

/**
 * Pre-downloads OpenStreetMap tiles for offline use.
 * Zoom 0-3: World overview (~85 tiles, ~2 MB)
 * Zoom 0-5: Continent detail (~1,365 tiles, ~15 MB)
 * Zoom 0-7: Country detail (~21,845 tiles, ~200 MB) — too large, skip
 *
 * Tiles are stored in OSMDroid's cache directory so the map
 * library picks them up automatically.
 */
class TilePreloader(private val context: Context) {

    companion object {
        private const val TAG = "TilePreloader"
        private const val TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png"
        private const val PREFS_KEY = "tiles_preloaded_zoom"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class PreloadProgress(
        val totalTiles: Int,
        val downloadedTiles: Int,
        val failedTiles: Int,
        val currentZoom: Int,
        val isComplete: Boolean
    )

    /**
     * Check if tiles have already been preloaded.
     */
    fun isPreloaded(maxZoom: Int = 3): Boolean {
        val prefs = context.getSharedPreferences("firstvoice", Context.MODE_PRIVATE)
        return prefs.getInt(PREFS_KEY, -1) >= maxZoom
    }

    /**
     * Pre-download tiles for zoom levels 0 to maxZoom.
     * Calls onProgress for each tile downloaded.
     *
     * Zoom level tile counts:
     * 0: 1 tile
     * 1: 4 tiles
     * 2: 16 tiles
     * 3: 64 tiles (total: 85)
     * 4: 256 tiles (total: 341)
     * 5: 1024 tiles (total: 1365)
     */
    suspend fun preloadTiles(
        maxZoom: Int = 3,
        onProgress: (PreloadProgress) -> Unit = {}
    ): PreloadProgress = withContext(Dispatchers.IO) {
        val tileDir = getTileCacheDir()
        var totalTiles = 0
        for (z in 0..maxZoom) {
            totalTiles += 2.0.pow(z).toInt() * 2.0.pow(z).toInt()
        }

        var downloaded = 0
        var failed = 0

        for (z in 0..maxZoom) {
            val tilesPerSide = 2.0.pow(z).toInt()
            for (x in 0 until tilesPerSide) {
                for (y in 0 until tilesPerSide) {
                    val tileFile = File(tileDir, "$z/$x/$y.png")
                    if (tileFile.exists()) {
                        downloaded++
                        continue
                    }

                    try {
                        val url = String.format(TILE_URL, z, x, y)
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "FirstVoice/1.0")
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            tileFile.parentFile?.mkdirs()
                            response.body?.byteStream()?.use { input ->
                                FileOutputStream(tileFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            downloaded++
                        } else {
                            failed++
                            Log.w(TAG, "Failed to download tile $z/$x/$y: ${response.code}")
                        }
                        response.close()
                    } catch (e: Exception) {
                        failed++
                        Log.w(TAG, "Error downloading tile $z/$x/$y: ${e.message}")
                    }

                    onProgress(PreloadProgress(totalTiles, downloaded, failed, z, false))
                }
            }
        }

        // Mark as preloaded
        context.getSharedPreferences("firstvoice", Context.MODE_PRIVATE)
            .edit().putInt(PREFS_KEY, maxZoom).apply()

        val result = PreloadProgress(totalTiles, downloaded, failed, maxZoom, true)
        onProgress(result)
        Log.d(TAG, "Tile preload complete: $downloaded/$totalTiles downloaded, $failed failed")
        result
    }

    /**
     * Get the OSMDroid tile cache directory.
     * This is where OSMDroid looks for cached tiles.
     */
    private fun getTileCacheDir(): File {
        val osmdroidDir = File(context.filesDir, "osmdroid/tiles/Mapnik")
        osmdroidDir.mkdirs()
        return osmdroidDir
    }

    /**
     * Get the total size of cached tiles in bytes.
     */
    fun getCachedTileSize(): Long {
        val dir = getTileCacheDir()
        return if (dir.exists()) {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
    }

    /**
     * Clear all cached tiles.
     */
    fun clearCache() {
        val dir = getTileCacheDir()
        if (dir.exists()) dir.deleteRecursively()
        context.getSharedPreferences("firstvoice", Context.MODE_PRIVATE)
            .edit().remove(PREFS_KEY).apply()
    }
}
