# 0010. API error model: domain exception kinds and one global handler

- Status: Accepted
- Date: 2026-11-20

## Context

The engineering standards require RFC 9457 problem details from one global exception handler, without stack traces or internal messages (ASVS L1). Every feature module throws its own business exceptions, but the handler lives in `shared`, and `shared` must not depend on any feature module (that would create cycles that `ModularityTests` rejects). Validation has one source of truth, the domain, so a field error must travel from a pure-Java domain class to an HTTP 400 without Spring in the domain.

## Decision

- `shared.api.DomainException` (pure Java) is the base of every business exception. It carries a **kind**: `INVALID_INPUT`, `CONFLICT` or `NOT_FOUND`, and optional **details** (field → reason). Its message and details are client-visible and must never contain customer text.
- One `@RestControllerAdvice` in `shared.infra.web` maps by kind, not by class. It extends Spring's `ResponseEntityExceptionHandler`, so Spring MVC's own errors (405, 415, unknown route, unreadable body) keep their correct status.

| Source | Status | `errors` extension |
|---|---|---|
| `DomainException` `INVALID_INPUT` / `CONFLICT` / `NOT_FOUND` | 400 / 409 / 404 | `details()` |
| Missing or non-UUID `X-Customer-Id` (temporary, until Sprint 4) | 400 | `{"X-Customer-Id": reason}` |
| Malformed JSON, wrong types | 400 | — (parser message not exposed) |
| Unknown JSON property | 400 | `{"<name>": "unknown property"}` if the name matches `^[A-Za-z][A-Za-z0-9_]{0,63}$`, otherwise none (client input is not echoed) |
| Path or query parameter that cannot be converted (e.g. a non-UUID `/api/v1/tickets/{id}`) | 400 | `{"<param>": "must be a UUID" \| "has an invalid value"}`; the value is not echoed, and `instance` is the matched route template (`/api/v1/tickets/%7Bid%7D`), not the raw path |
| No route or static resource matches the path | 404 | — (generic detail, path not echoed; `instance` is an opaque `urn:uuid:` occurrence id, also in the debug log) |
| Body over 128 KB (`triode.api.max-request-body-bytes`): declared `Content-Length` rejected by a filter before reading (plain `sendError(413)` if no resolver handles it); chunked bodies stopped by Jackson's `max-document-length` | 413 | — |
| Anything else, incl. invalid stored data found by `reconstitute` | 500 | — (logged server-side, generic detail) |

- The 128 KB body limit is derived from the domain limits so that every valid ticket fits: in the worst case each code point is a non-BMP character sent as an escaped surrogate pair (12 bytes), and 12 × (255 + 10,000) = 123,060 bytes plus JSON syntax. It must be raised if `Ticket.MAX_SUBJECT_LENGTH` or `MAX_BODY_LENGTH` grows.
- Invalid data loaded from the database is the server's fault: the persistence adapter wraps the domain's `INVALID_INPUT` exception in a server-side exception, so it is a 500, never a 400.

## Consequences

- New modules get correct HTTP errors by choosing a kind; the handler never changes for a new exception class.
- Only three kinds exist. A case that fits none of them (e.g. "forbidden" once authentication exists) needs a new kind and a line in this table, and every module sees it.
- Kinds are coarse: two different conflicts both become 409 with only the message to tell them apart. If clients need to branch on the cause, we add a stable problem `type` URI per exception later.
- Domain messages become part of the API contract and must stay free of personal data; the review checks this.
