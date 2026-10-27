package com.example.data.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import com.example.R
import com.example.data.model.VideoPlayerApp

class SettingsManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("zentv_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PLAYER_PACKAGE = "preferred_player_package"
        private const val KEY_PLAYER_ACTIVITY = "preferred_player_activity"
        private const val KEY_PLAYER_LABEL = "preferred_player_label"

        private val KNOWN_TV_PLAYERS = listOf(
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro",
            "com.brouken.player",
            "org.xbmc.kodi",
            "org.courville.nova",
            "is.xyz.mpv"
        )
    }

    fun getPreferredPlayerPackage(): String? = prefs.getString(KEY_PLAYER_PACKAGE, null)
    fun getPreferredPlayerActivity(): String? = prefs.getString(KEY_PLAYER_ACTIVITY, null)
    fun getPreferredPlayerLabel(): String? = prefs.getString(KEY_PLAYER_LABEL, null)

    fun setPreferredPlayer(player: VideoPlayerApp) {
        if (player.isSystemDefault) {
            prefs.edit()
                .remove(KEY_PLAYER_PACKAGE)
                .remove(KEY_PLAYER_ACTIVITY)
                .remove(KEY_PLAYER_LABEL)
                .apply()
        } else {
            prefs.edit()
                .putString(KEY_PLAYER_PACKAGE, player.packageName)
                .putString(KEY_PLAYER_ACTIVITY, player.activityName)
                .putString(KEY_PLAYER_LABEL, player.label)
                .apply()
        }
    }

    fun getInstalledVideoPlayers(): List<VideoPlayerApp> {
        val pm = context.packageManager
        val playersMap = mutableMapOf<String, VideoPlayerApp>()

        // 1. System Default entry first
        val defaultApp = VideoPlayerApp(
            packageName = "",
            activityName = null,
            label = context.getString(R.string.player_system_default),
            icon = null,
            isSystemDefault = true
        )

        // 2. Query Intent for video/*
        val videoIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("http://example.com/video.mp4"), "video/*")
        }
        val resolveInfos = pm.queryIntentActivities(videoIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && !playersMap.containsKey(pkg)) {
                val label = info.loadLabel(pm).toString()
                val icon = info.loadIcon(pm)
                playersMap[pkg] = VideoPlayerApp(
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    label = label,
                    icon = icon
                )
            }
        }

        // 3. Query Intent for mpegurl / m3u8
        val m3uIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("http://example.com/stream.m3u8"), "application/x-mpegurl")
        }
        val m3uResolve = pm.queryIntentActivities(m3uIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in m3uResolve) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && !playersMap.containsKey(pkg)) {
                val label = info.loadLabel(pm).toString()
                val icon = info.loadIcon(pm)
                playersMap[pkg] = VideoPlayerApp(
                    packageName = pkg,
                    activityName = info.activityInfo.name,
                    label = label,
                    icon = icon
                )
            }
        }

        // 4. Check known TV players explicitly
        for (pkg in KNOWN_TV_PLAYERS) {
            if (!playersMap.containsKey(pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    playersMap[pkg] = VideoPlayerApp(
                        packageName = pkg,
                        activityName = null,
                        label = label,
                        icon = icon
                    )
                } catch (_: PackageManager.NameNotFoundException) {}
            }
        }

        val result = mutableListOf<VideoPlayerApp>()
        result.add(defaultApp)
        result.addAll(playersMap.values.sortedBy { it.label.lowercase() })
        return result
    }

    fun launchStream(streamUrl: String): Boolean {
        val uri = Uri.parse(streamUrl)
        val prefPkg = getPreferredPlayerPackage()
        val prefAct = getPreferredPlayerActivity()

        if (!prefPkg.isNullOrEmpty()) {
            val targetedIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                setPackage(prefPkg)
                if (!prefAct.isNullOrEmpty()) {
                    setClassName(prefPkg, prefAct)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(targetedIntent)
                return true
            } catch (e: Exception) {
                // If specific player launch failed, fall back to chooser
            }
        }

        // Fallback: generic viewer or chooser
        val genericIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            val chooser = Intent.createChooser(genericIntent, "Play channel with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, R.string.error_playback, Toast.LENGTH_LONG).show()
            false
        }
    }
}
