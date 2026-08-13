# Roadmap

Ordered by what unblocks the most, not by what is easiest.

Each item states what it needs and what it will break, because every one of them touches the export path.

---

## 0. Instrumented test for `ImageSource`

**Before anything else.**

The one shipped export bug lived in `ImageSource.decode`, was invisible to the compiler and to all 15 unit tests, and broke every export.
There is currently nothing standing between a decode regression and a release.

Needs a `connectedAndroidTest` source set and a fixture image, either pushed to the device or bundled in `androidTest/assets`.
Assert: a known image decodes, its dimensions match after downsampling, a rotated-EXIF fixture comes back upright, and a garbage URI returns null rather than throwing.

Nothing else on this list is safe to build without it.

---

## 1. Crop and rotate

The most requested pair for anything scan-shaped, and the reason people open a "utility" app rather than sharing straight to a PDF printer.

Use uCrop or canhub's Android-Image-Cropper rather than hand-rolling a crop overlay.
Applies per page, before export.

**What it breaks:** `UiState.images` is currently a bare `List<Uri>`, which works because a page is exactly its source image.
A crop makes that false.
The list becomes a list of page models carrying a URI plus a transform, and `PdfExporter` has to apply the transform between decode and layout.
Do that refactor deliberately, not as a side effect.

**Also:** this is the first genuine second screen, so it is where navigation stops being furniture (see decision 12).

---

## 2. Compress and resize

A target-size search over `Bitmap.compress`, with a live estimate of the output size in the export bar.

The estimate is the hard part and the part users judge the app on.
Do it by measuring one representative page rather than by guessing from pixel count.

**What it breaks:** nothing structural. `EXPORT_MAX_EDGE` becomes a user-facing setting rather than a constant, which the code already anticipates.

---

## 3. Format conversion, JPG / PNG / WebP out

Shares the whole decode path with the PDF export, so the work is an output writer and a file-naming scheme, not a new pipeline.

**What it breaks:** the app currently has exactly one output type, and `MainViewModel.suggestedFileName` and the SAF mime type are both hardcoded to PDF.
Both become a function of the chosen format.

---

## 4. Background export via WorkManager

`PdfDocument` buffers the entire document in memory before writing, and the export currently runs in `viewModelScope`.
A large job dies if the app is backgrounded, and no ceiling on page count has ever been measured.

Measure first: find the page count at which a mid-range device fails, and record it in `docs/TESTING.md`.
Then decide whether the fix is WorkManager, a streaming writer, or simply a cap with an honest message.

**What it breaks:** `ExportState` currently lives in the ViewModel and dies with it. A WorkManager job outlives the ViewModel, so progress has to come from observed work state instead.

---

## 5. Strip EXIF and GPS on export

A real privacy feature and a natural extension of the existing decode step, which already reads EXIF for orientation.

Photos carry GPS coordinates.
A PDF assembled from them and mailed onwards carries them too, and nobody expects that.

Should be a visible toggle with a sane default rather than silent behaviour, since some users want the metadata kept.

---

## Not planned

Stated so the answer is on record rather than relitigated:

- **Cloud sync, accounts, or any upload.** Contradicts decision 2, which is the product's one claim.
- **OCR.** Would pull in either a large ML dependency or a network service. Different product.
- **PDF editing, merging existing PDFs, page extraction.** Needs a real PDF parser, which the platform writer is not.
- **Ads or analytics.** There is no `INTERNET` permission and there will not be one.
