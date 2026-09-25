package my.id.rakyzumusic.core.playback

interface PlaybackDependencies {
    val playbackStreamRequestProvider: PlaybackStreamRequestProvider
    val playbackQualityProvider: PlaybackQualityProvider
    val offlinePlaybackAssetProvider: OfflinePlaybackAssetProvider
}
