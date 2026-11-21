package app.mannybrowser

import app.mannybrowser.data.Bookmark
import app.mannybrowser.data.Shortcut
import app.mannybrowser.data.StartTiles
import org.junit.Assert.*
import org.junit.Test

class StartTilesTest {
    @Test fun shortcutsComeFirstThenFavorites() {
        val tiles = StartTiles.merge(
            listOf(Shortcut("Mail", "https://mail.example.com")),
            listOf(Bookmark("News", "https://news.example.com"), Bookmark("Docs", "https://docs.example.com"))
        )
        assertEquals(listOf("https://mail.example.com", "https://news.example.com", "https://docs.example.com"), tiles.map { it.url })
        assertFalse(tiles[0].bookmark)
        assertTrue(tiles[1].bookmark)
    }

    @Test fun sameUrlNeverAppearsTwiceAndShortcutWins() {
        val tiles = StartTiles.merge(
            listOf(Shortcut("My name", "https://example.com")),
            listOf(Bookmark("Example Domain", "https://example.com"))
        )
        assertEquals(1, tiles.size)
        assertEquals("My name", tiles[0].name)
        assertFalse(tiles[0].bookmark)
    }

    @Test fun everyFavoriteBecomesATile() {
        val bookmarks = listOf(
            Bookmark("A", "https://a.example.com"),
            Bookmark("B", "https://b.example.com"),
            Bookmark("C", "http://c.example.com/path?x=1")
        )
        val tiles = StartTiles.merge(emptyList(), bookmarks)
        assertEquals(3, tiles.size)
        assertTrue(tiles.all { it.bookmark })
    }

    @Test fun emptyInputsGiveNoTiles() {
        assertTrue(StartTiles.merge(emptyList(), emptyList()).isEmpty())
    }

    @Test fun faviconKeyIsTheLowercaseHost() {
        assertEquals("example.com", StartTiles.faviconKey("https://Example.COM/path?q=1"))
        assertEquals("www.5min.at", StartTiles.faviconKey("https://www.5min.at/villach/"))
        assertEquals("localhost", StartTiles.faviconKey("http://localhost:8765/page"))
    }

    @Test fun faviconKeyRejectsUrlsWithoutHost() {
        assertNull(StartTiles.faviconKey("about:blank"))
        assertNull(StartTiles.faviconKey("not a url at all:::"))
        assertNull(StartTiles.faviconKey(""))
    }
}
