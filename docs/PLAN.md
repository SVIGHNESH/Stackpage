# Implementation plan

How `docs/ROADMAP.md` actually gets built: the roadmap says *what* and *why*, this document says *in what order, touching which files, gated by what*.

The plan is cut into milestones, each of which leaves the app shippable.
No milestone starts until the previous one's gate is green, because every one of them builds on the refactor or engine the one before it introduced.

A standing rule inherited from the roadmap: nothing here adds a network call, and any new permission (camera in M3) is a deliberate, documented change to the manifest's no-permissions comment.

---

## M0 - Safety net (roadmap 0.1)

**Goal:** a decode regression can no longer reach a release unnoticed.

### Work

1. Add the `androidTest` source set and dependencies to `app/build.gradle.kts`: `androidx.test.ext:junit`, `androidx.test:runner`, and the instrumentation runner config in `defaultConfig`.
2. Bundle fixtures in `app/src/androidTest/assets/`:
   - `plain-900x1200.jpg` - a known-size baseline.
   - `rotated-90.jpg` - EXIF orientation 6, so the decoded result must come back 1200x900 upright.
   - `huge-6000x8000.jpg` - forces `inSampleSize` > 1.
   - `not-an-image.bin` - garbage bytes.
3. Write `ImageSourceTest` against a `content://` URI for each fixture (write them to the test app's cache and serve via `FileProvider`, or use `Uri.fromFile` on instrumentation context - decide in-code, whichever needs less scaffolding).
   Assert: decode succeeds with expected post-rotation dimensions, the long edge never exceeds `EXPORT_MAX_EDGE`, garbage returns null rather than throwing, and `readSize` agrees with `decode` on orientation.
4. Add a `PdfExporterTest` smoke test: export two fixtures to a file in the instrumentation context's cache dir, assert the file starts with `%PDF` and contains two `/Type /Page` entries.

### Gate

`./gradlew :app:connectedDebugAndroidTest` passes on the SM-T225.
Record the run in `docs/TESTING.md` and move the decode regression from "no coverage" to "covered".

---

## M1 - The tool framework (roadmap 0.2)

**Goal:** the app becomes a multi-tool shell with the PDF tool as its first resident.
This is a refactor with zero user-visible feature change, which is exactly why it must land alone.

### Target structure

```
dev.vighnesh.stackpage
├── MainActivity.kt              Nav host only; picker launchers move into io/
├── ui/home/HomeScreen.kt        Tool launcher: sections, not a grid
├── ui/theme/                    Unchanged
├── feature/
│   └── stack/                   The existing PDF flow, renamed, otherwise intact
│       ├── StackScreen.kt       (was MainScreen.kt)
│       ├── StackViewModel.kt    (was MainViewModel.kt)
│       └── PageGrid.kt
├── image/                       (was pdf/ImageSource.kt) shared decode engine
├── pdf/                         PageLayout.kt, PdfExporter.kt - unchanged
└── io/
    ├── Pickers.kt               rememberImagePicker / rememberPdfCreator wrappers
    ├── Output.kt                SAF write, mime/name mapping, share intent
    └── OpenWith.kt              ACTION_VIEW with the no-viewer toast
```

### Work

1. Add `androidx.navigation:navigation-compose` to the catalog.
   Two destinations to start: `home` and `stack`.
   Back from `stack` returns home; system back on home exits.
2. Extract the picker and SAF launcher code from `MainActivity` into `io/Pickers.kt` as composable `remember*` helpers, and the open/share intents into `io/`.
   `MainActivity` shrinks to theme + nav host.
3. Move files into the structure above.
   Pure mechanical moves in one commit; renames in a second commit so `git log --follow` stays useful.
4. Build `HomeScreen`: app name, a "Make a PDF" card (the only tool), and the privacy line.
   Design per the existing system - ink on paper, one primary action per card, no icon grid.
5. Decide and document launch behaviour: the app opens on `home` from now on.
   One extra tap for the existing flow is the accepted cost of every future tool; note it in `docs/DECISIONS.md` as decision 13.

### Gate

- The full M0 suite still passes untouched - the refactor moved files, not behaviour.
- The pick-reorder-export flow re-run on hardware per `docs/RUNBOOK.md` section 4, screenshots into `docs/TESTING.md`.
- `grep "^import android" app/src/main/java/dev/vighnesh/stackpage/pdf/PageLayout.kt` still prints nothing.
- While in there: fix the two known cosmetic grid defects (first thumbnail letterboxed, missing remove-button scrim) or explicitly re-log them against the new file paths.

---

## M2 - Compress to target size (roadmap 1.1)

**Goal:** the first new tool, proving the framework, and the highest-demand everyday job.

### Design

The engine is a pure function so the search logic is JVM-testable, mirroring the `PageLayout` pattern:

```kotlin
// image/TargetSizeSearch.kt - no Android imports
fun searchPlan(originalBytes: Long, targetBytes: Long, probe: (Attempt) -> Long): Plan
```

`probe` is injected: in production it is a real `Bitmap.compress` into a counting stream; in JVM tests it is a fake curve.
The search is binary over JPEG quality 40-95 first, then over scale in 10% steps if quality 40 still misses the target, converging in at most ~10 probes.

### Work

1. `image/TargetSizeSearch.kt` + `TargetSizeSearchTest` (monotonic fake, non-monotonic fake, unreachable target reports the best achievable rather than looping).
2. `image/Encoder.kt`: bitmap → JPEG/WebP bytes at (quality, scale), reusing `ImageSource.decode`.
3. `feature/compress/`: pick one or many images, target chips (50 / 100 / 200 / 500 KB / custom), live "will be ~X KB" estimate from probing the largest image, result list with before/after sizes, save-all via `io/Output`.
4. Home gains its second card; `io/Output` gains JPEG/WebP mime and naming (`name-200kb.jpg`).

### Gate

- JVM: search tests pass.
- Device: a 4MB photo hits a 200 KB target within 10%; a screenshot PNG converts and hits target; batch of 6 works; results land where SAF said.
- QA matrix updated.

---

## M3 - Scan mode (roadmap 1.2 + 1.3)

**Goal:** paper to PDF.
Crop/rotate ships inside this milestone because scan correction and manual crop are the same surface.

### Pre-work: two spikes, in this order

1. **ML Kit document scanner delivery.** Verify on the SM-T225 that `play-services-mlkit-document-scanner` works without adding any manifest permission and without network at scan time (models download via Play services out-of-band).
   If it silently requires more than that, fall back to CameraX capture + our own perspective crop, and record the finding in `docs/DECISIONS.md` either way.
2. **Page model refactor.** Before any UI: `StackViewModel`'s `List<Uri>` becomes `List<PageSpec>` where `PageSpec = (uri, rotationDegrees, cropRect?)`.
   `PdfExporter` applies the transform between decode and layout.
   `PageLayoutTest` untouched; add `PageSpecTest` for the transform arithmetic (pure Kotlin, JVM).

### Work

1. Manifest gains `CAMERA` (or nothing, if the ML Kit scanner hosts its own capture activity - the spike answers this).
   Update the manifest comment and README honestly: camera on request, still no `INTERNET`.
2. `feature/scan/`: capture → auto-corners → user adjusts → filter (original / greyscale / high-contrast B&W via `ColorMatrix`) → lands as pages in the stack tool.
3. Crop/rotate editor reachable from any page thumbnail in the stack grid (long-press menu or edit badge), writing back to its `PageSpec`.
4. Home: "Scan to PDF" becomes the first card.

### Gate

- A real paper document scans to a legible A4 PDF under 500 KB with the B&W filter.
- Rotate + crop on a gallery image round-trips correctly into the export (checked against `/MediaBox` and by eye).
- M0 suite green; manifest diff reviewed against the privacy claim.

---

## M4 - Convert and resize (roadmap 1.4)

Small by design; mostly reuses M2's encoder.

1. `feature/convert/`: pick → choose format (JPG / PNG / WebP; HEIC accepted in via the platform decoder) → optional resize preset → save.
2. Presets in pure Kotlin (`image/Presets.kt`): passport/signature dimensions and common social sizes, with a JVM test that presets are unique and positive.
3. `io/Output` completes its mime/extension table.

**Gate:** each format round-trips on device; a HEIC from the tablet camera converts; QA matrix updated.

---

## M5 - Existing PDFs (roadmap 2.1 + 2.2)

1. `pdf/PdfImporter.kt` wrapping `PdfRenderer`: page count, render page N at a chosen dpi.
   Instrumented test against a fixture PDF (generated by our own exporter in the test, so no binary fixture needed).
2. `feature/pdfpages/`: open a PDF → pages appear in the existing reorder grid → delete / reorder / extract range / append another PDF or images → re-export.
   This is the payoff of the hand-written grid: the surface already exists.
3. The rasterisation cost banner: when the source PDF contains text, say "pages will be re-saved as images" - detection heuristic is fine (any page with extractable text via `PdfRenderer` is not available pre-33, so gate the banner on file size heuristics or just always show it for imported PDFs).

**Gate:** merge two PDFs, split one, reorder pages of a scanned document - all verified by pulling the output and counting `/Type /Page`.

---

## M6 - Protect and sign (roadmap 2.3 + 2.4)

1. Add PDFBox-Android; record the decision-3 reversal in `docs/DECISIONS.md` (its stated revisit condition - encryption in scope - is now met).
2. `feature/protect/`: set a password on any PDF (open ours or imported), AES-128 via PDFBox.
3. `feature/sign/`: draw a signature on a canvas, store as a transparent PNG in app-private storage, stamp with drag-to-place onto any page; text watermark rides the same placement UI.
4. Revisit M5 with PDFBox available: lossless page operations for born-digital PDFs, removing the rasterisation banner where it no longer applies.

**Gate:** a protected PDF refuses to open without the password in two third-party viewers; a signed page shows the signature at the placed position in the export.

---

## M7 - The adjacent tools (roadmap 3.x, ordered)

Each is a self-contained `feature/` package over existing engines; ship in this order, one at a time.

1. **Clean metadata** (3.1) - smallest, purest fit: re-encode via the existing decode path, verify with `ExifInterface` that GPS and timestamps are gone. Also becomes a toggle at PDF export.
2. **QR scan + generate** (3.2) - ML Kit barcode on-device + ZXing generation; spike the same Play-services question as M3 first.
3. **OCR** (3.3) - ML Kit on-device text recognition: copy text from any image; searchable-PDF text layer only after M6's PDFBox is in.
4. **ID-photo maker** (3.4) - M4 presets + background whitening + 4x6 sheet layout (pure-Kotlin layout maths, JVM-tested like `PageLayout`).
5. **Batch rename / ZIP** (3.5) - `java.util.zip`, no new deps.
6. **WorkManager export** (3.6) - do the measurement first: find the page count where the SM-T225 fails, record it in `docs/TESTING.md`, then pick WorkManager vs streaming vs an honest cap.

Each lands with its own gate: device run, QA matrix row, home card.

---

## Cross-cutting rules

- **One milestone per release; version bump each time** (`versionName` 1.1, 1.2, ...). Before the first sideloaded release to anyone else: create the release keystore - owner's decision on where it lives (`docs/RUNBOOK.md` section 6).
- **Every engine is pure Kotlin where possible** and gets JVM tests before its UI exists: `TargetSizeSearch`, `PageSpec` transforms, presets, sheet layout. `PageLayoutTest` is the model.
- **Every milestone ends with the hardware pass** from `docs/RUNBOOK.md` and an updated QA matrix. A milestone with a red or un-run matrix is not done.
- **Docs move with code:** a new tool updates `ARCHITECTURE.md`'s tree, a reversed or new decision lands in `DECISIONS.md` in the same PR.
- **Dependency budget:** each new dependency is named in this plan or it does not go in. Currently planned: navigation-compose (M1), ML Kit doc scanner (M3, pending spike), a crop library (M3, uCrop vs canhub decided at spike time), PDFBox-Android (M6), ML Kit barcode + ZXing (M7.2), ML Kit text recognition (M7.3), CameraX (M3, only if the fallback path is needed).

## Risks worth naming now

| Risk | Where it bites | Mitigation |
| --- | --- | --- |
| ML Kit's Play-services delivery conflicts with the offline stance | M3, M7.2, M7.3 | Spike before committing; CameraX + own correction is the fallback; the claim is "no network *by the app*", document precisely what Play services does |
| `PdfDocument` memory ceiling unknown | M3 onward (scans make big stacks) | Measure at M3 gate, not when a user hits it; M7.6 exists for the fix |
| Page-model refactor destabilises the one working flow | M3 pre-work | It lands behind the M0 suite plus a full hardware pass before any scan UI is written |
| PDFBox-Android staleness / size | M6 | Evaluate at M6 start, not now; if unacceptable, encryption drops rather than a worse library going in |
| Scope creep toward the forty-icon grid | every milestone | The roadmap's "not planned" list is binding; new tool ideas get a roadmap PR first, not a branch |
