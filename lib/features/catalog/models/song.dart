/// Model entitas `public.songs`.
///
/// `albumTitle` & `artistName` hanya terisi saat query join (opsional).
class Song {
  const Song({
    required this.id,
    required this.title,
    required this.albumId,
    required this.artistId,
    this.albumTitle,
    this.artistName,
    this.durationSeconds,
    this.audioUrl,
    this.coverUrl,
    this.genre,
    this.lyrics,
    this.playCount = 0,
    this.trackNumber = 0,
    this.uploadedBy,
    this.createdAt,
    this.isPublished = true,
  });

  factory Song.fromJson(Map<String, dynamic> json) {
    return Song(
      id: json['id'] as String,
      title: json['title'] as String,
      albumId: json['album_id'] as String? ?? '',
      artistId: json['artist_id'] as String? ?? '',
      albumTitle: json['album_title'] as String?,
      artistName: json['artist_name'] as String?,
      durationSeconds: json['duration_seconds'] as int?,
      audioUrl: json['audio_url'] as String?,
      coverUrl: json['cover_url'] as String?,
      genre: json['genre'] as String?,
      lyrics: json['lyrics'] as String?,
      playCount: json['play_count'] as int? ?? 0,
      trackNumber: json['track_number'] as int? ?? 0,
      uploadedBy: json['uploaded_by'] as String?,
      createdAt: json['created_at'] != null
          ? DateTime.tryParse(json['created_at'] as String)
          : null,
      isPublished: json['is_published'] as bool? ?? true,
    );
  }

  final String id;
  final String title;
  final String albumId;
  final String artistId;
  final String? albumTitle;
  final String? artistName;
  final int? durationSeconds;
  final String? audioUrl;
  final String? coverUrl;
  final String? genre;
  final String? lyrics;
  final int playCount;
  final int trackNumber;
  final String? uploadedBy;
  final DateTime? createdAt;
  final bool isPublished;

  Map<String, dynamic> toJson() {
    return {
      'title': title,
      'album_id': albumId,
      'artist_id': artistId,
      if (durationSeconds != null) 'duration_seconds': durationSeconds,
      if (audioUrl != null) 'audio_url': audioUrl,
      if (coverUrl != null) 'cover_url': coverUrl,
      if (genre != null) 'genre': genre,
      if (lyrics != null) 'lyrics': lyrics,
      'track_number': trackNumber,
      'is_published': isPublished,
      if (uploadedBy != null) 'uploaded_by': uploadedBy,
    };
  }

  Song copyWith({
    String? title,
    String? albumId,
    String? artistId,
    int? durationSeconds,
    String? audioUrl,
    String? coverUrl,
    String? genre,
    String? lyrics,
    int? trackNumber,
    bool? isPublished,
  }) {
    return Song(
      id: id,
      title: title ?? this.title,
      albumId: albumId ?? this.albumId,
      artistId: artistId ?? this.artistId,
      albumTitle: albumTitle,
      artistName: artistName,
      durationSeconds: durationSeconds ?? this.durationSeconds,
      audioUrl: audioUrl ?? this.audioUrl,
      coverUrl: coverUrl ?? this.coverUrl,
      genre: genre ?? this.genre,
      lyrics: lyrics ?? this.lyrics,
      playCount: playCount,
      trackNumber: trackNumber ?? this.trackNumber,
      uploadedBy: uploadedBy,
      createdAt: createdAt,
      isPublished: isPublished ?? this.isPublished,
    );
  }
}
