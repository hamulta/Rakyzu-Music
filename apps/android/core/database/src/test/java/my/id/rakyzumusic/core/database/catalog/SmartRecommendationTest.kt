package my.id.rakyzumusic.core.database.catalog

import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartRecommendationTest {
    @Test
    fun rankingUsesPrivateSignalsWithoutRepeatingConsumedTracks() {
        val tracks = listOf(
            track("recent", "artist-a", "album-a"),
            track("liked", "artist-b", "album-b"),
            track("a-2", "artist-a", "album-c"),
            track("a-3", "artist-a", "album-d"),
            track("a-4", "artist-a", "album-e"),
            track("followed", "artist-c", "album-f"),
            track("unrelated", "artist-d", "album-g"),
        )

        val result = rankSmartRecommendations(
            tracks = tracks,
            recentTrackIds = listOf("recent"),
            likedTrackIds = listOf("liked"),
            savedAlbumIds = emptyList(),
            followedArtistIds = listOf("artist-c"),
        )

        assertEquals("followed", result.first().id)
        assertTrue(result.none { it.id == "recent" || it.id == "liked" || it.id == "unrelated" })
        assertTrue(result.count { it.artistId == "artist-a" } <= 2)
    }

    @Test
    fun rankingDoesNotPretendToPersonalizeWithoutSignals() {
        assertTrue(
            rankSmartRecommendations(
                tracks = listOf(track("one", "artist", "album")),
                recentTrackIds = emptyList(),
                likedTrackIds = emptyList(),
                savedAlbumIds = emptyList(),
                followedArtistIds = emptyList(),
            ).isEmpty(),
        )
    }

    private fun track(id: String, artistId: String, albumId: String) = Track(
        id = id,
        title = id,
        artist = artistId,
        durationMs = 180_000,
        artistId = artistId,
        albumId = albumId,
    )
}
