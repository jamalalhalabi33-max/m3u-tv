package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.ZenDatabaseHelper
import com.example.data.model.Channel
import com.example.data.model.Playlist
import com.example.data.parser.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZenTvLogicTest {

    private lateinit var context: Context
    private lateinit var db: ZenDatabaseHelper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = ZenDatabaseHelper.getInstance(context)
    }

    @Test
    fun testM3uParserWithMetadataAndCategories() {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cnn" tvg-name="CNN Int" tvg-logo="http://logo.com/cnn.png" tvg-chno="5" group-title="News", CNN International
            http://stream.example.com/cnn.m3u8
            #EXTINF:-1 tvg-id="espn" tvg-name="ESPN HD" tvg-logo="http://logo.com/espn.png" tvg-chno="2" group-title="Sports", ESPN HD
            http://stream.example.com/espn.m3u8
            #EXTINF:-1 group-title="Movies", HBO Cinema
            http://stream.example.com/hbo.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(
            ByteArrayInputStream(m3uContent.toByteArray(Charsets.UTF_8)),
            playlistId = "p1",
            playlistName = "Test Playlist"
        )

        assertEquals(3, channels.size)

        // First channel: channelNumber = 5 (from tvg-chno)
        assertEquals("CNN International", channels[0].name)
        assertEquals(5, channels[0].channelNumber)
        assertEquals("News", channels[0].groupTitle)
        assertEquals("http://logo.com/cnn.png", channels[0].logoUrl)

        // Second channel: channelNumber = 2
        assertEquals("ESPN HD", channels[1].name)
        assertEquals(2, channels[1].channelNumber)
        assertEquals("Sports", channels[1].groupTitle)

        // Third channel: sequential number 3 (fallback)
        assertEquals("HBO Cinema", channels[2].name)
        assertEquals(3, channels[2].channelNumber)
        assertEquals("Movies", channels[2].groupTitle)
    }

    @Test
    fun testDatabaseCategoriesAndFavoritesSorting() {
        val p1 = Playlist("p1", "Playlist A", "URL", "http://test.com/a.m3u", 2)
        val channels1 = listOf(
            Channel("c1", "p1", "Playlist A", 20, "Channel 20", null, "News", "http://s1", isFavorite = true),
            Channel("c2", "p1", "Playlist A", 5, "Channel 5", null, "Sports", "http://s2", isFavorite = false)
        )
        db.insertPlaylist(p1, channels1)

        val p2 = Playlist("p2", "Playlist B", "URL", "http://test.com/b.m3u", 2)
        val channels2 = listOf(
            Channel("c3", "p2", "Playlist B", 2, "Channel 2", null, "News", "http://s3", isFavorite = true),
            Channel("c4", "p2", "Playlist B", 12, "Channel 12", null, "Movies", "http://s4", isFavorite = true)
        )
        db.insertPlaylist(p2, channels2)

        // Verify categories for playlist A
        val categories = db.getCategories("p1")
        assertEquals(2, categories.size)

        // Verify favorites combined from all playlists and sorted by original channel number
        // Channel numbers: 20 (p1), 2 (p2), 12 (p2).
        // Expected order: 2, 12, 20
        val favorites = db.getFavorites()
        assertEquals(3, favorites.size)
        assertEquals(2, favorites[0].channelNumber)
        assertEquals(12, favorites[1].channelNumber)
        assertEquals(20, favorites[2].channelNumber)

        // Test toggle favorite
        val newState = db.toggleFavorite("c2")
        assertTrue(newState)
        assertEquals(4, db.getFavorites().size)

        val reverted = db.toggleFavorite("c2")
        assertFalse(reverted)
        assertEquals(3, db.getFavorites().size)
    }

    @Test
    fun testSearchFiltering() {
        val p = Playlist("search_p", "Search Playlist", "URL", "http://test.com/search.m3u", 3)
        val channels = listOf(
            Channel("s1", "search_p", "Search Playlist", 1, "BBC News", null, "News", "http://s1"),
            Channel("s2", "search_p", "Search Playlist", 2, "Sky Sports 1", null, "Sports", "http://s2"),
            Channel("s3", "search_p", "Search Playlist", 101, "BBC One", null, "Entertainment", "http://s3")
        )
        db.insertPlaylist(p, channels)

        // Search by name "BBC"
        val bbcResults = db.getChannels("search_p", null, "BBC")
        assertEquals(2, bbcResults.size)

        // Search by channel number "101"
        val numResults = db.getChannels("search_p", null, "101")
        assertEquals(1, numResults.size)
        assertEquals("BBC One", numResults[0].name)
    }
}
