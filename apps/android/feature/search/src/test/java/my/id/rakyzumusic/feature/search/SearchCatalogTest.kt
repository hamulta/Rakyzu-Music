package my.id.rakyzumusic.feature.search

import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCatalogTest {
    @Test
    fun normalizationIsCaseWhitespaceAndAccentInsensitive() {
        val catalog = catalog(
            artists = listOf(Artist("artist-1", "Beyoncé  Rakyzu")),
        )

        val results = catalog.search("  BEYONCE   rakyzu ")

        assertEquals(listOf("artist-1"), results.artists.map(Artist::id))
        assertEquals("beyonce rakyzu", "  Beyoncé   Rakyzu ".normalizedSearchText())
    }

    @Test
    fun exactPrefixTokenAndContainsMatchesUseStableRanking() {
        val tracks = listOf(
            track("contains", "Resignal"),
            track("token", "Midnight Signal"),
            track("prefix", "Signal Fire"),
            track("exact", "Signal"),
        )

        val results = catalog(tracks = tracks).search("signal")

        assertEquals(
            listOf("exact", "prefix", "token", "contains"),
            results.tracks.map(Track::id),
        )
    }

    @Test
    fun artistNameMatchesAssociatedAlbumsAndTracks() {
        val artist = Artist("artist-1", "Rakyzu Sessions")
        val album = Album("album-1", artist.id, "Signal Zero", "2026-08-31")
        val track = track(
            id = "track-1",
            title = "Midnight",
            artist = artist.name,
            artistId = artist.id,
            albumId = album.id,
            albumTitle = album.title,
        )

        val results = catalog(listOf(artist), listOf(album), listOf(track)).search("sessions")

        assertEquals(listOf(artist.id), results.artists.map(Artist::id))
        assertEquals(listOf(album.id), results.albums.map { it.album.id })
        assertEquals(listOf(track.id), results.tracks.map(Track::id))
    }

    @Test
    fun equalRankUsesCaseInsensitiveLabelThenStableId() {
        val tracks = listOf(
            track("track-b", "Signal Beta"),
            track("track-a", "signal alpha"),
            track("track-a2", "Signal Alpha"),
        )

        val results = catalog(tracks = tracks).search("signal")

        assertEquals(
            listOf("track-a", "track-a2", "track-b"),
            results.tracks.map(Track::id),
        )
    }

    @Test
    fun blankOrUnmatchedQueryReturnsNoResults() {
        val catalog = catalog(tracks = listOf(track("track-1", "Midnight Signal")))

        assertEquals(SearchResults(), catalog.search("   "))
        assertEquals(SearchResults(), catalog.search("unrelated"))
    }

    @Test(timeout = 2_000L)
    fun largeCatalogIsBoundedWhilePreservingTotalMatches() {
        val tracks = List(5_000) { index ->
            track("track-$index", "Signal ${index.toString().padStart(4, '0')}")
        }

        val results = catalog(tracks = tracks).search("signal")

        assertEquals(5_000, results.totalTrackMatches)
        assertEquals(MAX_RESULTS_PER_TYPE, results.tracks.size)
        assertEquals("track-0", results.tracks.first().id)
        assertTrue(results.totalMatches > results.displayedCount)
    }

    @Test
    fun browseCategoriesFollowEditorialOrderAndRejectUnknownTracks() {
        val first = track("track-1", "Midnight Signal")
        val second = track("track-2", "Afterglow Circuit")
        val snapshot = catalog(tracks = listOf(first, second)).copy(
            editorialShelves = listOf(
                EditorialShelf("later", "Late Night", null, 2, listOf(second)),
                EditorialShelf("first", "Fresh Signals", "Made for discovery", 0, listOf(first, first)),
                EditorialShelf("invalid", "Missing", null, 1, listOf(track("missing", "Hidden"))),
            ),
        )

        val categories = snapshot.browseCategories()

        assertEquals(listOf("first", "later"), categories.map(BrowseCategory::id))
        assertEquals(listOf("track-1"), categories.first().tracks.map(Track::id))
    }

    private fun catalog(
        artists: List<Artist> = emptyList(),
        albums: List<Album> = emptyList(),
        tracks: List<Track> = emptyList(),
    ) = CatalogSnapshot(artists, albums, tracks, 42L)

    private fun track(
        id: String,
        title: String,
        artist: String = "Rakyzu Sessions",
        artistId: String = "artist-1",
        albumId: String = "album-1",
        albumTitle: String = "Signal Zero",
    ) = Track(
        id = id,
        title = title,
        artist = artist,
        durationMs = 180_000L,
        artistId = artistId,
        albumId = albumId,
        albumTitle = albumTitle,
    )
}
