package my.id.rakyzumusic.core.database.catalog

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.HomeFeedSnapshot
import my.id.rakyzumusic.core.model.Track

interface CatalogLocalDataSource {
    fun observeCatalog(): Flow<CatalogSnapshot>

    fun observeHomeFeed(userId: String): Flow<HomeFeedSnapshot>

    suspend fun replaceCatalog(snapshot: CatalogSnapshot, syncedAtEpochMillis: Long)

    suspend fun recordRecentlyPlayed(userId: String, trackId: String, playedAtEpochMillis: Long): Boolean
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
            "sync_metadata",
        )
        .map {
            val catalog = dao.readSnapshot().toDomain()
            val tracksById = catalog.tracks.associateBy(Track::id)
            val recentIds = dao.getRecentlyPlayedTrackIds(userId, RECENTLY_PLAYED_LIMIT)
            HomeFeedSnapshot(
                catalog = catalog,
                recentlyPlayed = recentIds.mapNotNull(tracksById::get),
                smartRecommendations = rankSmartRecommendations(
                    tracks = catalog.tracks,
                    recentTrackIds = recentIds,
                    likedTrackIds = dao.getLikedTrackIds(userId, SIGNAL_LIMIT),
                    savedAlbumIds = dao.getSavedAlbumIds(userId, SIGNAL_LIMIT),
                    followedArtistIds = dao.getFollowedArtistIds(userId, SIGNAL_LIMIT),
                ),
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

    private companion object {
        const val RECENTLY_PLAYED_LIMIT = 20
        const val SIGNAL_LIMIT = 100
    }
}

internal fun rankSmartRecommendations(
    tracks: List<Track>,
    recentTrackIds: List<String>,
    likedTrackIds: List<String>,
    savedAlbumIds: List<String>,
    followedArtistIds: List<String>,
    limit: Int = 20,
): List<Track> {
    if (limit <= 0 || tracks.isEmpty()) return emptyList()
    val tracksById = tracks.associateBy(Track::id)
    val recentIds = recentTrackIds.toSet()
    val likedIds = likedTrackIds.toSet()
    val savedAlbums = savedAlbumIds.toSet()
    val followedArtists = followedArtistIds.toSet()
    val recentTracks = recentTrackIds.mapNotNull(tracksById::get)
    val affinityArtists = recentTracks.mapTo(mutableSetOf(), Track::artistId) +
        likedTrackIds.mapNotNull(tracksById::get).map(Track::artistId)
    val affinityAlbums = recentTracks.mapTo(mutableSetOf(), Track::albumId)
    if (recentIds.isEmpty() && likedIds.isEmpty() && savedAlbums.isEmpty() &&
        followedArtists.isEmpty()
    ) return emptyList()

    val ranked = tracks.asSequence()
        .filterNot { it.id in recentIds || it.id in likedIds }
        .map { track ->
            val score =
                (if (track.artistId in followedArtists) 12 else 0) +
                    (if (track.albumId in savedAlbums) 10 else 0) +
                    (if (track.artistId in affinityArtists) 6 else 0) +
                    (if (track.albumId in affinityAlbums) 4 else 0)
            track to score
        }
        .filter { (_, score) -> score > 0 }
        .sortedWith(compareByDescending<Pair<Track, Int>> { it.second }.thenBy { it.first.id })

    val artistCounts = mutableMapOf<String, Int>()
    return ranked.mapNotNull { (track, _) ->
        val artistCount = artistCounts[track.artistId] ?: 0
        if (artistCount >= MAX_RECOMMENDATIONS_PER_ARTIST) null else {
            artistCounts[track.artistId] = artistCount + 1
            track
        }
    }.take(limit.coerceAtMost(MAX_SMART_RECOMMENDATIONS)).toList()
}

private const val MAX_RECOMMENDATIONS_PER_ARTIST = 2
private const val MAX_SMART_RECOMMENDATIONS = 20

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
)
