import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:rakyzu_music/core/models/app_role.dart';
import 'package:rakyzu_music/features/catalog/data/catalog_exception.dart';
import 'package:rakyzu_music/features/catalog/data/catalog_repository.dart';
import 'package:rakyzu_music/features/catalog/models/album.dart';
import 'package:rakyzu_music/features/catalog/models/artist.dart';
import 'package:rakyzu_music/features/catalog/models/song.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

SupabaseClient _clientWith(MockClient mock) {
  return SupabaseClient(
    'https://dummy-project.supabase.co',
    'dummy-anon-key',
    httpClient: mock,
  );
}

http.Response _json(Object? body, {http.BaseRequest? request}) {
  return http.Response(
    jsonEncode(body),
    200,
    headers: {'content-type': 'application/json'},
    request: request,
  );
}

void main() {
  group('AppRole', () {
    test('fromString maps values & null', () {
      expect(AppRole.fromString('free'), AppRole.free);
      expect(AppRole.fromString('premium'), AppRole.premium);
      expect(AppRole.fromString('staff'), AppRole.staff);
      expect(AppRole.fromString('admin'), AppRole.admin);
      expect(AppRole.fromString('owner'), AppRole.owner);
      expect(AppRole.fromString('unknown'), isNull);
      expect(AppRole.fromString(null), isNull);
    });

    test('canManageCatalog hanya untuk staff/admin/owner', () {
      expect(AppRole.free.canManageCatalog, isFalse);
      expect(AppRole.premium.canManageCatalog, isFalse);
      expect(AppRole.staff.canManageCatalog, isTrue);
      expect(AppRole.admin.canManageCatalog, isTrue);
      expect(AppRole.owner.canManageCatalog, isTrue);
    });

    test('isAdminOrOwner hanya admin/owner', () {
      expect(AppRole.free.isAdminOrOwner, isFalse);
      expect(AppRole.staff.isAdminOrOwner, isFalse);
      expect(AppRole.admin.isAdminOrOwner, isTrue);
      expect(AppRole.owner.isAdminOrOwner, isTrue);
    });
  });

  group('Artist model', () {
    test('fromJson/toJson round-trip', () {
      final artist = Artist.fromJson({
        'id': 'abc-123',
        'name': 'Tulus',
        'bio': 'Penyanyi',
        'image_url': 'https://img/x.jpg',
        'is_verified': true,
        'created_by': 'user-1',
        'created_at': '2026-08-20T10:00:00.000Z',
      });

      expect(artist.id, 'abc-123');
      expect(artist.name, 'Tulus');
      expect(artist.bio, 'Penyanyi');
      expect(artist.isVerified, isTrue);
      expect(artist.createdBy, 'user-1');
      expect(artist.createdAt, isNotNull);

      final json = artist.toJson();
      expect(json['name'], 'Tulus');
      expect(json['is_verified'], isTrue);
      expect(json, isNot(contains('id')));
    });

    test('copyWith', () {
      const a = Artist(id: '1', name: 'A');
      final b = a.copyWith(name: 'B', isVerified: true);
      expect(b.name, 'B');
      expect(b.isVerified, isTrue);
      expect(b.id, '1');
    });
  });

  group('Album model', () {
    test('fromJson maps join fields & releaseDate', () {
      final album = Album.fromJson({
        'id': 'alb-1',
        'title': 'Manusia',
        'artist_id': 'art-1',
        'artist_name': 'Tulus',
        'cover_url': 'https://img/cover.jpg',
        'release_date': '2026-01-01',
        'genre': 'Pop',
        'song_count': 10,
      });

      expect(album.artistName, 'Tulus');
      expect(album.releaseDate, DateTime(2026, 1, 1));
      expect(album.songCount, 10);
    });

    test('toJson memformat release_date sebagai yyyy-MM-dd', () {
      final album = Album(
        id: 'alb-1',
        title: 'T',
        artistId: 'art-1',
        releaseDate: DateTime(2026, 8, 5),
      );
      expect(album.toJson()['release_date'], '2026-08-05');
    });
  });

  group('Song model', () {
    test('fromJson defaults & join fields', () {
      final song = Song.fromJson({
        'id': 's-1',
        'title': 'Hati-hati',
        'album_id': 'alb-1',
        'artist_id': 'art-1',
        'album_title': 'Album',
        'artist_name': 'Artis',
        'duration_seconds': 240,
        'audio_url': 'https://r2/audio/s-1.mp3',
        'play_count': 5,
        'track_number': 3,
      });

      expect(song.albumTitle, 'Album');
      expect(song.artistName, 'Artis');
      expect(song.durationSeconds, 240);
      expect(song.trackNumber, 3);
      expect(song.playCount, 5);
      expect(song.lyrics, isNull);
    });

    test('fromJson default play_count/track_number saat kosong', () {
      final song = Song.fromJson({
        'id': 's-2',
        'title': 'X',
        'album_id': 'alb-2',
        'artist_id': 'art-2',
      });
      expect(song.playCount, 0);
      expect(song.trackNumber, 0);
    });
  });

  group('CatalogException', () {
    test('menerjemahkan pesan RLS ke Bahasa Indonesia', () {
      final e = CatalogException.from(
        const PostgrestException(
          message: 'new row violates row-level security policy',
        ),
      );
      expect(e.message, contains('tidak memiliki izin'));
    });

    test('mempertahankan CatalogException asli', () {
      const original = CatalogException('Asli');
      final e = CatalogException.from(original);
      expect(e, same(original));
    });

    test('fallback ke pesan mentah', () {
      final e = CatalogException.from(Exception('something odd'));
      expect(e.message, contains('something odd'));
    });
  });

  group('CatalogRepository (mock http)', () {
    test('getArtists memetakan baris dan mengirim filter ilike', () async {
      final requests = <Uri>[];
      final mock = MockClient((request) async {
        requests.add(request.url);
        return _json(
          [
            {
              'id': 'art-1',
              'name': 'Tulus',
              'bio': null,
              'image_url': null,
              'is_verified': true,
              'created_by': 'user-1',
              'created_at': '2026-08-20T10:00:00.000Z',
            },
          ],
          request: request,
        );
      });

      final repo = CatalogRepository(_clientWith(mock));
      final artists = await repo.getArtists(search: 'tul');

      expect(artists, hasLength(1));
      expect(artists.first.name, 'Tulus');
      expect(artists.first.isVerified, isTrue);
      final url = requests.first.toString();
      expect(url, contains('/rest/v1/artists'));
      expect(url, contains('name=ilike.%25tul%25'));
    });

    test('getArtist null saat tidak ditemukan', () async {
      final mock = MockClient((request) async => _json(null, request: request));
      final repo = CatalogRepository(_clientWith(mock));
      expect(await repo.getArtist('none'), isNull);
    });

    test('getSongs memetakan join album/artist', () async {
      final mock = MockClient((request) async {
        return _json(
          [
            {
              'id': 's-1',
              'title': 'Lagu',
              'album_id': 'alb-1',
              'artist_id': 'art-1',
              'album': {'title': 'Album'},
              'artist': {'name': 'Artis'},
              'duration_seconds': 180,
              'audio_url': null,
              'cover_url': null,
              'genre': 'Pop',
              'lyrics': null,
              'play_count': 0,
              'track_number': 1,
              'uploaded_by': null,
              'created_at': null,
            },
          ],
          request: request,
        );
      });

      final repo = CatalogRepository(_clientWith(mock));
      final songs = await repo.getSongs(albumId: 'alb-1');

      expect(songs, hasLength(1));
      expect(songs.first.albumTitle, 'Album');
      expect(songs.first.artistName, 'Artis');
      expect(songs.first.genre, 'Pop');
    });

    test('getAlbums memetakan song_count dari relasi songs(count)', () async {
      final mock = MockClient((request) async {
        return _json(
          [
            {
              'id': 'alb-1',
              'title': 'Album',
              'artist_id': 'art-1',
              'artist': {'name': 'Artis'},
              'cover_url': null,
              'release_date': null,
              'genre': null,
              'created_by': null,
              'created_at': null,
              'songs': [
                {'count': 3},
              ],
            },
          ],
          request: request,
        );
      });

      final repo = CatalogRepository(_clientWith(mock));
      final albums = await repo.getAlbums();

      expect(albums, hasLength(1));
      expect(albums.first.artistName, 'Artis');
      expect(albums.first.songCount, 3);
    });

    test('error Postgrest diterjemahkan ke CatalogException', () async {
      final mock = MockClient((request) async {
        return http.Response(
          jsonEncode({'message': 'new row violates row-level security policy'}),
          403,
          headers: {'content-type': 'application/json'},
          request: request,
        );
      });

      final repo = CatalogRepository(_clientWith(mock));
      expect(
        () => repo.createArtist(name: 'X'),
        throwsA(
          isA<CatalogException>().having(
            (e) => e.message,
            'message',
            contains('tidak memiliki izin'),
          ),
        ),
      );
    });
  });
}
