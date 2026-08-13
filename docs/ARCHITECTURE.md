# Architecture

How Stackpage is put together, and why the seams sit where they do.

## Module layout

One Gradle module, `:app`.
A `:core` split was considered and rejected: the only Android-free logic is one file, and a module boundary to protect one file is ceremony.
The boundary is enforced by convention instead, and `docs/TESTING.md` explains how it is checked.

```
dev.vighnesh.stackpage
├── MainActivity.kt          Theme plus nav host, nothing else
├── ui/
│   ├── home/HomeScreen.kt   Tool launcher: sections of cards, not an icon grid
│   └── theme/               Colour scheme and type scale
├── feature/
│   ├── stack/               The PDF flow
│   │   ├── StackRoute.kt    Wires the view model and the io/ launchers
│   │   ├── StackScreen.kt   Empty state, export bar, options sheet, result overlays
│   │   ├── StackViewModel.kt Page stack, export options, export state
│   │   ├── PageGrid.kt      Reorderable thumbnail grid; tap opens the editor
│   │   └── CropEditor.kt    Full-screen crop and rotate for one page
│   ├── compress/            Compress-to-target-size
│   │   ├── CompressRoute.kt
│   │   ├── CompressScreen.kt Target chips, item list, save bar
│   │   └── CompressViewModel.kt Batch state, estimate, save-all into a SAF tree
│   ├── convert/             Format conversion and preset resize
│   │   ├── ConvertRoute.kt
│   │   ├── ConvertScreen.kt  Format chips, size-preset chips, item list
│   │   └── ConvertViewModel.kt JPG/PNG/WebP out, fit-within resize, SAF tree
│   ├── scan/
│   │   └── ScanRoute.kt     Landing screen; launches the ML Kit scanner
│   ├── clean/               Metadata removal by re-encode
│   │   ├── CleanRoute.kt
│   │   ├── CleanScreen.kt
│   │   └── CleanViewModel.kt Decode at 4096px cap, fresh JPEG, SAF tree
│   ├── sign/                Draw once, stamp onto any PDF page
│   │   ├── SignRoute.kt
│   │   ├── SignScreen.kt     Pad, page preview, drag-to-place, size slider
│   │   ├── SignViewModel.kt  PdfRenderer preview; PDFBox appends the stamp
│   │   ├── SignaturePad.kt   Stroke capture to a transparent PNG
│   │   └── SignatureStore.kt One PNG in app-private files
│   └── protect/             Password-protect any PDF
│       ├── ProtectRoute.kt
│       ├── ProtectScreen.kt  File card, password field, save
│       └── ProtectViewModel.kt PDFBox AES-128 StandardProtectionPolicy
├── image/
│   ├── ImageSource.kt       Shared decode engine: EXIF rotation, downsampling
│   ├── TargetSizeSearch.kt  Pure Kotlin. Quality/scale search over an injected probe.
│   ├── Presets.kt           Pure Kotlin. Size presets and fit-within arithmetic.
│   └── Encoder.kt           Bitmap to JPEG/WebP bytes; counting-stream probe
├── pdf/
│   ├── PageLayout.kt        Pure Kotlin. Page geometry in PostScript points.
│   ├── PageTransform.kt     Pure Kotlin. Crop-then-rotate arithmetic.
│   ├── PageSpec.kt          A page: uri + rotation + optional crop
│   ├── PdfImporter.kt       PdfRenderer: existing PDF pages to cache JPEGs
│   └── PdfExporter.kt       PdfDocument, one page per PageSpec
└── io/
    ├── Pickers.kt           rememberImagePicker / rememberPdfCreator / rememberDirectoryPicker
    ├── Output.kt            Share intent, mime and name mapping, SAF-tree file creation
    └── OpenWith.kt          ACTION_VIEW with the no-viewer toast
```

## Data flow

There is one direction and no repository layer, because there is no persistence.

```
Photo Picker ──uris──▶ StackViewModel.addImages
                            │
                       UiState.images (List<Uri>, order is page order)
                            │
             ┌──────────────┴──────────────┐
             ▼                             ▼
        PageGrid                    StackViewModel.export
     (thumbnails, drag)                     │
                                     PdfExporter.export
                                            │
                             per image: ImageSource.decode
                                            │
                                     layoutPage(...)
                                            │
                              PdfDocument page + drawBitmap
                                            │
                                  SAF OutputStream ──▶ file
```

`UiState.images` is the single source of truth for both what is displayed and what is exported.
The list order *is* the page order.
There is no separate page model, no ids, and no index bookkeeping to drift out of sync.

## The seam that matters

`pdf/PageLayout.kt` contains the arithmetic that decides where an image lands on a page: aspect fit, centring, margins, orientation resolution, and the margin clamp.
It imports no Android types.

This is deliberate and it is the most load-bearing structural decision in the repo.
Page geometry is where apps in this category actually go wrong: images stretched because someone scaled width and height independently, margins eaten because the content box was computed from the page rather than the page minus insets, landscape photos forced onto portrait pages.
None of those failures throw.
They produce a PDF that opens fine and looks wrong.

Keeping the maths free of `Bitmap` and `Context` means all of it is covered by JVM tests that run in about half a second, with no emulator and no Robolectric.
`PageLayoutTest` is the specification: if you want to change what a correct page looks like, change the test first.

Everything Android-flavoured lives on the other side of that seam:

- `ImageSource` owns decoding, and therefore owns the two failure modes of real phone photos: they are too big for memory, and their orientation is in EXIF rather than in the pixels.
- `PdfExporter` owns the platform `PdfDocument`, page construction, progress reporting, cancellation and bitmap lifetime.

## State model

`UiState` holds three things: the image list, the export options, and an `ExportState`.

`ExportState` is a sealed interface with four cases: `Idle`, `Running(done, total)`, `Done(target, pageCount, byteSize)`, `Failed(message)`.
The UI renders a blocking overlay for the last three and nothing for `Idle`.
Making this a sealed type rather than a set of booleans is what stops the "spinner and success card both visible" class of bug.

Export runs in a `viewModelScope` coroutine held in `exportJob` so that cancellation is real: `PdfExporter` calls `ensureActive()` before each page, so cancelling stops at the next page boundary rather than after the whole document is built.

Nothing is persisted.
Killing the app loses the page stack, which is correct for a tool whose whole job is a single one-shot operation.
The one cache that exists is the imported-PDF page renders under `cacheDir/imported-pdf-pages`, and it is deleted on Clear all and on every process start, because the page list they belong to does not survive either.

## Why the platform PDF writer

`android.graphics.pdf.PdfDocument` ships with the OS, carries no licence encumbrance, and needs no third-party parser.
It writes via Skia, so the output identifies itself as `Skia/PDF` in the producer field.

iText and PDFBox would only be warranted for OCR, encryption, or editing an existing PDF.
None of those are in scope, and iText's AGPL terms would be a real constraint on a public repo.

## Reordering

Drag-to-reorder is written directly against `LazyGridState` rather than pulled in as a dependency.

The whole interaction is a long-press detector, a hit test against `layoutInfo.visibleItemsInfo`, and a move on the underlying list.
It is about sixty lines.
Owning it keeps the drag threshold, the haptic pattern and the swap rule tunable, and avoids taking a dependency whose API churns between majors for something this small.

The move goes through `StackViewModel.move`, so the list stays the single source of truth and the grid animates via `Modifier.animateItem()` rather than by tracking a floating drag offset.

## Memory budget

The export path is the only place this app can plausibly run out of memory, so the budget is explicit:

- Images are downsampled at decode time via `inSampleSize` toward `EXPORT_MAX_EDGE` (2400px). Sampling is power-of-two, so the decoded long edge can land anywhere in [2400, 4800); the page layout's destination rect does the final scaling. The instrumented `ImageSourceTest` pins this exact behaviour.
- Bitmaps decode as `RGB_565`. A printed page has no alpha, and this halves the cost of every decode.
- Exactly one page bitmap is alive at a time. `PdfExporter` recycles in a `finally` before moving to the next image.
- `PdfDocument` itself buffers the whole document in memory before `writeTo`. This is the real ceiling on page count and it is not currently bounded. A 100-page export has not been tested. See `docs/ROADMAP.md`.
