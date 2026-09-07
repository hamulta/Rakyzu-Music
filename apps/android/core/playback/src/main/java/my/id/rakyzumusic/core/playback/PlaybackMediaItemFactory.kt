package my.id.rakyzumusic.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import my.id.rakyzumusic.core.model.Track

internal fun Track.toPlaybackMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri("$PLAYBACK_SCHEME://$PLAYBACK_HOST/$id")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(albumTitle.takeIf(String::isNotBlank))
            .setDurationMs(durationMs.coerceAtLeast(0L))
            .setIsPlayable(true)
            .setExtras(
                Bundle().apply {
                    putString(PLAYBACK_ARTIST_ID_KEY, artistId)
                    putString(PLAYBACK_ALBUM_ID_KEY, albumId)
                },
            )
            .build(),
    )
    .build()

internal const val PLAYBACK_ARTIST_ID_KEY = "rakyzu.artist_id"
internal const val PLAYBACK_ALBUM_ID_KEY = "rakyzu.album_id"
