/// Model entitas `public.albums`.
///
/// `artistName` & `songCount` hanya terisi saat query join (opsional).
class Album {
  const Album({
    required this.id,
    required this.title,
    required this.artistId,
    this.artistName,
    this.coverUrl,
    this.releaseDate,
    this.genre,
    this.createdBy,
    this.createdAt,
    this.songCount,
  });

  factory Album.fromJson(Map<String, dynamic> json) {
    return Album(
      id: json['id'] as String,
      title: json['title'] as String,
      artistId: json['artist_id'] as String? ?? '',
      artistName: json['artist_name'] as String?,
      coverUrl: json['cover_url'] as String?,
      releaseDate: json['release_date'] != null
          ? DateTime.tryParse(json['release_date'] as String)
          : null,
      genre: json['genre'] as String?,
      createdBy: json['created_by'] as String?,
      createdAt: json['created_at'] != null
          ? DateTime.tryParse(json['created_at'] as String)
          : null,
      songCount: json['song_count'] as int?,
    );
  }

  final String id;
  final String title;
  final String artistId;
  final String? artistName;
  final String? coverUrl;
  final DateTime? releaseDate;
  final String? genre;
  final String? createdBy;
  final DateTime? createdAt;
  final int? songCount;

  Map<String, dynamic> toJson() {
    return {
      'title': title,
      'artist_id': artistId,
      if (coverUrl != null) 'cover_url': coverUrl,
      if (releaseDate != null)
        'release_date': releaseDate!.toIso8601String().split('T').first,
      if (genre != null) 'genre': genre,
      if (createdBy != null) 'created_by': createdBy,
    };
  }

  Album copyWith({
    String? title,
    String? artistId,
    String? coverUrl,
    DateTime? releaseDate,
    String? genre,
  }) {
    return Album(
      id: id,
      title: title ?? this.title,
      artistId: artistId ?? this.artistId,
      artistName: artistName,
      coverUrl: coverUrl ?? this.coverUrl,
      releaseDate: releaseDate ?? this.releaseDate,
      genre: genre ?? this.genre,
      createdBy: createdBy,
      createdAt: createdAt,
      songCount: songCount,
    );
  }
}
