package my.id.rakyzumusic.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumArtworkTest {
    @Test
    fun blankAlbumUsesPlaceholderWithoutARequest() {
        assertEquals(
            ArtworkVisualState.Placeholder,
            initialArtworkVisualState(
                albumId = "",
                hasReadyRequest = false,
            ),
        )
    }

    @Test
    fun readyRequestStartsInLoadingState() {
        assertEquals(
            ArtworkVisualState.Loading,
            initialArtworkVisualState(
                albumId = ALBUM_ID,
                hasReadyRequest = true,
            ),
        )
    }

    @Test
    fun unavailableAuthenticatedRequestUsesFailureState() {
        assertEquals(
            ArtworkVisualState.Failed,
            initialArtworkVisualState(
                albumId = ALBUM_ID,
                hasReadyRequest = false,
            ),
        )
    }

    @Test
    fun cacheKeyIsStableAndNormalizesAlbumIdCase() {
        assertEquals(
            "rakyzu-album-artwork:$ALBUM_ID",
            artworkCacheKey(ALBUM_ID.uppercase()),
        )
    }

    private companion object {
        const val ALBUM_ID = "a2000000-0000-4000-8000-000000000001"
    }
}
