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

fun createCatalogLocalDataSource(context: Context): CatalogLocalDataSource {
    val database = RakyzuDatabaseFactory.create(context)
    return RoomCatalogLocalDataSource(database)
}

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
            "sync_metadata",
        )
        .map {
            val catalog = dao.readSnapshot().toDomain()
            val tracksById = catalog.tracks.associateBy(Track::id)
            HomeFeedSnapshot(
                catalog = catalog,
                recentlyPlayed = dao.getRecentlyPlayedTrackIds(userId, RECENTLY_PLAYED_LIMIT)
                    .mapNotNull(tracksById::get),
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
    }
}

private fun CatalogEntitySnapshot.toDomain(): CatalogSnapshot {
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
