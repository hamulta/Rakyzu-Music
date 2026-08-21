import 'dart:async';

/// Layanan hitung skip untuk Free tier.
/// Reset otomatis tiap 1 jam (local state, MVP — tidak perlu server di 0.6.x).
class SkipLimitService {
  SkipLimitService({this.limitPerHour = 6});

  final int limitPerHour;

  int _count = 0;
  DateTime _windowStart = DateTime.now();
  Timer? _resetTimer;

  int get count => _count;
  int get remaining => (limitPerHour - _count).clamp(0, limitPerHour);
  bool get isLimitReached => _count >= limitPerHour;
  Duration get timeUntilReset {
    final elapsed = DateTime.now().difference(_windowStart);
    const window = Duration(hours: 1);
    if (elapsed >= window) return Duration.zero;
    return window - elapsed;
  }

  bool _isWindowExpired() {
    return DateTime.now().difference(_windowStart).inHours >= 1;
  }

  void _ensureWindow() {
    if (_isWindowExpired()) {
      _count = 0;
      _windowStart = DateTime.now();
      _resetTimer?.cancel();
    }
  }

  /// Coba konsumsi 1 skip. Return true jika berhasil, false jika limit tercapai.
  bool tryConsume() {
    _ensureWindow();
    if (isLimitReached) return false;
    _count++;
    // Schedule reset jika belum ada.
    _resetTimer ??= Timer(const Duration(hours: 1), () {
      _count = 0;
      _windowStart = DateTime.now();
      _resetTimer = null;
    });
    return true;
  }

  void reset() {
    _count = 0;
    _windowStart = DateTime.now();
    _resetTimer?.cancel();
    _resetTimer = null;
  }

  void dispose() {
    _resetTimer?.cancel();
  }
}
