package my.id.rakyzumusic.core.database.catalog

import my.id.rakyzumusic.core.model.DiscoveryMode
import my.id.rakyzumusic.core.model.ListeningHistoryItem
import my.id.rakyzumusic.core.model.PersonalizedCollection
import my.id.rakyzumusic.core.model.PersonalizedTrack
import my.id.rakyzumusic.core.model.TasteProfile
import my.id.rakyzumusic.core.model.Track

internal data class PersonalizationResult(
    val history: List<ListeningHistoryItem>,
    val recommendations: List<PersonalizedTrack>,
    val mixes: List<PersonalizedCollection>,
    val radioStations: List<PersonalizedCollection>,
    val tasteProfile: TasteProfile,
)

internal fun buildPersonalization(
    userId: String,
    tracks: List<Track>,
    listeningSignals: List<StoredListeningSignal>,
    likedTrackIds: List<String>,
    savedAlbumIds: List<String>,
    followedArtistIds: List<String>,
    feedback: List<RecommendationFeedbackEntity>,
    preference: PersonalizationPreferenceEntity?,
    rotationBucket: Long,
): PersonalizationResult {
    val tracksById = tracks.associateBy(Track::id)
    val excludedIds = feedback.filter(RecommendationFeedbackEntity::excludedFromTaste)
        .mapTo(linkedSetOf(), RecommendationFeedbackEntity::trackId)
    val hiddenIds = feedback.filter(RecommendationFeedbackEntity::isHidden)
        .mapTo(linkedSetOf(), RecommendationFeedbackEntity::trackId)
    val mode = DiscoveryMode.fromStorage(preference?.discoveryMode)
    val enabled = preference?.enabled ?: true
    val history = listeningSignals.mapNotNull { signal ->
        tracksById[signal.trackId]?.let { track ->
            ListeningHistoryItem(
                track = track,
                lastPlayedAtEpochMillis = signal.playedAtEpochMs.coerceAtLeast(0L),
                playCount = signal.playCount.coerceIn(1, MAX_PLAY_COUNT),
            )
        }
    }
    val usableHistory = history.filterNot { it.track.id in excludedIds }
    val usableLiked = likedTrackIds.filterNot(excludedIds::contains).mapNotNull(tracksById::get)
    val savedAlbums = savedAlbumIds.toSet()
    val followedArtists = followedArtistIds.toSet()
    val signalCount = (
        usableHistory.map { it.track.id } + usableLiked.map { it.id } +
            savedAlbums.map { "album:$it" } + followedArtists.map { "artist:$it" }
        ).distinct().size
    val profile = TasteProfile(
        enabled = enabled,
        discoveryMode = mode,
        signalCount = signalCount.coerceAtMost(MAX_SIGNAL_COUNT),
        excludedTrackIds = excludedIds,
        hiddenTrackIds = hiddenIds,
    )
    if (!enabled || signalCount == 0 || tracks.isEmpty()) {
        return PersonalizationResult(history, emptyList(), emptyList(), emptyList(), profile)
    }

    val artistAffinity = mutableMapOf<String, Int>()
    val albumAffinity = mutableMapOf<String, Int>()
    usableHistory.forEachIndexed { index, item ->
        val recency = (HISTORY_RECENCY_MAX - index).coerceAtLeast(1)
        val repeat = item.playCount.coerceAtMost(5) * 2
        artistAffinity.add(item.track.artistId, recency + repeat)
        albumAffinity.add(item.track.albumId, recency / 2 + repeat)
    }
    usableLiked.forEach { track ->
        artistAffinity.add(track.artistId, 12)
        albumAffinity.add(track.albumId, 8)
    }
    followedArtists.forEach { artistAffinity.add(it, 16) }
    savedAlbums.forEach { albumAffinity.add(it, 14) }

    val consumedIds = history.mapTo(mutableSetOf()) { it.track.id } + likedTrackIds
    val seed = userId.hashCode().toLong() xor rotationBucket
    val scored = tracks.asSequence()
        .filterNot { it.id in hiddenIds || it.id in consumedIds }
        .map { track ->
            val affinity = (artistAffinity[track.artistId] ?: 0) +
                (albumAffinity[track.albumId] ?: 0)
            val explorationBonus = when (mode) {
                DiscoveryMode.Familiar -> 0
                DiscoveryMode.Balanced -> if (track.artistId !in artistAffinity) 2 else 0
                DiscoveryMode.Explore -> if (track.artistId !in artistAffinity) 12 else 1
            }
            RankedTrack(track, affinity + explorationBonus, rotationKey(track.id, seed))
        }
        .filter { mode != DiscoveryMode.Familiar || it.score > 0 }
        .sortedWith(
            compareByDescending<RankedTrack>(RankedTrack::score)
                .thenBy(RankedTrack::rotationKey)
                .thenBy { it.track.id },
        )
        .toList()

    val ranked = diversify(scored, MAX_RECOMMENDATIONS, MAX_PER_ARTIST)
    val recommendations = ranked.map { rankedTrack ->
        PersonalizedTrack(
            track = rankedTrack.track,
            reason = recommendationReason(
                rankedTrack.track,
                followedArtists,
                savedAlbums,
                artistAffinity,
                albumAffinity,
                mode,
            ),
        )
    }
    val topArtists = artistAffinity.entries
        .filter { it.key.isNotBlank() }
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(MAX_MIXES)
        .map(Map.Entry<String, Int>::key)
    val mixes = topArtists.mapIndexedNotNull { index, artistId ->
        val familiar = tracks.filter { it.artistId == artistId && it.id !in hiddenIds }
        val discovery = ranked.map(RankedTrack::track).filterNot { it.artistId == artistId }
        val mixTracks = interleave(familiar, discovery).distinctBy(Track::id).take(MAX_COLLECTION_TRACKS)
        if (mixTracks.isEmpty()) null else PersonalizedCollection(
            id = "daily-${index + 1}-${rotationBucket}",
            title = "Rakyzu Mix ${index + 1}",
            subtitle = "A private daily mix of familiar favorites and fresh catalog picks.",
            tracks = mixTracks,
        )
    }

    val radioStations = buildList {
        usableHistory.firstOrNull()?.track?.let { seedTrack ->
            val stationTracks = tracks
                .filterNot { it.id == seedTrack.id || it.id in hiddenIds }
                .sortedWith(
                    compareByDescending<Track> {
                        (if (it.artistId == seedTrack.artistId) 4 else 0) +
                            (if (it.albumId == seedTrack.albumId) 2 else 0)
                    }.thenBy { rotationKey(it.id, seed xor seedTrack.id.hashCode().toLong()) },
                )
                .let { listOf(seedTrack) + it }
                .distinctBy(Track::id)
                .take(MAX_COLLECTION_TRACKS)
            add(
                PersonalizedCollection(
                    id = "track-radio-${seedTrack.id}",
                    title = "${seedTrack.title} Radio",
                    subtitle = "Starts with ${seedTrack.title} and stays close to its sound.",
                    tracks = stationTracks,
                ),
            )
        }
        topArtists.firstOrNull()?.let { artistId ->
            val artistName = tracks.firstOrNull { it.artistId == artistId }?.artist.orEmpty()
            val stationTracks = tracks
                .filterNot { it.id in hiddenIds }
                .sortedWith(
                    compareByDescending<Track> { if (it.artistId == artistId) 2 else 0 }
                        .thenBy { rotationKey(it.id, seed xor artistId.hashCode().toLong()) },
                )
                .distinctBy(Track::id)
                .take(MAX_COLLECTION_TRACKS)
            if (stationTracks.isNotEmpty()) add(
                PersonalizedCollection(
                    id = "artist-radio-$artistId",
                    title = "${artistName.ifBlank { "Artist" }} Radio",
                    subtitle = "Music from this artist with diverse Rakyzu catalog discoveries.",
                    tracks = stationTracks,
                ),
            )
        }
    }.distinctBy(PersonalizedCollection::id)

    return PersonalizationResult(history, recommendations, mixes, radioStations, profile)
}

private data class RankedTrack(val track: Track, val score: Int, val rotationKey: Long)

private fun diversify(
    ranked: List<RankedTrack>,
    limit: Int,
    maxPerArtist: Int,
): List<RankedTrack> {
    val artistCounts = mutableMapOf<String, Int>()
    return ranked.filter { item ->
        val key = item.track.artistId.ifBlank { "track:${item.track.id}" }
        val count = artistCounts[key] ?: 0
        if (count >= maxPerArtist) false else {
            artistCounts[key] = count + 1
            true
        }
    }.take(limit)
}

private fun recommendationReason(
    track: Track,
    followedArtists: Set<String>,
    savedAlbums: Set<String>,
    artistAffinity: Map<String, Int>,
    albumAffinity: Map<String, Int>,
    mode: DiscoveryMode,
): String = when {
    track.artistId in followedArtists -> "Because you follow ${track.artist.ifBlank { "this artist" }}"
    track.albumId in savedAlbums -> "From an album saved in your Library"
    (artistAffinity[track.artistId] ?: 0) > 0 ->
        "Based on artists you listen to on this device"
    (albumAffinity[track.albumId] ?: 0) > 0 -> "Based on albums you enjoy"
    mode == DiscoveryMode.Explore -> "A fresh catalog discovery"
    else -> "Selected from your private taste profile"
}

private fun interleave(first: List<Track>, second: List<Track>): List<Track> = buildList {
    val size = maxOf(first.size, second.size)
    repeat(size) { index ->
        first.getOrNull(index)?.let(::add)
        second.getOrNull(index)?.let(::add)
    }
}

private fun MutableMap<String, Int>.add(key: String, value: Int) {
    if (key.isNotBlank()) this[key] = ((this[key] ?: 0) + value).coerceAtMost(MAX_AFFINITY)
}

private fun rotationKey(id: String, seed: Long): Long = id.fold(seed) { value, character ->
    (value * 31L + character.code).and(Long.MAX_VALUE)
}

private const val MAX_PLAY_COUNT = 10_000
private const val MAX_SIGNAL_COUNT = 500
private const val MAX_AFFINITY = 10_000
private const val HISTORY_RECENCY_MAX = 20
private const val MAX_RECOMMENDATIONS = 20
private const val MAX_COLLECTION_TRACKS = 20
private const val MAX_PER_ARTIST = 2
private const val MAX_MIXES = 3
