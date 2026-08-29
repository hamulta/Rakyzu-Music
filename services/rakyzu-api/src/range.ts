export interface ByteRange {
  offset: number;
  length: number;
}

export type ParsedRange =
  | { kind: "full" }
  | { kind: "partial"; range: ByteRange }
  | { kind: "unsatisfiable" };

export function parseRange(header: string | null, size: number): ParsedRange {
  if (header === null) return { kind: "full" };
  if (!Number.isSafeInteger(size) || size < 0 || !header.startsWith("bytes=")) {
    return { kind: "unsatisfiable" };
  }

  const specification = header.slice("bytes=".length).trim();
  if (specification.includes(",")) return { kind: "unsatisfiable" };
  const match = specification.match(/^(\d*)-(\d*)$/);
  if (!match) return { kind: "unsatisfiable" };

  const startText = match[1] ?? "";
  const endText = match[2] ?? "";
  if (startText === "" && endText === "") return { kind: "unsatisfiable" };

  if (startText === "") {
    const suffix = parseSafeInteger(endText);
    if (suffix === null || suffix <= 0 || size === 0) return { kind: "unsatisfiable" };
    const length = Math.min(suffix, size);
    return { kind: "partial", range: { offset: size - length, length } };
  }

  const start = parseSafeInteger(startText);
  const requestedEnd = endText === "" ? size - 1 : parseSafeInteger(endText);
  if (start === null || requestedEnd === null || start >= size || requestedEnd < start) {
    return { kind: "unsatisfiable" };
  }
  const end = Math.min(requestedEnd, size - 1);
  return { kind: "partial", range: { offset: start, length: end - start + 1 } };
}

function parseSafeInteger(value: string): number | null {
  if (!/^\d+$/.test(value)) return null;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : null;
}
