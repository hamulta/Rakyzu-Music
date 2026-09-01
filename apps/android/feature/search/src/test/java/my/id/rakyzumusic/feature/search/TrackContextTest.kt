package my.id.rakyzumusic.feature.search

import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackContextTest {
    @Test
    fun canonicalArtistAndAlbumIdsEnableInternalNavigation() {
        val capabilities = track(
            artistId = "artist-1",
            albumId = "album-1",
        ).contextCapabilities()

        assertTrue(capabilities.canViewArtist)
        assertTrue(capabilities.canViewAlbum)
    }

    @Test
    fun blankIdsDisableMetadataNavigation() {
        val capabilities = track(
            artistId = "",
            albumId = "   ",
        ).contextCapabilities()

        assertFalse(capabilities.canViewArtist)
        assertFalse(capabilities.canViewAlbum)
    }

    @Test
    fun oneValidAssociationDoesNotEnableTheOther() {
        val capabilities = track(
            artistId = "artist-1",
            albumId = "",
        ).contextCapabilities()

        assertTrue(capabilities.canViewArtist)
        assertFalse(capabilities.canViewAlbum)
    }

    private fun track(artistId: String, albumId: String) = Track(
        id = "track-1",
        title = "Midnight Signal",
        artist = "Rakyzu Sessions",
        durationMs = 180_000L,
        artistId = artistId,
        albumId = albumId,
        albumTitle = "Signal Zero",
    )
}
