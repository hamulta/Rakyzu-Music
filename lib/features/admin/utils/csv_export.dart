import 'dart:convert';

String toCsv(List<Map<String, dynamic>> rows) {
  if (rows.isEmpty) return '';
  final headers = rows.first.keys.toList();
  final sb = StringBuffer();
  sb.writeln(headers.map((h) => '"$h"').join(','));
  for (final r in rows) {
    sb.writeln(headers
        .map((h) => '"${(r[h]?.toString() ?? '').replaceAll('"', '""')}"')
        .join(','));
  }
  return sb.toString();
}

String csvBase64(String csv) => base64Encode(utf8.encode(csv));
