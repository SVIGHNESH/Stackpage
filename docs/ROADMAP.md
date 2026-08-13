# Roadmap

Stackpage is growing from "images to PDF" into an offline everyday-documents utility: the app you reach for when your phone has to act as a scanner, a converter, and a file clerk.

Two rules bound everything on this list.

1. **The no-network rule is permanent.** Every feature here runs entirely on-device. Anything that needs a server - cloud OCR, translation, AI edits - is out, because the manifest-level privacy claim (decision 2 in `docs/DECISIONS.md`) is the product's moat.
2. **A cluster, not a grid.** The trap every super-utility app falls into is a home screen of forty icons. The scope is one coherent cluster - capture, convert, compress, assemble, protect documents and images - organised as a few shallow sections, each of which works completely.

Ordered by what unblocks the most, not by what is easiest.
Each item states what it needs and what it will break, because every one of them touches the shared image or export path.

---

## Phase 0 - foundations

### 0.1 Instrumented test for `ImageSource`

**Before anything else.**

The one shipped export bug lived in `ImageSource.decode`, was invisible to the compiler and to all 15 unit tests, and broke every export.
There is currently nothing standing between a decode regression and a release.

Needs a `connectedAndroidTest` source set and a fixture image, either pushed to the device or bundled in `androidTest/assets`.
Assert: a known image decodes, its dimensions match after downsampling, a rotated-EXIF fixture comes back upright, and a garbage URI returns null rather than throwing.

Nothing else on this list is safe to build without it.

### 0.2 Navigation and the tool framework

The refactor that every later item depends on, done once instead of five times.

Today `UiState` assumes one flow and `MainScreen` is the whole app.
A multi-tool app needs a home surface with real navigation, each tool as a self-contained feature package, and two shared layers underneath: `ImageSource` for input and a common save/share layer for output.

Structure to aim for:

```
ui/home/          Tool launcher: a few sections, not a grid of forty
feature/<tool>/   One package per tool, owning its screen and state
pdf/, image/      Shared engines: decode, layout, write
io/               Shared pickers, SAF output, share sheet, file naming
```

**What it breaks:** `UiState.images` as a bare `List<Uri>` survives only inside the PDF tool.
This lands before tool number two, not after tool number five - retrofitting navigation under five shipped screens is how codebases rot.

---

## Phase 1 - the everyday jobs (highest demand first)

### 1.1 Compress to target size

"Make this fit under 200 KB" is *the* everyday-user job: government portals, job applications and exam forms all enforce hard upload limits, and nobody serves this well without ads.

Preset targets (50 / 100 / 200 / 500 KB / custom) plus a quality slider, for single images and batches, with a live estimate in the bar.
Implementation is a binary search over `Bitmap.compress` quality, then dimensions if quality alone cannot reach the target.
Estimate by measuring one representative page, not by guessing from pixel count.

**Needs:** the Phase 0.2 framework - this is deliberately the first new tool through it.
**What it breaks:** nothing structural; `EXPORT_MAX_EDGE` becomes an input rather than a constant, which the code already anticipates.

### 1.2 Document scan mode

The single biggest unlock: camera capture with edge detection, perspective correction, and a black-and-white "scan" filter turns "images to PDF" into "paper to PDF", which is what everyday users actually want.

ML Kit's document scanner runs on-device and keeps the no-network rule; OpenCV is the fallback if its Play-services delivery model proves incompatible with the no-permissions stance - verify that before committing to it.

**Needs:** CameraX, and crop/rotate (1.3) as the correction step.
**What it breaks:** first use of the camera permission. That is a user-visible change to the "no permissions" claim, so the manifest comment and README must be updated honestly: camera on request, still no network.

### 1.3 Crop, rotate, straighten

Required by scan mode and long-requested on its own.
Use uCrop or canhub's Android-Image-Cropper rather than hand-rolling a crop overlay.

**What it breaks:** a page stops being exactly its source image.
The PDF tool's list becomes a list of page models carrying a URI plus a transform, and `PdfExporter` applies the transform between decode and layout.
Do that refactor deliberately, not as a side effect.

### 1.4 Format conversion and resize presets

JPG / PNG / WebP out, HEIC in, sharing the whole existing decode path - the work is an output writer and a naming scheme, not a new pipeline.
Resize presets ride along: passport and signature sizes for form uploads, plus common social dimensions.

**What it breaks:** the output layer currently hardcodes one mime type and one filename shape; both become a function of the chosen format.

---

## Phase 2 - the PDF side grows up

### 2.1 PDF to images

Platform `PdfRenderer`, zero new dependencies.
This is the gateway: once an existing PDF can be rendered to pages, those pages flow into every tool the app already has.

### 2.2 Merge, split, reorder, extract pages

Built directly on 2.1 - render, manipulate the page list with the grid UI that already exists, re-export.
The reorder grid was hand-written (decision 8) and this is where that investment pays off.

**Honest cost:** round-tripping through the renderer rasterises text PDFs, growing them and making text unselectable.
Acceptable for scanned documents, wrong for born-digital ones - say so in the UI rather than surprising people.
Lossless page operations need a real PDF library; see 2.3.

### 2.3 Password-protect a PDF

"Lock my ID document" is a real weekly task.
Needs a real writer - PDFBox-Android is the candidate (Apache licence, works offline) - which also unlocks lossless page operations in 2.2.

**What it breaks:** decision 3 (platform writer only) gets its stated revisit condition met: encryption is now in scope.
Record the reversal in `docs/DECISIONS.md` when it lands.

### 2.4 Watermark and signature stamping

Draw a signature once, store it locally, stamp it on any page; text watermarks ride along.
"Sign and send back" is a weekly task for nearly everyone.

---

## Phase 3 - adjacent tools that share the same muscles

### 3.1 Clean metadata (strip EXIF and GPS)

Promoted from a PDF-export option to a standalone tool: pick photos, get copies with metadata removed.
Photos carry GPS coordinates and nobody expects a shared file to include them; this fits the privacy brand exactly.
The decode path already reads EXIF, so the work is small.
A visible toggle with a sane default at PDF export as well, since some users want metadata kept.

### 3.2 QR and barcode scanner + generator

Scan to text, URL, or WiFi credentials; generate QR codes for a UPI ID, contact, or WiFi network.
ML Kit barcode scanning runs on-device; generation is ZXing, pure Java.
Tiny, fully offline, and used daily.

### 3.3 OCR - image to text

ML Kit's on-device text recognition means copy-text-from-a-photo *without* breaking the no-network rule.
Later, combined with 2.3's real PDF writer, this becomes searchable scanned PDFs - the feature that makes scan mode best-in-class.

### 3.4 ID-photo maker

Fixed sizes from 1.4's presets, background whitening, and a 4x6 print-sheet layout so one photo prints as eight.
Pairs with scan mode's capture flow.

### 3.5 Batch file tools

Batch rename with patterns, images to ZIP, and a view of what the app's own outputs are consuming - housekeeping for the files this app itself creates.

### 3.6 Background export via WorkManager

`PdfDocument` buffers the entire document in memory and export runs in `viewModelScope`, so a large job dies if the app is backgrounded and no page-count ceiling has ever been measured.
Measure first, record the failure point in `docs/TESTING.md`, then decide between WorkManager, a streaming writer, or an honest cap.
Rises in priority the moment scan mode makes 50-page documents common.

**What it breaks:** `ExportState` currently dies with the ViewModel; progress has to come from observed work state instead.

---

## Deliberately not planned

Stated so the answer is on record rather than relitigated.

- **Anything needing network** - cloud OCR, translation, AI editing, sync, accounts. Kills the manifest-level privacy claim that is the product's moat.
- **Video tools.** A different memory and codec universe that doubles the app's complexity for a different audience.
- **A forty-icon tools grid.** Sections stay few and shallow; a tool that does not fit the documents-and-images cluster does not go in.
- **Ads and analytics.** There is no `INTERNET` permission and there will not be one.
