package my.id.rakyzumusic.core.database.catalog

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track

interface CatalogLocalDataSource {
    fun observeCatalog(): Flow<CatalogSnapshot>

    suspend fun replaceCatalog(snapshot: CatalogSnapshot, syncedAtEpochMillis: Long)
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
        .createFlow("artists", "albums", "tracks", "sync_metadata")
        .map { dao.readSnapshot().toDomain() }

    override suspend fun replaceCatalog(snapshot: CatalogSnapshot, syncedAtEpochMillis: Long) {
        dao.replaceCatalog(
            artists = snapshot.artists.map(Artist::toEntity),
            albums = snapshot.albums.map(Album::toEntity),
            tracks = snapshot.tracks.map(Track::toEntity),
            syncedAtEpochMillis = syncedAtEpochMillis,
        )
    }
}

private fun CatalogEntitySnapshot.toDomain(): CatalogSnapshot {
    val artistsById = artists.associateBy(ArtistEntity::id)
    val albumsById = albums.associateBy(AlbumEntity::id)
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
        tracks = tracks.map { track ->
            val album = albumsById[track.albumId]
            val artist = album?.let { artistsById[it.artistId] }
            Track(
                id = track.id,
                title = track.title,
                artist = artist?.name.orEmpty(),
                durationMs = track.durationMs,
                artistId = artist?.id.orEmpty(),
                albumId = album?.id.orEmpty(),
                albumTitle = album?.title.orEmpty(),
                discNumber = track.discNumber,
                trackNumber = track.trackNumber,
                isExplicit = track.isExplicit,
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
