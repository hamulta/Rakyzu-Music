import { describe, expect, it } from "vitest";

import { parseRange } from "../src/range";

describe("parseRange", () => {
  it("returns a full response when Range is absent", () => {
    expect(parseRange(null, 100)).toEqual({ kind: "full" });
  });

  it("parses bounded and open-ended ranges", () => {
    expect(parseRange("bytes=10-19", 100)).toEqual({
      kind: "partial",
      range: { offset: 10, length: 10 },
    });
    expect(parseRange("bytes=90-", 100)).toEqual({
      kind: "partial",
      range: { offset: 90, length: 10 },
    });
  });

  it("parses suffix ranges", () => {
    expect(parseRange("bytes=-15", 100)).toEqual({
      kind: "partial",
      range: { offset: 85, length: 15 },
    });
  });

  it.each(["bytes=100-101", "bytes=20-10", "items=0-5", "bytes=0-1,3-4"])(
    "rejects unsupported or unsatisfiable range %s",
    (header) => {
      expect(parseRange(header, 100)).toEqual({ kind: "unsatisfiable" });
    },
  );
});
