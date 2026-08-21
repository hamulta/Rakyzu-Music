/// Model entitas `public.genres`.
class Genre {
  const Genre({
    required this.id,
    required this.name,
    this.iconUrl,
  });

  factory Genre.fromJson(Map<String, dynamic> json) {
    return Genre(
      id: json['id'] as String,
      name: json['name'] as String,
      iconUrl: json['icon_url'] as String?,
    );
  }

  final String id;
  final String name;
  final String? iconUrl;
}
