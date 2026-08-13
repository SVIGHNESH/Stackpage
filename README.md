# Stackpage

An offline Android utility that turns images into a PDF.
Pick photos or scans, drag them into the order you want, and export one document.

Nothing is uploaded.
The app declares no permissions at all - not even `INTERNET` - which makes that claim structural rather than a promise.

## Status

v1 covers the whole core loop: pick → reorder → export.
Crop, rotate, resize, compress and format conversion are planned but not built (see [Roadmap](#roadmap)).

`./gradlew :app:assembleDebug test` passes: the debug APK builds and 15 JVM unit tests cover the page-layout arithmetic.
Nothing here has been seen running on a screen - there is no AVD and no system image on this machine, so the UI is unverified visually.

## Build

```bash
./gradlew :app:assembleDebug      # debug APK
./gradlew test                    # page-layout unit tests, no emulator needed
./gradlew :app:assembleRelease    # R8 + resource shrinking
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Toolchain is pinned to what the local Gradle cache already holds: Gradle 9.5.1, Kotlin 2.4.10, AGP 8.13.0, Compose BOM 2025.09.01.
Kotlin must stay at 2.4.x: Compose BOM 2025.09.01 pulls in a `kotlin-stdlib` compiled with 2.4 metadata, which a 2.2 compiler cannot read.
Configuration cache is off; the Kotlin plugin cannot serialise `KotlinCompile` on this Gradle version.

`compileSdk` 36, `minSdk` 26, `targetSdk` 36, package `dev.vighnesh.stackpage`.

## Architecture

```
pdf/PageLayout.kt    Pure Kotlin. Page geometry in PostScript points. Unit tested.
pdf/ImageSource.kt   Decoding: EXIF rotation + downsampling.
pdf/PdfExporter.kt   android.graphics.pdf.PdfDocument, one page per image.
ui/MainViewModel.kt  The page stack and export state.
ui/MainScreen.kt     Empty state, export bar, options sheet, export result.
ui/PageGrid.kt       Reorderable thumbnail grid.
```

`pdf/PageLayout.kt` deliberately imports no Android types.
The arithmetic that decides where an image lands on a page - aspect fit, centring, margins, orientation - is where the bugs in this app category live, and keeping it free of `Bitmap` and `Context` means it is tested on the JVM in under a second instead of on a device.

## Decisions worth knowing

- **No permissions.** The Photo Picker (`PickMultipleVisualMedia`) returns URIs the user chose; SAF (`CreateDocument`) writes to the file the user named. Neither needs `READ_MEDIA_IMAGES` or `WRITE_EXTERNAL_STORAGE`.
- **Platform PDF writer.** `android.graphics.pdf.PdfDocument` ships with the OS and carries no licence encumbrance. iText or PDFBox would only be warranted for OCR or encryption.
- **Images are downsampled to 2400px on the long edge** before export (`ImageSource.EXPORT_MAX_EDGE`). That is roughly 300dpi across an A4 short side - past what anyone prints or reads - and it is what stops a 50MP photo from OOMing the app at ~200MB of ARGB.
- **EXIF rotation is applied on decode.** Most phone cameras store the sensor orientation and put the real one in EXIF; without this, portrait photos export sideways.
- **Bitmaps decode as RGB_565.** A printed page has no alpha, and this halves the memory cost of every decode.
- **Drag-to-reorder is hand-written** against `LazyGridState` rather than pulled in as a dependency. It is about sixty lines, and owning it keeps the drag threshold, haptic and swap rule tunable.
- **No dynamic colour.** Wallpaper-derived palettes routinely produce a pastel primary that reads as "photo gallery", which is the wrong promise for a tool that makes documents.

## Design

Ink on paper.
Warm paper `#FAFAF8` in light mode, `#101418` in dark, near-black ink for text, and a single deep ink-blue `#1E3A5F` for anything actionable.
Green `#15803D` appears in exactly one place - the export-success state - so it never degrades into decoration.

Type is Plus Jakarta Sans, bundled as a variable font (one 176KB file, all weights) rather than downloaded, because an offline-only utility should not need the network on first launch.
Headings tighten tracking as they grow; body copy keeps default tracking at 1.4-1.5 line height.

Layout is a single column with one primary CTA per screen and no bottom navigation - there is one flow, so a nav bar would be furniture.
4/8dp spacing rhythm, 48dp minimum touch targets, edge-to-edge with safe-area insets honoured on the export bar.

Font licence: `OFL.txt` (SIL Open Font License 1.1).

## Roadmap

1. **Crop and rotate** - uCrop or canhub's Android-Image-Cropper, applied per page before export.
2. **Compress and resize** - target-size search over `Bitmap.compress`, with a live output-size estimate.
3. **Format conversion** - JPG/PNG/WebP out, sharing the same decode path.
4. **Batch and background** - WorkManager for large exports so a 100-page job survives the app being backgrounded.
5. **Strip EXIF/GPS on export** - a real privacy feature, and a natural extension of the current decode step.

Before any of these: get the app onto a real device or an emulator and look at it.
