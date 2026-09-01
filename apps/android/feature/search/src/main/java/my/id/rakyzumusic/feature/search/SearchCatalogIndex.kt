package my.id.rakyzumusic.feature.search

import java.util.PriorityQueue
import my.id.rakyzumusic.core.model.Artist
import my.id.rakyzumusic.core.model.CatalogSnapshot
import my.id.rakyzumusic.core.model.Track

internal class SearchCatalogIndex private constructor(
    private val artists: List<SearchCandidate<Artist>>,
    private val albums: List<SearchCandidate<SearchAlbumResult>>,
    private val tracks: List<SearchCandidate<Track>>,
) {
    fun search(rawQuery: String): SearchResults {
        val query = rawQuery.normalizedSearchText()
        if (query.isBlank()) return SearchResults()

        val artistMatches = artists.topMatches(query)
        val albumMatches = albums.topMatches(query)
        val trackMatches = tracks.topMatches(query)
        return SearchResults(
            artists = artistMatches.values,
            albums = albumMatches.values,
            tracks = trackMatches.values,
            totalArtistMatches = artistMatches.total,
            totalAlbumMatches = albumMatches.total,
            totalTrackMatches = trackMatches.total,
        )
    }

    companion object {
        fun from(catalog: CatalogSnapshot): SearchCatalogIndex {
            val artistsById = catalog.artists.associateBy(Artist::id)
            return SearchCatalogIndex(
                artists = catalog.artists.map { artist ->
                    SearchCandidate(
                        primary = artist.name.normalizedSearchText(),
                        associated = emptyList(),
                        label = artist.name,
                        id = artist.id,
                        value = artist,
                    )
                },
                albums = catalog.albums.map { album ->
                    val artistName = artistsById[album.artistId]?.name.orEmpty()
                    SearchCandidate(
                        primary = album.title.normalizedSearchText(),
                        associated = listOf(artistName.normalizedSearchText()),
                        label = album.title,
                        id = album.id,
                        value = SearchAlbumResult(album, artistName),
                    )
                },
                tracks = catalog.tracks.map { track ->
                    SearchCandidate(
                        primary = track.title.normalizedSearchText(),
                        associated = listOf(
                            track.artist.normalizedSearchText(),
                            track.albumTitle.orEmpty().normalizedSearchText(),
                        ),
                        label = track.title,
                        id = track.id,
                        value = track,
                    )
                },
            )
        }
    }
}

internal fun CatalogSnapshot.search(rawQuery: String): SearchResults =
    SearchCatalogIndex.from(this).search(rawQuery)

private data class SearchCandidate<T>(
    val primary: String,
    val associated: List<String>,
    val label: String,
    val id: String,
    val value: T,
) {
    fun score(query: String): Int? {
        primary.searchScore(query)?.let { return it }
        var bestAssociatedScore: Int? = null
        for (candidate in associated) {
            val score = candidate.searchScore(query) ?: continue
            if (bestAssociatedScore == null || score < bestAssociatedScore) {
                bestAssociatedScore = score
            }
        }
        return bestAssociatedScore?.plus(ASSOCIATED_MATCH_OFFSET)
    }
}

private data class Ranked<T>(
    val score: Int,
    val label: String,
    val id: String,
    val value: T,
)

private data class BoundedMatches<T>(
    val values: List<T>,
    val total: Int,
)

private fun <T> List<SearchCandidate<T>>.topMatches(query: String): BoundedMatches<T> {
    val best = PriorityQueue<Ranked<T>>(MAX_RESULTS_PER_TYPE, RANKED_ORDER.reversed())
    var total = 0
    for (candidate in this) {
        val score = candidate.score(query) ?: continue
        total += 1
        val ranked = Ranked(score, candidate.label, candidate.id, candidate.value)
        if (best.size < MAX_RESULTS_PER_TYPE) {
            best += ranked
        } else if (RANKED_ORDER.compare(ranked, best.peek()) < 0) {
            best.poll()
            best += ranked
        }
    }
    return BoundedMatches(
        values = best.toList().sortedWith(RANKED_ORDER).map(Ranked<T>::value),
        total = total,
    )
}

private fun String.searchScore(query: String): Int? {
    if (this == query) return 0
    if (startsWith(query)) return 1
    var separator = indexOf(' ')
    while (separator >= 0) {
        val tokenStart = separator + 1
        if (regionMatches(tokenStart, query, 0, query.length)) return 2
        separator = indexOf(' ', tokenStart)
    }
    return if (contains(query)) 3 else null
}

private val RANKED_ORDER = compareBy<Ranked<*>> { it.score }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
    .thenBy { it.id }

private const val ASSOCIATED_MATCH_OFFSET = 4
