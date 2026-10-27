package com.example.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class TvImageLoader private constructor(context: Context) {

    private val applicationContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Sized to ~1/8 of available memory, max 24MB
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceIn(8 * 1024, 24 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val diskCacheDir = File(applicationContext.cacheDir, "zentv_logos").apply {
        if (!exists()) mkdirs()
    }

    companion object {
        @Volatile
        private var instance: TvImageLoader? = null

        fun get(context: Context): TvImageLoader {
            return instance ?: synchronized(this) {
                instance ?: TvImageLoader(context).also { instance = it }
            }
        }
    }

    fun load(imageView: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            imageView.tag = null
            imageView.setImageResource(R.drawable.ic_tv_placeholder)
            return
        }

        // Check memory cache first
        val cached = memoryCache.get(url)
        if (cached != null) {
            imageView.tag = url
            imageView.setImageBitmap(cached)
            return
        }

        // Set tag and fallback placeholder while loading
        imageView.tag = url
        imageView.setImageResource(R.drawable.ic_tv_placeholder)

        scope.launch {
            val bitmap = loadFromDiskOrNetwork(url)
            if (bitmap != null) {
                memoryCache.put(url, bitmap)
                withContext(Dispatchers.Main) {
                    if (imageView.tag == url) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun loadFromDiskOrNetwork(url: String): Bitmap? {
        val key = hashKey(url)
        val diskFile = File(diskCacheDir, key)

        if (diskFile.exists() && diskFile.length() > 0) {
            decodeSampledBitmap(diskFile.absolutePath, 128, 128)?.let {
                return it
            }
        }

        // Download from network
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }

            val body = response.body ?: return null
            val bytes = body.bytes()

            // Save to disk cache
            try {
                FileOutputStream(diskFile).use { out ->
                    out.write(bytes)
                }
            } catch (_: Exception) {}

            return decodeSampledBitmap(bytes, 128, 128)
        } catch (_: Exception) {
            return null
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565 // Half memory of ARGB_8888

        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (_: OutOfMemoryError) {
            memoryCache.evictAll()
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.RGB_565

        return try {
            BitmapFactory.decodeFile(path, options)
        } catch (_: OutOfMemoryError) {
            memoryCache.evictAll()
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun hashKey(url: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            url.hashCode().toString()
        }
    }
}
