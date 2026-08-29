package my.id.rakyzumusic.core.playback

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
            .setIsPlayable(true)
            .build(),
    )
    .build()
