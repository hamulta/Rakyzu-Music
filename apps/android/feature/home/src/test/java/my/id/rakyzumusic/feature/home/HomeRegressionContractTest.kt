package my.id.rakyzumusic.feature.home

import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.PersonalizedCollection
import my.id.rakyzumusic.core.model.PersonalizedTrack
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRegressionContractTest {
    @Test
    fun musicFilterExposesEveryPopulatedShelfType() {
        val state = populatedState()

        val visibility = state.toHomeSectionVisibility(HomeFilter.Music)

        assertTrue(visibility.recentlyPlayed)
        assertTrue(visibility.editorialShelves)
        assertTrue(visibility.newReleases)
        assertTrue(visibility.allTracks)
    }

    @Test
    fun newReleaseFilterSuppressesMusicOnlyShelves() {
        val state = populatedState()

        val visibility = state.toHomeSectionVisibility(HomeFilter.NewReleases)

        assertFalse(visibility.recentlyPlayed)
        assertFalse(visibility.editorialShelves)
        assertTrue(visibility.newReleases)
        assertFalse(visibility.allTracks)
    }

    @Test
    fun madeForYouFilterShowsOnlyPersonalizedSurfacesAndControls() {
        val state = populatedState().let { baseline ->
            val track = baseline.catalog.tracks.single()
            baseline.copy(
                recommendations = listOf(PersonalizedTrack(track, "Because you listen locally")),
                mixes = listOf(PersonalizedCollection("mix", "Rakyzu Mix", "Daily", listOf(track))),
                radioStations = listOf(
                    PersonalizedCollection("radio", "Track Radio", "Local radio", listOf(track)),
                ),
            )
        }

        val visibility = state.toHomeSectionVisibility(HomeFilter.MadeForYou)

        assertFalse(visibility.recentlyPlayed)
        assertFalse(visibility.editorialShelves)
        assertFalse(visibility.newReleases)
        assertFalse(visibility.allTracks)
        assertTrue(visibility.smartRecommendations)
        assertTrue(visibility.mixes)
        assertTrue(visibility.radioStations)
        assertTrue(visibility.personalizationControls)
    }

    @Test
    fun emptyFeedSuppressesEveryShelfForEveryFilter() {
        val state = HomeUiState(isRefreshing = false)

        HomeFilter.entries.forEach { filter ->
            assertEquals(
                HomeSectionVisibility(
                    recentlyPlayed = false,
                    editorialShelves = false,
                    newReleases = false,
                    allTracks = false,
                ),
                state.toHomeSectionVisibility(filter),
            )
        }
    }

    @Test(timeout = 2_000L)
    fun largeCatalogDerivationKeepsLinearOutputAndStableReleaseOrder() {
        val albums = List(200) { albumIndex ->
            Album(
                id = "album-$albumIndex",
                artistId = "artist-1",
                title = "Album $albumIndex",
                releaseDate = "release-${albumIndex.toString().padStart(3, '0')}",
            )
        }
        val tracks = albums.flatMapIndexed { albumIndex, album ->
            List(25) { trackIndex ->
                Track(
                    id = "track-$albumIndex-$trackIndex",
                    title = "Track $albumIndex-$trackIndex",
                    artist = "Rakyzu Sessions",
                    durationMs = 180_000L,
                    artistId = "artist-1",
                    albumId = album.id,
                    albumTitle = album.title,
                    trackNumber = trackIndex + 1,
                )
            }
        }
        val catalog = CatalogSnapshot(
            artists = emptyList(),
            albums = albums,
            tracks = tracks,
            lastSyncedAtEpochMillis = 42L,
        )

        val sections = catalog.toHomeDerivedSections()

        assertSame(tracks, sections.featuredQueue)
        assertEquals(tracks.size, sections.newReleaseTracks.size)
        assertEquals(tracks.map(Track::id).toSet(), sections.newReleaseTracks.map(Track::id).toSet())
        assertEquals("album-199", sections.newReleaseTracks.first().albumId)
        assertEquals("album-0", sections.newReleaseTracks.last().albumId)
    }

    private fun populatedState(): HomeUiState {
        val track = Track(
            id = "track-1",
            title = "Midnight Signal",
            artist = "Rakyzu Sessions",
            durationMs = 180_000L,
            artistId = "artist-1",
            albumId = "album-1",
            albumTitle = "Signal Zero",
        )
        val catalog = CatalogSnapshot(
            artists = emptyList(),
            albums = listOf(
                Album(
                    id = "album-1",
                    artistId = "artist-1",
                    title = "Signal Zero",
                    releaseDate = "2026-08-31",
                ),
            ),
            tracks = listOf(track),
            lastSyncedAtEpochMillis = 42L,
            editorialShelves = listOf(
                EditorialShelf(
                    id = "shelf-1",
                    title = "Rakyzu picks",
                    subtitle = null,
                    position = 0,
                    tracks = listOf(track),
                ),
            ),
        )
        return HomeUiState(
            catalog = catalog,
            recentlyPlayed = HomeFeedSnapshot(catalog, listOf(track)).recentlyPlayed,
            isRefreshing = false,
        )
    }
}
