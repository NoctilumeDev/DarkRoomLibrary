# Design QA

- Date: 2026-08-30
- Preview: `http://127.0.0.1:4178/DarkRoomLibrary/`
- CSS viewport: 430 × 932, device scale factor 1
- State: responsive web demo; reader review and reader-home overlay; super-administrator book and borrow management; login sheet

## Visual truth and implementation evidence

| Issue | Source visual truth | Browser-rendered implementation | Source px | Implementation px | Side-by-side comparison |
| --- | --- | --- | --- | --- | --- |
| Review segmented control | `C:\Users\lenovo\AppData\Local\Temp\codex-clipboard-5c5fa8d4-ee10-401e-b0a3-e4d17bd8b21b.png` | `C:\Users\lenovo\AppData\Local\Temp\drl-book-reviews-fixed.png` | 666 × 1197 | 415 × 899 | `C:\Users\lenovo\AppData\Local\Temp\drl-compare-review.png` |
| Demo overlay alignment | `C:\Users\lenovo\AppData\Local\Temp\codex-clipboard-be1f4072-8065-4b58-addd-cca71ac36375.png` | `C:\Users\lenovo\AppData\Local\Temp\drl-reader-home-panel-fixed.png` | 1440 × 3200 | 430 × 932 | `C:\Users\lenovo\AppData\Local\Temp\drl-compare-panel.png` |
| Deleted-book state and table | `C:\Users\lenovo\AppData\Local\Temp\codex-clipboard-af8b425f-daa7-4fe7-99b8-510d83031d7d.png` | `C:\Users\lenovo\AppData\Local\Temp\drl-book-manage-deleted-fixed.png` | 622 × 1203 | 415 × 899 | `C:\Users\lenovo\AppData\Local\Temp\drl-compare-book-manage.png` |
| Borrow table | `C:\Users\lenovo\AppData\Local\Temp\codex-clipboard-f8d11d6f-93ed-4f69-851d-b3c457749f02.png` | `C:\Users\lenovo\AppData\Local\Temp\drl-borrow-manage-fixed-start.png` | 643 × 1198 | 415 × 899 | `C:\Users\lenovo\AppData\Local\Temp\drl-compare-borrow.png` |
| Login labels | `C:\Users\lenovo\AppData\Local\Temp\codex-clipboard-5d9b33d7-ff4e-49cc-b420-bfd839ec9ba4.png` | `C:\Users\lenovo\AppData\Local\Temp\drl-login-fixed.png` | 639 × 1270 | 430 × 932 | `C:\Users\lenovo\AppData\Local\Temp\drl-compare-login.png` |

The source images include handset/browser chrome at varying densities. For comparison, both sides were scaled to a common 430 px visual width without changing aspect ratio. Findings were judged on the app-owned content and focused issue regions, not on the external browser chrome.

## Findings and comparison history

### Iteration 1 — blocked

- [P1] The reader review radio group stretched to the mobile paper width while its two buttons occupied only the left portion, producing an unintended empty third segment.
- [P1] The reader demo panel was positioned relative to a header toggle offset from the right edge, so the 310 px panel extended past the left viewport boundary.
- [P1] Book and borrow tables removed most columns at the compact breakpoint, preventing mobile users from reaching complete record information.
- [P2] The intentionally hidden add-book action left its wrapper in the deleted-books grid, creating a blank action cell and making the state change look broken.
- [P2] The 60 px login label column wrapped four-character labels and broke row alignment.

### Iteration 2 — fixes applied

- Constrained the review radio group to its content width and aligned it to the start of the mobile toolbar.
- Anchored the demo panel to 12 px viewport gutters with fixed positioning across reader, administrator, and staff shells.
- Preserved all book and borrow columns and added table-scoped horizontal overflow, touch panning, keyboard focus, and accessible region labels.
- Removed the entire add-action wrapper in deleted mode and promoted the query action to a full-width row; the add action remains intentionally unavailable because deleted records can only be restored.
- Increased the login label column to 72 px, disabled label wrapping, and normalized the captcha label spacing.

### Post-fix evidence

- Review group: 190 px container versus 188.67 px total button width; no residual blank segment and no document overflow.
- Demo panel: left 12 px, right 418 px inside a 430 px viewport; no clipping or document overflow.
- Deleted-book toolbar: add-action count 0; query width equals toolbar width at 366.67 px; batch-restore action remains present.
- Book table: 13 headers retained; scroll region 351 px client width versus 1335 px scroll width. Horizontal gesture moved the table to scrollLeft 640 while page scrollX remained 0.
- Borrow table: 9 headers retained; scroll region 351 px client width versus 1140 px scroll width. Horizontal gesture moved the table to scrollLeft 520 while page scrollX remained 0.
- Login labels: all three labels are 72 × 32 px with `white-space: nowrap`; no document overflow.
- Browser console: no application warnings or errors across the five verified states.

## Required fidelity surfaces

- Fonts and typography: existing reader serif and administrator Chinese type systems are unchanged; only the login label width and nowrap contract changed to prevent broken wrapping.
- Spacing and layout rhythm: the review control now terminates after the second option; the overlay uses equal viewport gutters; deleted-mode actions no longer contain an empty grid cell.
- Colors and tokens: existing paper, seal, jade, ink, and night/day theme tokens are unchanged.
- Image quality and assets: no source assets, crops, icons, or background images were changed.
- Copy and content: all product copy is preserved. The hidden add-book action is classified as an intentional business rule, while the surrounding layout defect is fixed.

## Verification

- Primary interactions tested in the in-app browser: enter demo, switch reader/admin identity, open/close demo overlay, switch normal/deleted book state, and horizontally scroll both administrator tables.
- `npm run lint`: passed.
- `npm run test:unit`: 13 files and 68 tests passed.
- `npm run build:demo`: passed.

## Follow-up polish

- P3: native handset scrollbar rendering differs by browser, but the horizontal scroll affordance and touch behavior are present and the page itself remains fixed.

final result: passed
