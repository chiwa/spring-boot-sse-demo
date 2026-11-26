---
name: sse-demo-maintenance
description: Review or maintain the Spring Boot and React SSE demo in this repository. Use for timed messages, Broadcast, session lifecycle, client listing, frontend stream behavior, related API contracts, Robot Framework E2E tests, and architecture documentation.
---

# Maintain the SSE demo

Read the relevant sections of the [Thai README](../../README.md) and [English README](../../README.en.md), and inspect the implementation before changing a contract.

## Preserve behavior

- The receiver page opens two independent EventSource connections. `GET /api/events` sends timed `tick` events and defaults to one second for a missing or empty interval. `GET /api/broadcast/events` sends only published `broadcast` events and idle heartbeat comments. Interval changes replace only the timed stream when it is enabled. Keep terminal `error` distinct from manual `closed`.
- Invalid intervals return an empty HTTP 400, and a stopped SSE session manager returns an empty HTTP 503, including for `Accept: text/event-stream`. `GET /api/clients` lists both channels ordered by `connectedAt` then `id`. Each row describes one stream.
- `POST /api/broadcast` targets only Broadcast sessions. Keep writes off the scheduler and HTTP request threads. Serialize writes per session on virtual threads and keep the eight-entry pending queue bounded. Skip periodic frames on a busy session. Count only successful writes within three seconds, and remove and close a session when a Broadcast write times out.
- Use shared cleanup on completion, timeout, and failure. During shutdown, `SseSessions` stops accepting new sessions, cancels timers, and requests emitter completion before the web server starts its graceful shutdown.

## Work within the existing boundaries

- `EventController` owns HTTP mapping. `EventService` validates Broadcast content, targets `Channel.BROADCAST`, and owns the `Delivery` interface. `SseSessions` implements delivery, filters active sessions by channel, and manages the lifecycle. `SseConnection` handles each emitter and its queue and cleanup. Reuse these boundaries when adding behavior.
- React pages and components render the UI. `useEventStream` manages SSE lifecycle, `api.js` sends REST requests, and `streamData.js` validates interval input and SSE payloads.

## Unit tests, integration tests, and documentation

- For every code change, review and run the relevant unit and integration tests and check that the documentation matches. For every behavior change or bug fix, add or update a focused unit test and an integration test that exercise the affected behavior. For presentation-only or behavior-preserving work, reuse applicable tests, verify affected UI in a browser, and record the verification instead of asserting markup or styles solely to add a test.
- Backend tests live in `backend/src/test`. Use MockMvc for the HTTP and SSE contracts. For delivery changes, exercise both channels together and cover recipient counts, backpressure or timeout as applicable, and session removal.
- Run `npm --prefix frontend ci` if dependencies are missing or the lockfile changed. Run `mvn -f backend/pom.xml test` and `npm --prefix frontend test` as applicable. The Node runner covers frontend logic and REST contracts. Use the Robot Framework suite in `tests/e2e/sse.robot` for browser verification of rendering, validation feedback, and EventSource lifecycle. Follow the README setup, then run `tests/e2e/.venv/bin/robot --outputdir tests/e2e/results tests/e2e/sse.robot` from the repo root. Rebuild with `npm --prefix frontend run build` after changing frontend code and before running against Vite Preview on port 4173 so it serves the current `frontend/dist`. Update the suite when a covered flow changes. Build the backend with `mvn -f backend/pom.xml package`.
- Update both `README.md` and `README.en.md` whenever behavior, APIs, architecture, or verification steps change. Keep the descriptions, commands, and affected Mermaid diagrams synchronized across both languages. Report the tests run, browser checks, and any verification that remains blocked.
