# Changelog

All notable changes to DarkRoomLibrary are documented in this file.

## [1.2.5] - 2026-08-08

### Security

- Updated `org.jsoup:jsoup` from `1.22.2` to `1.23.1` to resolve `GHSA-pmhh-3w7g-xqp8`.
- Kept the patch dependency-only: no business logic, database schema, API, or frontend behavior changed.

## [1.2.4] - 2026-08-08

### Security

- Updated the locked `nanoid` transitive dependency for `GHSA-2v37-7h3g-55p8`.
- Added CodeQL, dependency review, and high-severity npm audit gates.
- Kept the development API behind the Vite same-origin proxy so the strict CSP remains effective.

### Delivery

- Added architecture and release-version repository gates.
- Added CI-verified, Security-gated releases with ZIP, SHA-256, manifest, and SPDX SBOM assets.
- Extended the Pages deployment wait boundary after a GitHub-side deployment timeout.

## [1.2.3] - 2026-08-02

### Changed

- Froze the enhanced-monolith architecture and completed production boundary hardening.
- Published the final M0-M8-style library workflow verification evidence for six identities, three instances, concurrency, middleware degradation, and browser diagnostics.
