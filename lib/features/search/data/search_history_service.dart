import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// Local search history storage using SharedPreferences.
/// History is stored per-device, not synced to Supabase.
class SearchHistoryService {
  static const _key = 'search_history';
  static const _maxItems = 20;

  /// Get search history (most recent first).
  Future<List<String>> getHistory() async {
    final prefs = await SharedPreferences.getInstance();
    final json = prefs.getString(_key);
    if (json == null) return const [];
    final list = jsonDecode(json) as List<dynamic>;
    return list.cast<String>();
  }

  /// Add a query to history. Duplicates are moved to top.
  Future<void> addQuery(String query) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return;

    final prefs = await SharedPreferences.getInstance();
    final history = await getHistory();

    // Remove if already exists.
    history.remove(trimmed);

    // Add to top.
    history.insert(0, trimmed);

    // Trim to max items.
    final trimmedHistory = history.take(_maxItems).toList();

    await prefs.setString(_key, jsonEncode(trimmedHistory));
  }

  /// Remove a specific query from history.
  Future<void> removeQuery(String query) async {
    final prefs = await SharedPreferences.getInstance();
    final history = await getHistory();
    history.remove(query);
    await prefs.setString(_key, jsonEncode(history));
  }

  /// Clear all search history.
  Future<void> clearHistory() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}
