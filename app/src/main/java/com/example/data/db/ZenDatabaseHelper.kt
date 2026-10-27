package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.data.model.CategorySummary
import com.example.data.model.Channel
import com.example.data.model.Playlist

class ZenDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "zentv.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_PLAYLISTS = "playlists"
        private const val TABLE_CHANNELS = "channels"

        private const val COL_P_ID = "id"
        private const val COL_P_NAME = "name"
        private const val COL_P_SOURCE_TYPE = "source_type"
        private const val COL_P_SOURCE_PATH = "source_path"
        private const val COL_P_CHANNEL_COUNT = "channel_count"
        private const val COL_P_CREATED_AT = "created_at"

        private const val COL_C_ID = "id"
        private const val COL_C_PLAYLIST_ID = "playlist_id"
        private const val COL_C_PLAYLIST_NAME = "playlist_name"
        private const val COL_C_CHANNEL_NUMBER = "channel_number"
        private const val COL_C_NAME = "name"
        private const val COL_C_LOGO_URL = "logo_url"
        private const val COL_C_GROUP_TITLE = "group_title"
        private const val COL_C_STREAM_URL = "stream_url"
        private const val COL_C_IS_FAVORITE = "is_favorite"

        @Volatile
        private var instance: ZenDatabaseHelper? = null

        fun getInstance(context: Context): ZenDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: ZenDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                $COL_P_ID TEXT PRIMARY KEY,
                $COL_P_NAME TEXT NOT NULL,
                $COL_P_SOURCE_TYPE TEXT NOT NULL,
                $COL_P_SOURCE_PATH TEXT NOT NULL,
                $COL_P_CHANNEL_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_P_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_CHANNELS (
                $COL_C_ID TEXT PRIMARY KEY,
                $COL_C_PLAYLIST_ID TEXT NOT NULL,
                $COL_C_PLAYLIST_NAME TEXT NOT NULL,
                $COL_C_CHANNEL_NUMBER INTEGER NOT NULL,
                $COL_C_NAME TEXT NOT NULL,
                $COL_C_LOGO_URL TEXT,
                $COL_C_GROUP_TITLE TEXT NOT NULL DEFAULT '',
                $COL_C_STREAM_URL TEXT NOT NULL,
                $COL_C_IS_FAVORITE INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_c_playlist ON $TABLE_CHANNELS ($COL_C_PLAYLIST_ID, $COL_C_CHANNEL_NUMBER)")
        db.execSQL("CREATE INDEX idx_c_playlist_group ON $TABLE_CHANNELS ($COL_C_PLAYLIST_ID, $COL_C_GROUP_TITLE)")
        db.execSQL("CREATE INDEX idx_c_fav_order ON $TABLE_CHANNELS ($COL_C_IS_FAVORITE, $COL_C_CHANNEL_NUMBER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHANNELS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYLISTS")
        onCreate(db)
    }

    fun insertPlaylist(playlist: Playlist, channels: List<Channel>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Insert or replace playlist record
            val cv = ContentValues().apply {
                put(COL_P_ID, playlist.id)
                put(COL_P_NAME, playlist.name)
                put(COL_P_SOURCE_TYPE, playlist.sourceType)
                put(COL_P_SOURCE_PATH, playlist.sourcePath)
                put(COL_P_CHANNEL_COUNT, channels.size)
                put(COL_P_CREATED_AT, playlist.createdAt)
            }
            db.insertWithOnConflict(TABLE_PLAYLISTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            // Remove existing channels for this playlist if re-importing
            db.delete(TABLE_CHANNELS, "$COL_C_PLAYLIST_ID = ?", arrayOf(playlist.id))

            val sql = """
                INSERT INTO $TABLE_CHANNELS (
                    $COL_C_ID, $COL_C_PLAYLIST_ID, $COL_C_PLAYLIST_NAME,
                    $COL_C_CHANNEL_NUMBER, $COL_C_NAME, $COL_C_LOGO_URL,
                    $COL_C_GROUP_TITLE, $COL_C_STREAM_URL, $COL_C_IS_FAVORITE
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            val statement = db.compileStatement(sql)
            for (ch in channels) {
                statement.clearBindings()
                statement.bindString(1, ch.id)
                statement.bindString(2, ch.playlistId)
                statement.bindString(3, ch.playlistName)
                statement.bindLong(4, ch.channelNumber.toLong())
                statement.bindString(5, ch.name)
                if (ch.logoUrl != null) {
                    statement.bindString(6, ch.logoUrl)
                } else {
                    statement.bindNull(6)
                }
                statement.bindString(7, ch.groupTitle)
                statement.bindString(8, ch.streamUrl)
                statement.bindLong(9, if (ch.isFavorite) 1L else 0L)
                statement.executeInsert()
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PLAYLISTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_P_CREATED_AT DESC"
        )
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(COL_P_ID)
            val nameIdx = it.getColumnIndexOrThrow(COL_P_NAME)
            val typeIdx = it.getColumnIndexOrThrow(COL_P_SOURCE_TYPE)
            val pathIdx = it.getColumnIndexOrThrow(COL_P_SOURCE_PATH)
            val countIdx = it.getColumnIndexOrThrow(COL_P_CHANNEL_COUNT)
            val timeIdx = it.getColumnIndexOrThrow(COL_P_CREATED_AT)

            while (it.moveToNext()) {
                list.add(
                    Playlist(
                        id = it.getString(idIdx),
                        name = it.getString(nameIdx),
                        sourceType = it.getString(typeIdx),
                        sourcePath = it.getString(pathIdx),
                        channelCount = it.getInt(countIdx),
                        createdAt = it.getLong(timeIdx)
                    )
                )
            }
        }
        return list
    }

    fun deletePlaylist(playlistId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_CHANNELS, "$COL_C_PLAYLIST_ID = ?", arrayOf(playlistId))
            db.delete(TABLE_PLAYLISTS, "$COL_P_ID = ?", arrayOf(playlistId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getCategories(playlistId: String): List<CategorySummary> {
        val list = mutableListOf<CategorySummary>()
        val db = readableDatabase
        val query = """
            SELECT $COL_C_GROUP_TITLE, COUNT(*) as cnt 
            FROM $TABLE_CHANNELS 
            WHERE $COL_C_PLAYLIST_ID = ? AND TRIM($COL_C_GROUP_TITLE) != '' 
            GROUP BY $COL_C_GROUP_TITLE 
            ORDER BY $COL_C_GROUP_TITLE ASC
        """.trimIndent()

        val cursor = db.rawQuery(query, arrayOf(playlistId))
        cursor.use {
            val groupIdx = it.getColumnIndexOrThrow(COL_C_GROUP_TITLE)
            val cntIdx = it.getColumnIndexOrThrow("cnt")
            while (it.moveToNext()) {
                val group = it.getString(groupIdx)
                val count = it.getInt(cntIdx)
                list.add(CategorySummary(group, count))
            }
        }
        return list
    }

    fun getChannels(
        playlistId: String,
        category: String? = null,
        searchQuery: String? = null
    ): List<Channel> {
        val list = mutableListOf<Channel>()
        val db = readableDatabase

        val selection = StringBuilder("$COL_C_PLAYLIST_ID = ?")
        val args = mutableListOf(playlistId)

        if (!category.isNullOrEmpty()) {
            selection.append(" AND $COL_C_GROUP_TITLE = ?")
            args.add(category)
        }

        if (!searchQuery.isNullOrBlank()) {
            selection.append(" AND ($COL_C_NAME LIKE ? OR CAST($COL_C_CHANNEL_NUMBER AS TEXT) LIKE ?)")
            val pattern = "%${searchQuery.trim()}%"
            args.add(pattern)
            args.add(pattern)
        }

        val cursor = db.query(
            TABLE_CHANNELS,
            null,
            selection.toString(),
            args.toTypedArray(),
            null,
            null,
            "$COL_C_CHANNEL_NUMBER ASC"
        )

        cursor.use { c ->
            readChannelsFromCursor(c, list)
        }
        return list
    }

    fun getFavorites(): List<Channel> {
        val list = mutableListOf<Channel>()
        val db = readableDatabase
        // Combines favorites from all playlists, preserves original playlist channel numbers and ordering
        val cursor = db.query(
            TABLE_CHANNELS,
            null,
            "$COL_C_IS_FAVORITE = 1",
            null,
            null,
            null,
            "$COL_C_CHANNEL_NUMBER ASC, $COL_C_PLAYLIST_NAME ASC"
        )
        cursor.use { c ->
            readChannelsFromCursor(c, list)
        }
        return list
    }

    fun toggleFavorite(channelId: String): Boolean {
        val db = writableDatabase
        var newFavState = false
        db.beginTransaction()
        try {
            val cursor = db.query(
                TABLE_CHANNELS,
                arrayOf(COL_C_IS_FAVORITE),
                "$COL_C_ID = ?",
                arrayOf(channelId),
                null,
                null,
                null
            )
            var currentFav = 0
            cursor.use {
                if (it.moveToFirst()) {
                    currentFav = it.getInt(0)
                }
            }
            newFavState = (currentFav == 0)
            val cv = ContentValues().apply {
                put(COL_C_IS_FAVORITE, if (newFavState) 1 else 0)
            }
            db.update(TABLE_CHANNELS, cv, "$COL_C_ID = ?", arrayOf(channelId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return newFavState
    }

    fun setFavorite(channelId: String, isFavorite: Boolean) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_C_IS_FAVORITE, if (isFavorite) 1 else 0)
        }
        db.update(TABLE_CHANNELS, cv, "$COL_C_ID = ?", arrayOf(channelId))
    }

    private fun readChannelsFromCursor(cursor: Cursor, outList: MutableList<Channel>) {
        val idIdx = cursor.getColumnIndexOrThrow(COL_C_ID)
        val pIdIdx = cursor.getColumnIndexOrThrow(COL_C_PLAYLIST_ID)
        val pNameIdx = cursor.getColumnIndexOrThrow(COL_C_PLAYLIST_NAME)
        val noIdx = cursor.getColumnIndexOrThrow(COL_C_CHANNEL_NUMBER)
        val nameIdx = cursor.getColumnIndexOrThrow(COL_C_NAME)
        val logoIdx = cursor.getColumnIndexOrThrow(COL_C_LOGO_URL)
        val groupIdx = cursor.getColumnIndexOrThrow(COL_C_GROUP_TITLE)
        val urlIdx = cursor.getColumnIndexOrThrow(COL_C_STREAM_URL)
        val favIdx = cursor.getColumnIndexOrThrow(COL_C_IS_FAVORITE)

        while (cursor.moveToNext()) {
            outList.add(
                Channel(
                    id = cursor.getString(idIdx),
                    playlistId = cursor.getString(pIdIdx),
                    playlistName = cursor.getString(pNameIdx),
                    channelNumber = cursor.getInt(noIdx),
                    name = cursor.getString(nameIdx),
                    logoUrl = cursor.getString(logoIdx),
                    groupTitle = cursor.getString(groupIdx),
                    streamUrl = cursor.getString(urlIdx),
                    isFavorite = cursor.getInt(favIdx) == 1
                )
            )
        }
    }
}
