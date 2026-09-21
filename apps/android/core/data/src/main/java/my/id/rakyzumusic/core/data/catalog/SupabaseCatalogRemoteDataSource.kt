package my.id.rakyzumusic.core.data.catalog

import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import my.id.rakyzumusic.core.model.Album
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.EditorialShelf
import my.id.rakyzumusic.core.model.Track

internal class SupabaseCatalogRemoteDataSource(
    private val postgrest: Postgrest,
) : CatalogRemoteDataSource {
    override suspend fun fetchCatalog(): CatalogSnapshot {
        val artistRows = postgrest[ARTISTS_TABLE]
            .select(ARTIST_COLUMNS)
            .decodeList<ArtistRow>()
        val albumRows = postgrest[ALBUMS_TABLE]
            .select(ALBUM_COLUMNS)
            .decodeList<AlbumRow>()
        val trackRows = postgrest[TRACKS_TABLE]
            .select(TRACK_COLUMNS)
            .decodeList<TrackRow>()
        val shelfRows = postgrest[EDITORIAL_SHELVES_TABLE]
            .select(EDITORIAL_SHELF_COLUMNS)
            .decodeList<EditorialShelfRow>()
        val shelfTrackRows = postgrest[EDITORIAL_SHELF_TRACKS_TABLE]
            .select(EDITORIAL_SHELF_TRACK_COLUMNS)
            .decodeList<EditorialShelfTrackRow>()

        val artists = artistRows
            .map { Artist(id = it.id, name = it.name) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Artist::name).thenBy(Artist::id))
        val albums = albumRows
            .map { Album(id = it.id, artistId = it.artistId, title = it.title, releaseDate = it.releaseDate) }
            .sortedWith(compareByDescending<Album> { it.releaseDate }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        val artistsById = artists.associateBy(Artist::id)
        val albumsById = albums.associateBy(Album::id)
        val tracks = trackRows.map { row ->
            val album = albumsById[row.albumId] ?: throw InvalidCatalogPayloadException()
            val artist = artistsById[album.artistId] ?: throw InvalidCatalogPayloadException()
            Track(
                id = row.id,
                title = row.title,
                artist = artist.name,
                durationMs = row.durationMs,
                artistId = artist.id,
                albumId = album.id,
                albumTitle = album.title,
                discNumber = row.discNumber,
                trackNumber = row.trackNumber,
                isExplicit = row.isExplicit,
            )
        }.sortedWith(compareBy(Track::albumId, Track::discNumber, Track::trackNumber, Track::id))
        val tracksById = tracks.associateBy(Track::id)
        val shelfIds = shelfRows.mapTo(mutableSetOf(), EditorialShelfRow::id)
        if (shelfTrackRows.any { it.shelfId !in shelfIds }) {
            throw InvalidCatalogPayloadException()
        }
        val shelfTracksByShelfId = shelfTrackRows.groupBy(EditorialShelfTrackRow::shelfId)
        val editorialShelves = shelfRows.map { row ->
            EditorialShelf(
                id = row.id,
                title = row.title,
                subtitle = row.subtitle,
                position = row.position,
                tracks = shelfTracksByShelfId[row.id]
                    .orEmpty()
                    .sortedWith(compareBy(EditorialShelfTrackRow::position, EditorialShelfTrackRow::trackId))
                    .map { tracksById[it.trackId] ?: throw InvalidCatalogPayloadException() },
                hasCustomArtwork = row.artworkObjectKey != null,
            )
        }.sortedWith(compareBy(EditorialShelf::position, EditorialShelf::id))

        return CatalogSnapshot(
            artists = artists,
            albums = albums,
            tracks = tracks,
            editorialShelves = editorialShelves,
            lastSyncedAtEpochMillis = null,
        )
    }

    override suspend fun searchCatalog(
        query: String,
        offset: Int,
        limit: Int,
    ): CatalogSearchPage {
        val rows = postgrest.rpc(
            function = SEARCH_CATALOG_FUNCTION,
            parameters = buildJsonObject {
                put("search_query", query)
                put("page_offset", offset)
                put("page_limit", limit)
            },
        ).decodeList<CatalogSearchRow>()

        val artists = rows.filter { it.kind == SEARCH_KIND_ARTIST }.map { row ->
            Artist(id = row.id, name = row.title)
        }
        val albums = rows.filter { it.kind == SEARCH_KIND_ALBUM }.map { row ->
            CatalogSearchAlbum(
                album = Album(
                    id = row.id,
                    artistId = row.artistId ?: throw InvalidCatalogPayloadException(),
                    title = row.title,
                    releaseDate = row.releaseDate,
                ),
                artistName = row.artistName.orEmpty(),
            )
        }
        val tracks = rows.filter { it.kind == SEARCH_KIND_TRACK }.map { row ->
            Track(
                id = row.id,
                title = row.title,
                artist = row.artistName ?: throw InvalidCatalogPayloadException(),
                durationMs = row.durationMs ?: throw InvalidCatalogPayloadException(),
                artistId = row.artistId ?: throw InvalidCatalogPayloadException(),
                albumId = row.albumId ?: throw InvalidCatalogPayloadException(),
                albumTitle = row.albumTitle ?: throw InvalidCatalogPayloadException(),
                discNumber = row.discNumber ?: throw InvalidCatalogPayloadException(),
                trackNumber = row.trackNumber ?: throw InvalidCatalogPayloadException(),
                isExplicit = row.isExplicit ?: false,
            )
        }
        if (artists.size + albums.size + tracks.size != rows.size) {
            throw InvalidCatalogPayloadException()
        }
        val totalCount = rows.firstOrNull()?.totalCount ?: 0
        val displayedCount = rows.size
        return CatalogSearchPage(
            artists = artists,
            albums = albums,
            tracks = tracks,
            totalCount = totalCount,
            nextOffset = (offset + displayedCount).takeIf { displayedCount > 0 && it < totalCount },
        )
    }

    private companion object {
        const val ARTISTS_TABLE = "artists"
        const val ALBUMS_TABLE = "albums"
        const val TRACKS_TABLE = "tracks"
        const val EDITORIAL_SHELVES_TABLE = "editorial_shelves"
        const val EDITORIAL_SHELF_TRACKS_TABLE = "editorial_shelf_tracks"
        const val SEARCH_CATALOG_FUNCTION = "search_catalog"
        const val SEARCH_KIND_ARTIST = "artist"
        const val SEARCH_KIND_ALBUM = "album"
        const val SEARCH_KIND_TRACK = "track"
        val ARTIST_COLUMNS = Columns.list("id", "name")
        val ALBUM_COLUMNS = Columns.list("id", "artist_id", "title", "release_date")
        val TRACK_COLUMNS = Columns.list(
            "id",
            "album_id",
            "title",
            "duration_ms",
            "disc_number",
            "track_number",
            "is_explicit",
        )
        val EDITORIAL_SHELF_COLUMNS = Columns.list(
            "id", "title", "subtitle", "position", "artwork_object_key",
        )
        val EDITORIAL_SHELF_TRACK_COLUMNS = Columns.list("shelf_id", "track_id", "position")
    }
}

@Serializable
private data class CatalogSearchRow(
    val kind: String,
    val id: String,
    val title: String,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("album_title") val albumTitle: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("disc_number") val discNumber: Int? = null,
    @SerialName("track_number") val trackNumber: Int? = null,
    @SerialName("is_explicit") val isExplicit: Boolean? = null,
    @SerialName("total_count") val totalCount: Int,
)

@Serializable
private data class ArtistRow(
    val id: String,
    val name: String,
)

@Serializable
private data class AlbumRow(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    val title: String,
    @SerialName("release_date") val releaseDate: String?,
)

@Serializable
private data class TrackRow(
    val id: String,
    @SerialName("album_id") val albumId: String,
    val title: String,
    @SerialName("duration_ms") val durationMs: Long,
    @SerialName("disc_number") val discNumber: Int,
    @SerialName("track_number") val trackNumber: Int,
    @SerialName("is_explicit") val isExplicit: Boolean,
)

@Serializable
private data class EditorialShelfRow(
    val id: String,
    val title: String,
    val subtitle: String?,
    val position: Int,
    @SerialName("artwork_object_key") val artworkObjectKey: String? = null,
)

@Serializable
private data class EditorialShelfTrackRow(
    @SerialName("shelf_id") val shelfId: String,
    @SerialName("track_id") val trackId: String,
    val position: Int,
)
