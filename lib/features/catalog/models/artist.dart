/// Model entitas `public.artists`.
class Artist {
  const Artist({
    required this.id,
    required this.name,
    this.bio,
    this.imageUrl,
    this.isVerified = false,
    this.createdBy,
    this.createdAt,
  });

  factory Artist.fromJson(Map<String, dynamic> json) {
    return Artist(
      id: json['id'] as String,
      name: json['name'] as String,
      bio: json['bio'] as String?,
      imageUrl: json['image_url'] as String?,
      isVerified: json['is_verified'] as bool? ?? false,
      createdBy: json['created_by'] as String?,
      createdAt: json['created_at'] != null
          ? DateTime.tryParse(json['created_at'] as String)
          : null,
    );
  }

  final String id;
  final String name;
  final String? bio;
  final String? imageUrl;
  final bool isVerified;
  final String? createdBy;
  final DateTime? createdAt;

  Map<String, dynamic> toJson() {
    return {
      if (name.isNotEmpty) 'name': name,
      if (bio != null) 'bio': bio,
      if (imageUrl != null) 'image_url': imageUrl,
      'is_verified': isVerified,
      if (createdBy != null) 'created_by': createdBy,
    };
  }

  Artist copyWith({
    String? name,
    String? bio,
    String? imageUrl,
    bool? isVerified,
  }) {
    return Artist(
      id: id,
      name: name ?? this.name,
      bio: bio ?? this.bio,
      imageUrl: imageUrl ?? this.imageUrl,
      isVerified: isVerified ?? this.isVerified,
      createdBy: createdBy,
      createdAt: createdAt,
    );
  }
}
