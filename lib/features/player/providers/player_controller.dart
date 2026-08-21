import 'dart:async';

import 'package:audio_service/audio_service.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:just_audio/just_audio.dart';

import '../../catalog/data/r2_storage_service.dart';
import '../../catalog/models/song.dart';
import 'audio_handler.dart';

/// Repeat mode untuk pemutar.
enum RepeatMode { off, all, one }

/// State pemutar audio global.
class PlaybackState {
  const PlaybackState({
    this.currentTrack,
    this.queue = const [],
    this.position = Duration.zero,
    this.duration = Duration.zero,
    this.isPlaying = false,
    this.shuffleEnabled = false,
    this.repeatMode = RepeatMode.off,
    this.isLoading = false,
    this.error,
  });

  final Song? currentTrack;
  final List<Song> queue;
  final Duration position;
  final Duration duration;
  final bool isPlaying;
  final bool shuffleEnabled;
  final RepeatMode repeatMode;
  final bool isLoading;
  final String? error;

  bool get hasTrack => currentTrack != null;
  bool get hasQueue => queue.isNotEmpty;

  /// Index currentTrack dalam queue (0-based, -1 jika tidak ada).
  int get currentIndex {
    if (currentTrack == null) return -1;
    return queue.indexWhere((s) => s.id == currentTrack!.id);
  }

  /// Sisa waktu (duration - position), 0 jika tidak diketahui.
  Duration get remaining =>
      duration > position ? duration - position : Duration.zero;

  PlaybackState copyWith({
    Song? currentTrack,
    bool clearCurrentTrack = false,
    List<Song>? queue,
    Duration? position,
    Duration? duration,
    bool? isPlaying,
    bool? shuffleEnabled,
    RepeatMode? repeatMode,
    bool? isLoading,
    String? error,
    bool clearError = false,
  }) {
    return PlaybackState(
      currentTrack:
          clearCurrentTrack ? null : (currentTrack ?? this.currentTrack),
      queue: queue ?? this.queue,
      position: position ?? this.position,
      duration: duration ?? this.duration,
      isPlaying: isPlaying ?? this.isPlaying,
      shuffleEnabled: shuffleEnabled ?? this.shuffleEnabled,
      repeatMode: repeatMode ?? this.repeatMode,
      isLoading: isLoading ?? this.isLoading,
      error: clearError ? null : (error ?? this.error),
    );
  }
}

/// Single source of truth untuk playback audio di seluruh app.
///
/// Mengelola AudioPlayer instance, queue, shuffle/repeat, dan state.
/// Signed URL di-refresh otomatis sebelum expiry.
class PlayerController extends StateNotifier<PlaybackState> {
  PlayerController(this._r2Storage) : super(const PlaybackState()) {
    _init();
  }

  final R2StorageService _r2Storage;
  final AudioPlayer _player = AudioPlayer();

  /// Audio handler untuk background playback (audio_service).
  /// Di-set via `setAudioHandler()` setelah AudioService.init() selesai.
  RakyzuAudioHandler? _audioHandler;

  /// Subscribe ke streams AudioPlayer.
  StreamSubscription<Duration>? _positionSub;
  StreamSubscription<Duration?>? _durationSub;
  StreamSubscription<PlayerState>? _playerStateSub;
  StreamSubscription<ProcessingState>? _processingSub;

  /// Timer untuk refresh signed URL sebelum expiry (default 10 min, refresh di 8 min).
  Timer? _refreshTimer;

  /// Cache signed URL per audioKey.
  final Map<String, _CachedUrl> _urlCache = {};

  AudioPlayer get player => _player;

  /// Set audio handler untuk background playback.
  /// Panggil setelah AudioService.init() di main.dart.
  // ignore: use_setters_to_change_properties
  void setAudioHandler(RakyzuAudioHandler handler) {
    _audioHandler = handler;
  }

  /// Sinkronkan queue + current track ke AudioHandler untuk notification/lock screen.
  void _syncToAudioHandler() {
    if (_audioHandler == null) return;

    // Sync queue.
    final mediaItems = state.queue.map(_songToMediaItem).toList();
    _audioHandler!.updateQueueFromPlayer(mediaItems);

    // Sync current track.
    if (state.currentTrack != null) {
      _audioHandler!
          .updateMediaItemFromPlayer(_songToMediaItem(state.currentTrack!));
    }
  }

  MediaItem _songToMediaItem(Song song) {
    return MediaItem(
      id: song.audioUrl ?? 'unknown',
      title: song.title,
      artist: song.artistName ?? 'Unknown Artist',
      album: song.albumTitle ?? '',
      duration: state.duration,
      artUri:
          song.coverUrl != null ? Uri.parse('https://${song.coverUrl}') : null,
    );
  }

  void _init() {
    _positionSub = _player.positionStream.listen((pos) {
      if (mounted) {
        state = state.copyWith(position: pos);
        _checkPlayCountThreshold(pos);
      }
    });

    _durationSub = _player.durationStream.listen((dur) {
      if (mounted && dur != null) {
        state = state.copyWith(duration: dur);
      }
    });

    _playerStateSub = _player.playerStateStream.listen((ps) {
      if (!mounted) return;
      state = state.copyWith(
        isPlaying: ps.playing,
        isLoading: ps.processingState == ProcessingState.loading ||
            ps.processingState == ProcessingState.buffering,
      );
    });

    _processingSub = _player.processingStateStream.listen((processing) {
      if (!mounted) return;
      if (processing == ProcessingState.completed) {
        _onTrackComplete();
      }
    });
  }

  // ---------------------------------------------------------------------------
  // Playback API
  // ---------------------------------------------------------------------------

  /// Mainkan satu lagu (solo, clear queue).
  Future<void> playSingle(Song song) async {
    state = state.copyWith(
      isLoading: true,
      clearError: true,
    );
    try {
      final url = await _getSignedUrl(song.audioUrl!);
      await _player.setUrl(url);
      await _player.play();
      state = state.copyWith(
        currentTrack: song,
        queue: [song],
        isLoading: false,
      );
      _startRefreshTimer(song.audioUrl!);
      _syncToAudioHandler();
    } on Object catch (e) {
      state = state.copyWith(
        isLoading: false,
        error: 'Gagal memutar: $e',
      );
    }
  }

  /// Mainkan daftar lagu dari index tertentu.
  Future<void> playFromQueue(List<Song> songs, {int startIndex = 0}) async {
    if (songs.isEmpty || startIndex < 0 || startIndex >= songs.length) return;

    state = state.copyWith(
      isLoading: true,
      clearError: true,
    );

    final target = songs[startIndex];
    try {
      final url = await _getSignedUrl(target.audioUrl!);
      await _player.setUrl(url);
      await _player.play();
      state = state.copyWith(
        currentTrack: target,
        queue: songs,
        isLoading: false,
      );
      _startRefreshTimer(target.audioUrl!);
      _syncToAudioHandler();
    } on Object catch (e) {
      state = state.copyWith(
        isLoading: false,
        error: 'Gagal memutar: $e',
      );
    }
  }

  /// Toggle play/pause.
  Future<void> togglePlay() async {
    if (!state.hasTrack) return;
    if (_player.playing) {
      await _player.pause();
    } else {
      await _player.play();
    }
  }

  /// Pause.
  Future<void> pause() async {
    await _player.pause();
  }

  /// Resume.
  Future<void> resume() async {
    await _player.play();
  }

  /// Stop playback.
  Future<void> stop() async {
    await _player.stop();
    _cancelRefreshTimer();
    state = const PlaybackState();
  }

  /// Seek ke posisi.
  Future<void> seek(Duration position) async {
    await _player.seek(position);
  }

  /// Skip ke lagu berikutnya.
  Future<void> skipNext() async {
    if (!state.hasQueue) return;

    final idx = state.currentIndex;
    if (idx < 0) return;

    final nextIdx = idx + 1;
    if (nextIdx < state.queue.length) {
      await _playTrackAtIndex(nextIdx);
    } else if (state.repeatMode == RepeatMode.all) {
      await _playTrackAtIndex(0);
    }
  }

  /// Skip ke lagu sebelumnya.
  Future<void> skipPrevious() async {
    if (!state.hasQueue) return;

    final idx = state.currentIndex;
    if (idx <= 0) {
      // Restart current track if at beginning.
      await seek(Duration.zero);
      return;
    }

    await _playTrackAtIndex(idx - 1);
  }

  /// Skip ke lagu spesifik dalam queue.
  Future<void> skipToIndex(int index) async {
    if (index < 0 || index >= state.queue.length) return;
    await _playTrackAtIndex(index);
  }

  // ---------------------------------------------------------------------------
  // Queue management
  // ---------------------------------------------------------------------------

  /// Tambahkan lagu ke akhir queue.
  void addToQueue(Song song) {
    final newQueue = [...state.queue, song];
    state = state.copyWith(queue: newQueue);
  }

  /// Masukkan lagu setelah lagu yang sedang diputar (Play Next).
  void playNext(Song song) {
    final newQueue = List<Song>.from(state.queue);
    final insertIdx = state.currentIndex + 1;
    newQueue.insert(insertIdx.clamp(0, newQueue.length), song);
    state = state.copyWith(queue: newQueue);
  }

  /// Hapus lagu dari queue.
  void removeFromQueue(int index) {
    if (index < 0 || index >= state.queue.length) return;

    final newQueue = List<Song>.from(state.queue);
    final removed = newQueue.removeAt(index);

    // Jika yang dihapus adalah track yang sedang diputar, skip next.
    if (removed.id == state.currentTrack?.id) {
      if (newQueue.isEmpty) {
        stop();
        return;
      }
      final nextIdx = index.clamp(0, newQueue.length - 1);
      state = state.copyWith(queue: newQueue);
      _playTrackAtIndex(nextIdx);
    } else {
      state = state.copyWith(queue: newQueue);
    }
  }

  /// Reorder lagu dalam queue (drag-drop).
  void reorderQueue(int oldIndex, int newIndex) {
    if (oldIndex < 0 || oldIndex >= state.queue.length) return;
    if (newIndex < 0 || newIndex >= state.queue.length) return;

    final newQueue = List<Song>.from(state.queue);
    final item = newQueue.removeAt(oldIndex);
    newQueue.insert(newIndex, item);
    state = state.copyWith(queue: newQueue);
  }

  /// Clear queue dan stop.
  void clearQueue() {
    stop();
  }

  // ---------------------------------------------------------------------------
  // Shuffle & Repeat
  // ---------------------------------------------------------------------------

  /// Toggle shuffle. Saat aktif, queue diacak (track current tetap di posisi 0).
  void toggleShuffle() {
    final newShuffle = !state.shuffleEnabled;
    if (newShuffle && state.hasQueue) {
      final current = state.currentTrack;
      final others = state.queue.where((s) => s.id != current?.id).toList();
      others.shuffle();
      final newQueue = <Song>[
        if (current != null) current,
        ...others,
      ];
      state = state.copyWith(
        shuffleEnabled: true,
        queue: newQueue,
      );
    } else {
      state = state.copyWith(shuffleEnabled: newShuffle);
    }
  }

  /// Cycle repeat: off → all → one → off.
  void toggleRepeat() {
    final next = switch (state.repeatMode) {
      RepeatMode.off => RepeatMode.all,
      RepeatMode.all => RepeatMode.one,
      RepeatMode.one => RepeatMode.off,
    };
    state = state.copyWith(repeatMode: next);

    // Set loop mode on just_audio player.
    _player.setLoopMode(
      switch (next) {
        RepeatMode.off => LoopMode.off,
        RepeatMode.all => LoopMode.all,
        RepeatMode.one => LoopMode.one,
      },
    );
  }

  // ---------------------------------------------------------------------------
  // Signed URL management
  // ---------------------------------------------------------------------------

  Future<String> _getSignedUrl(String key) async {
    final cached = _urlCache[key];
    if (cached != null && !cached.isExpired) {
      return cached.url;
    }

    final url = await _r2Storage.getReadUrl(key, expires: 3600);
    _urlCache[key] = _CachedUrl(url: url, obtainedAt: DateTime.now());
    return url;
  }

  void _startRefreshTimer(String audioKey) {
    _cancelRefreshTimer();
    // Refresh setelah 50 menit (URL expiry 60 min, refresh sebelum expiry).
    _refreshTimer = Timer(const Duration(minutes: 50), () async {
      if (!mounted) return;
      try {
        final url = await _getSignedUrl(audioKey);
        // Re-load source dengan URL baru tanpa restart playback.
        final currentPosition = _player.position;
        await _player.setUrl(url);
        await _player.seek(currentPosition);
      } on Object catch (_) {
        // Silently retry on next play.
      }
    });
  }

  void _cancelRefreshTimer() {
    _refreshTimer?.cancel();
    _refreshTimer = null;
  }

  // ---------------------------------------------------------------------------
  // Internal
  // ---------------------------------------------------------------------------

  Future<void> _playTrackAtIndex(int index) async {
    if (index < 0 || index >= state.queue.length) return;

    final track = state.queue[index];
    if (track.audioUrl == null) return;

    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final url = await _getSignedUrl(track.audioUrl!);
      await _player.setUrl(url);
      await _player.play();
      state = state.copyWith(
        currentTrack: track,
        isLoading: false,
      );
      _startRefreshTimer(track.audioUrl!);
      _syncToAudioHandler();
    } on Object catch (e) {
      state = state.copyWith(
        isLoading: false,
        error: 'Gagal memutar: $e',
      );
    }
  }

  void _onTrackComplete() {
    if (!state.hasTrack) return;

    switch (state.repeatMode) {
      case RepeatMode.one:
      // just_audio handles LoopMode.one automatically.
      case RepeatMode.all:
        skipNext();
      case RepeatMode.off:
        final idx = state.currentIndex;
        if (idx < state.queue.length - 1) {
          skipNext();
        } else {
          // Last track, stop.
          state = state.copyWith(isPlaying: false);
        }
    }
  }

  void _checkPlayCountThreshold(Duration position) {
    // Placeholder for v0.3.8 (play count tracking).
  }

  @override
  void dispose() {
    _positionSub?.cancel();
    _durationSub?.cancel();
    _playerStateSub?.cancel();
    _processingSub?.cancel();
    _cancelRefreshTimer();
    _player.dispose();
    super.dispose();
  }
}

class _CachedUrl {
  const _CachedUrl({required this.url, required this.obtainedAt});

  final String url;
  final DateTime obtainedAt;

  /// Consider expired after 55 minutes (URL valid 60 min).
  bool get isExpired => DateTime.now().difference(obtainedAt).inMinutes >= 55;
}
