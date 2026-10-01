package my.id.rakyzumusic.core.database.catalog

import my.id.rakyzumusic.core.model.DiscoveryMode
import my.id.rakyzumusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationEngineTest {
    @Test
    fun privateSignalsCreateExplainableMixesAndRadioWithoutConsumedTracks() {
        val result = personalize()

        assertTrue(result.recommendations.isNotEmpty())
        assertTrue(result.recommendations.none { it.track.id == "played" || it.track.id == "liked" })
        assertTrue(result.recommendations.all { it.reason.isNotBlank() })
        assertTrue(result.recommendations.groupingBy { it.track.artistId }.eachCount().values.all { it <= 2 })
        assertTrue(result.mixes.isNotEmpty())
        assertTrue(result.radioStations.any { it.id.startsWith("track-radio-") })
        assertEquals(2, result.history.first().playCount)
        assertTrue(result.tasteProfile.isPersonalized)
    }

    @Test
    fun pausedProfileKeepsHistoryButProducesNoPersonalizedSurface() {
        val result = personalize(
            preference = PersonalizationPreferenceEntity("listener", false, "balanced", 1L),
        )

        assertTrue(result.history.isNotEmpty())
        assertTrue(result.recommendations.isEmpty())
        assertTrue(result.mixes.isEmpty())
        assertTrue(result.radioStations.isEmpty())
        assertFalse(result.tasteProfile.enabled)
    }

    @Test
    fun hideAndTasteExclusionAreAccountScopedAndFailClosed() {
        val result = personalize(
            feedback = listOf(
                RecommendationFeedbackEntity("listener", "candidate-a", true, false, 2L),
                RecommendationFeedbackEntity("listener", "played", false, true, 3L),
            ),
        )

        assertTrue(result.recommendations.none { it.track.id == "candidate-a" })
        assertTrue("candidate-a" in result.tasteProfile.hiddenTrackIds)
        assertTrue("played" in result.tasteProfile.excludedTrackIds)
        assertEquals(2, result.history.first().playCount)
    }

    @Test
    fun rotationIsStableWithinDayAndChangesCollectionIdentityAcrossDays() {
        val first = personalize(rotationBucket = 20_000L)
        val repeated = personalize(rotationBucket = 20_000L)
        val nextDay = personalize(rotationBucket = 20_001L)

        assertEquals(first, repeated)
        assertNotEquals(first.mixes.map { it.id }, nextDay.mixes.map { it.id })
    }

    @Test
    fun exploreModeAdmitsFreshArtistsWhileFamiliarModeDoesNot() {
        val familiar = personalize(
            preference = PersonalizationPreferenceEntity("listener", true, "familiar", 1L),
        )
        val explore = personalize(
            preference = PersonalizationPreferenceEntity("listener", true, "explore", 1L),
        )

        assertTrue(familiar.recommendations.none { it.track.artistId == "artist-z" })
        assertTrue(explore.recommendations.any { it.track.artistId == "artist-z" })
        assertEquals(DiscoveryMode.Explore, explore.tasteProfile.discoveryMode)
    }

    private fun personalize(
        feedback: List<RecommendationFeedbackEntity> = emptyList(),
        preference: PersonalizationPreferenceEntity? = null,
        rotationBucket: Long = 20_000L,
    ) = buildPersonalization(
        userId = "listener",
        tracks = TRACKS,
        listeningSignals = listOf(StoredListeningSignal("played", 10_000L, 2)),
        likedTrackIds = listOf("liked"),
        savedAlbumIds = listOf("album-b"),
        followedArtistIds = listOf("artist-c"),
        feedback = feedback,
        preference = preference,
        rotationBucket = rotationBucket,
    )

    private companion object {
        val TRACKS = listOf(
            track("played", "artist-a", "album-a"),
            track("liked", "artist-b", "album-b"),
            track("candidate-a", "artist-a", "album-c"),
            track("candidate-a2", "artist-a", "album-d"),
            track("candidate-a3", "artist-a", "album-e"),
            track("candidate-b", "artist-b", "album-b"),
            track("candidate-c", "artist-c", "album-f"),
            track("fresh", "artist-z", "album-z"),
        )

        fun track(id: String, artistId: String, albumId: String) = Track(
            id = id,
            title = id,
            artist = artistId,
            durationMs = 180_000L,
            artistId = artistId,
            albumId = albumId,
            albumTitle = albumId,
        )
    }
}
