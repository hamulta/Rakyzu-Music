package my.id.rakyzumusic.core.database.catalog

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.DiscoveryMode
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track

interface CatalogLocalDataSource {
    fun observeCatalog(): Flow<CatalogSnapshot>

    fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot>

    suspend fun replaceCatalog(snapshot: CatalogSnapshot, syncedAtEpochMillis: Long)

    suspend fun recordRecentlyPlayed(userId: String, trackId: String, playedAtEpochMillis: Long): Boolean

    suspend fun setPersonalizationEnabled(userId: String, enabled: Boolean): Boolean = false

    suspend fun setDiscoveryMode(userId: String, mode: DiscoveryMode): Boolean = false

    suspend fun setRecommendationHidden(userId: String, trackId: String, hidden: Boolean): Boolean = false

    suspend fun setTasteSignalExcluded(userId: String, trackId: String, excluded: Boolean): Boolean = false

    suspend fun clearPersonalizationData(userId: String): Boolean = false
}

data class RakyzuLocalDataSources(
    val catalog: CatalogLocalDataSource,
    val library: LibraryLocalDataSource,
    val playlist: PlaylistLocalDataSource,
    val playbackQueue: PlaybackQueueLocalDataSource,
    val downloads: OfflineDownloadLocalDataSource,
    val trackContext: TrackContextLocalDataSource,
)

fun createRakyzuLocalDataSources(context: Context): RakyzuLocalDataSources {
    val database = RakyzuDatabaseFactory.create(context)
    return RakyzuLocalDataSources(
        catalog = RoomCatalogLocalDataSource(database),
        library = RoomLibraryLocalDataSource(database),
        playlist = RoomPlaylistLocalDataSource(database),
        playbackQueue = RoomPlaybackQueueLocalDataSource(database),
        downloads = RoomOfflineDownloadLocalDataSource(database),
        trackContext = RoomTrackContextLocalDataSource(database),
    )
}

fun createCatalogLocalDataSource(context: Context): CatalogLocalDataSource =
    createRakyzuLocalDataSources(context).catalog

internal class RoomCatalogLocalDataSource(
    private val database: RakyzuDatabase,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : CatalogLocalDataSource {
    private val dao = database.catalogDao()

    override fun observeCatalog(): Flow<CatalogSnapshot> = database.invalidationTracker
        .createFlow(
            "artists",
            "albums",
            "tracks",
            "editorial_shelves",
            "editorial_shelf_tracks",
            "sync_metadata",
        )
        .map { dao.readSnapshot().toDomain() }

    override fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot> = database.invalidationTracker
        .createFlow(
            "artists",
            "albums",
            "tracks",
            "editorial_shelves",
            "editorial_shelf_tracks",
            "recently_played",
            "library_liked_tracks",
            "library_saved_albums",
            "library_followed_artists",
            "personalization_preferences",
            "recommendation_feedback",
            "sync_metadata",
        )
        .map {
            val catalog = dao.readSnapshot().toDomain()
            val tracksById = catalog.tracks.associateBy(Track::id)
            val listeningSignals = dao.getListeningSignals(userId, RECENTLY_PLAYED_LIMIT)
            val recentIds = listeningSignals.map(StoredListeningSignal::trackId)
            val likedTrackIds = dao.getLikedTrackIds(userId, SIGNAL_LIMIT)
            val savedAlbumIds = dao.getSavedAlbumIds(userId, SIGNAL_LIMIT)
            val followedArtistIds = dao.getFollowedArtistIds(userId, SIGNAL_LIMIT)
            val personalization = buildPersonalization(
                userId = userId,
                tracks = catalog.tracks,
                listeningSignals = listeningSignals,
                likedTrackIds = likedTrackIds,
                savedAlbumIds = savedAlbumIds,
                followedArtistIds = followedArtistIds,
                feedback = dao.getRecommendationFeedback(userId),
                preference = dao.getPersonalizationPreference(userId),
                rotationBucket = currentTimeMillis().coerceAtLeast(0L) / MILLIS_PER_DAY,
            )
            HomeFeedSnapshot(
                catalog = catalog,
                recentlyPlayed = recentIds.mapNotNull(tracksById::get),
                listeningHistory = personalization.history,
                recommendations = personalization.recommendations,
                mixes = personalization.mixes,
                radioStations = personalization.radioStations,
                tasteProfile = personalization.tasteProfile,
            )
        }

    override suspend fun replaceCatalog(snapshot: CatalogSnapshot, syncedAtEpochMillis: Long) {
        dao.replaceCatalog(
            artists = snapshot.artists.map(Artist::toEntity),
            albums = snapshot.albums.map(Album::toEntity),
            tracks = snapshot.tracks.map(Track::toEntity),
            editorialShelves = snapshot.editorialShelves.map(EditorialShelf::toEntity),
            editorialShelfTracks = snapshot.editorialShelves.flatMap { shelf ->
                shelf.tracks.mapIndexed { index, track ->
                    EditorialShelfTrackEntity(
                        shelfId = shelf.id,
                        trackId = track.id,
                        position = index,
                    )
                }
            },
            syncedAtEpochMillis = syncedAtEpochMillis,
        )
    }

    override suspend fun recordRecentlyPlayed(
        userId: String,
        trackId: String,
        playedAtEpochMillis: Long,
    ): Boolean {
        if (userId.isBlank() || trackId.isBlank() || playedAtEpochMillis < 0L) return false
        return dao.recordRecentlyPlayed(
            userId = userId,
            trackId = trackId,
            playedAtEpochMillis = playedAtEpochMillis,
            limit = RECENTLY_PLAYED_LIMIT,
        )
    }

    override suspend fun setPersonalizationEnabled(userId: String, enabled: Boolean): Boolean {
        if (userId.isBlank()) return false
        val current = dao.getPersonalizationPreference(userId)
        dao.upsertPersonalizationPreference(
            PersonalizationPreferenceEntity(
                userId = userId,
                enabled = enabled,
                discoveryMode = current?.discoveryMode ?: DiscoveryMode.Balanced.storageValue,
                updatedAtEpochMillis = currentTimeMillis().coerceAtLeast(0L),
            ),
        )
        return true
    }

    override suspend fun setDiscoveryMode(userId: String, mode: DiscoveryMode): Boolean {
        if (userId.isBlank()) return false
        val current = dao.getPersonalizationPreference(userId)
        dao.upsertPersonalizationPreference(
            PersonalizationPreferenceEntity(
                userId = userId,
                enabled = current?.enabled ?: true,
                discoveryMode = mode.storageValue,
                updatedAtEpochMillis = currentTimeMillis().coerceAtLeast(0L),
            ),
        )
        return true
    }

    override suspend fun setRecommendationHidden(
        userId: String,
        trackId: String,
        hidden: Boolean,
    ): Boolean = updateFeedback(userId, trackId) { it.copy(isHidden = hidden) }

    override suspend fun setTasteSignalExcluded(
        userId: String,
        trackId: String,
        excluded: Boolean,
    ): Boolean = updateFeedback(userId, trackId) { it.copy(excludedFromTaste = excluded) }

    override suspend fun clearPersonalizationData(userId: String): Boolean {
        if (userId.isBlank()) return false
        dao.clearPersonalizationData(userId)
        return true
    }

    private suspend fun updateFeedback(
        userId: String,
        trackId: String,
        transform: (RecommendationFeedbackEntity) -> RecommendationFeedbackEntity,
    ): Boolean {
        if (userId.isBlank() || trackId.isBlank() || !dao.containsTrack(trackId)) return false
        val current = dao.getRecommendationFeedback(userId, trackId)
            ?: RecommendationFeedbackEntity(
                userId = userId,
                trackId = trackId,
                isHidden = false,
                excludedFromTaste = false,
                updatedAtEpochMillis = 0L,
            )
        val updated = transform(current).copy(
            userId = userId,
            trackId = trackId,
            updatedAtEpochMillis = currentTimeMillis().coerceAtLeast(0L),
        )
        if (!updated.isHidden && !updated.excludedFromTaste) {
            dao.deleteRecommendationFeedback(userId, trackId)
        } else {
            dao.upsertRecommendationFeedback(updated)
        }
        return true
    }

    private companion object {
        const val RECENTLY_PLAYED_LIMIT = 20
        const val SIGNAL_LIMIT = 100
        const val MILLIS_PER_DAY = 86_400_000L
    }
}

internal fun CatalogEntitySnapshot.toDomain(): CatalogSnapshot {
    val artistsById = artists.associateBy(ArtistEntity::id)
    val albumsById = albums.associateBy(AlbumEntity::id)
    val tracksById = tracks.associateBy(TrackEntity::id)
    val shelfTracksByShelfId = editorialShelfTracks.groupBy(EditorialShelfTrackEntity::shelfId)
    fun TrackEntity.toDomainTrack(): Track {
        val album = albumsById[albumId]
        val artist = album?.let { artistsById[it.artistId] }
        return Track(
            id = id,
            title = title,
            artist = artist?.name.orEmpty(),
            durationMs = durationMs,
            artistId = artist?.id.orEmpty(),
            albumId = album?.id.orEmpty(),
            albumTitle = album?.title.orEmpty(),
            discNumber = discNumber,
            trackNumber = trackNumber,
            isExplicit = isExplicit,
        )
    }
    return CatalogSnapshot(
        artists = artists.map { Artist(id = it.id, name = it.name) },
        albums = albums.map {
            Album(
                id = it.id,
                artistId = it.artistId,
                title = it.title,
                releaseDate = it.releaseDate,
            )
        },
        tracks = tracks.map { it.toDomainTrack() },
        editorialShelves = editorialShelves.map { shelf ->
            EditorialShelf(
                id = shelf.id,
                title = shelf.title,
                subtitle = shelf.subtitle,
                position = shelf.position,
                tracks = shelfTracksByShelfId[shelf.id]
                    .orEmpty()
                    .sortedBy(EditorialShelfTrackEntity::position)
                    .mapNotNull { tracksById[it.trackId] }
                    .map { it.toDomainTrack() },
                hasCustomArtwork = shelf.hasCustomArtwork,
                cardLabel = shelf.cardLabel,
                colorHex = shelf.colorHex,
                globalScore = shelf.globalScore,
            )
        },
        lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
    )
}

private fun Artist.toEntity() = ArtistEntity(id = id, name = name)

private fun Album.toEntity() = AlbumEntity(
    id = id,
    artistId = artistId,
    title = title,
    releaseDate = releaseDate,
)

private fun Track.toEntity() = TrackEntity(
    id = id,
    albumId = albumId,
    title = title,
    durationMs = durationMs,
    discNumber = discNumber,
    trackNumber = trackNumber,
    isExplicit = isExplicit,
)

private fun EditorialShelf.toEntity() = EditorialShelfEntity(
    id = id,
    title = title,
    subtitle = subtitle,
    position = position,
    hasCustomArtwork = hasCustomArtwork,
    cardLabel = cardLabel,
    colorHex = colorHex,
    globalScore = globalScore,
)
