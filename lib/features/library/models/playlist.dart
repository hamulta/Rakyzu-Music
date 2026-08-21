class Playlist {
  const Playlist({
    required this.id,
    required this.userId,
    required this.name,
    this.description,
    this.coverUrl,
    this.isPublic = false,
    this.createdAt,
    this.songCount = 0,
  });

  factory Playlist.fromJson(Map<String, dynamic> json) {
    return Playlist(
      id: json['id'] as String,
      userId: json['user_id'] as String,
      name: json['name'] as String,
      description: json['description'] as String?,
      coverUrl: json['cover_url'] as String?,
      isPublic: json['is_public'] as bool? ?? false,
      createdAt: json['created_at'] != null
          ? DateTime.tryParse(json['created_at'] as String)
          : null,
      songCount: json['song_count'] is int
          ? json['song_count'] as int
          : (json['song_count'] != null
              ? int.tryParse(json['song_count'].toString()) ?? 0
              : 0),
    );
  }

  final String id;
  final String userId;
  final String name;
  final String? description;
  final String? coverUrl;
  final bool isPublic;
  final DateTime? createdAt;
  final int songCount;

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      if (description != null) 'description': description,
      if (coverUrl != null) 'cover_url': coverUrl,
      'is_public': isPublic,
    };
  }

  Playlist copyWith({
    String? name,
    String? description,
    String? coverUrl,
    bool? isPublic,
  }) {
    return Playlist(
      id: id,
      userId: userId,
      name: name ?? this.name,
      description: description ?? this.description,
      coverUrl: coverUrl ?? this.coverUrl,
      isPublic: isPublic ?? this.isPublic,
      createdAt: createdAt,
      songCount: songCount,
    );
  }
}
