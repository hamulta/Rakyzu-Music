const BASE_HEADERS = {
  "content-security-policy": "default-src 'none'",
  "referrer-policy": "no-referrer",
  "x-content-type-options": "nosniff",
} as const;

export function jsonResponse(
  body: unknown,
  status: number,
  requestId: string,
  origin: string | null,
): Response {
  const headers = responseHeaders(requestId, origin);
  headers.set("content-type", "application/json; charset=utf-8");
  headers.set("cache-control", "no-store");
  return Response.json(body, { status, headers });
}

export function errorResponse(
  code: string,
  message: string,
  status: number,
  requestId: string,
  origin: string | null,
  extraHeaders?: HeadersInit,
): Response {
  const response = jsonResponse({ error: { code, message } }, status, requestId, origin);
  if (extraHeaders) {
    new Headers(extraHeaders).forEach((value, key) => response.headers.set(key, value));
  }
  return response;
}

export function responseHeaders(requestId: string, origin: string | null): Headers {
  const headers = new Headers(BASE_HEADERS);
  headers.set("x-request-id", requestId);
  if (origin) {
    headers.set("access-control-allow-origin", origin);
    headers.set("vary", "Origin");
  }
  return headers;
}
