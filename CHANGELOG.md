# Changelog

All notable changes to DarkRoomLibrary are documented in this file.

## [Unreleased]

### Documentation

- Synchronized the repository overview, technical documents, presentation, PDFs, and interactive module map with the released v1.2.7 implementation without changing product behavior.
- Corrected the v1.2.7 backend coverage evidence to the JaCoCo report-level unique source-line total: `3837/5293` (`72.49%`). The former CSV class-row sum double-counted source lines shared by outer and nested classes; the `290/290` test result and 70% gate are unchanged.

### Presentation

- Added a shared DarkRoomLibrary evidence theme, report-scope notes, and project navigation to the generated JaCoCo and Vitest V8 coverage pages. Native sorting, filtering, and source navigation remain intact, while the default saturated traffic-light colors and pixel icons are replaced by restrained jade, ochre, and seal-red evidence states.

## [1.2.7] - 2026-08-28

### Security

- Enforced the existing 8-20 character new-password contract at both request and service boundaries for public registration and administrator-created accounts.
- Replaced four user-influenced regular-expression scans with one bounded character-class pass while preserving the existing ASCII password policy.

### Verification

- Added request-wiring, service-boundary, line-terminator compatibility, and oversized-input regression tests; the backend passes `290/290` tests with `3837/5293` unique source lines covered (`72.49%`, rounded down from the JaCoCo report-level counter).
- Kept the database schema, API shape, frontend domain behavior, and existing-login compatibility unchanged. Remote CodeQL closure remains a release gate rather than a local claim.

## [1.2.6] - 2026-08-26

### Reliability

- Stabilized the exact backend coverage evidence at `284/284` tests and `3813/5277` covered lines.
- Added an append-only release guard and a repository gate that prevents its silent removal.
- Refreshed pinned GitHub Actions dependencies and pull-request workflow triggers.

### Documentation

- Clarified that the per-email daily verification-code budget counts delivery attempts, including failed mail delivery, while a failed delivery releases only the short resend slot.
- Kept product behavior, database schema, APIs, and frontend domain behavior unchanged.

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
