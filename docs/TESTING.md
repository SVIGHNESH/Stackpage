# Testing

What is covered automatically, what needs hardware, and what is currently broken.

## Automated coverage

```bash
./gradlew test
```

15 tests in `app/src/test/java/dev/vighnesh/stackpage/PageLayoutTest.kt`, all passing, about half a second.
They cover `layoutPage` only, which is the whole of the page geometry:

| Concern | What is asserted |
| --- | --- |
| Page dimensions | A4 595x842, Letter 612x792, Legal 612x1008, all in points |
| Orientation | Landscape swaps the page; `AUTO` follows the image; a square image defaults to portrait |
| Aspect ratio | The destination rect's ratio equals the source's, so nothing is ever stretched |
| Centring | Left gap equals right gap, top gap equals bottom gap |
| Margins | Honoured on all four sides for every preset |
| Fit-to-image | Page takes the image's shape, and grows to hold the margin |
| Margin clamp | An absurd margin cannot invert the rect into negative width |
| Small input | A tiny image is scaled up to fill the content box rather than sitting at 1:1 |
| Bad input | Zero or negative dimensions throw `IllegalArgumentException` |
| Exhaustive sweep | Every page size x orientation x margin combination yields a positive rect that stays on the page |

That last sweep is the one that catches regressions cheaply.
Any new `PageSize` or `Margin` is automatically covered by it.

## Instrumented coverage (M0)

```bash
./gradlew :app:connectedDebugAndroidTest
```

Six tests in `app/src/androidTest/`, running the real `BitmapFactory` and `ExifInterface` path against synthetic fixtures bundled as androidTest assets.

- `ImageSourceTest`: plain decode at expected size, EXIF orientation 6 comes back upright as 1200x900, a 6000x8000 source downsamples, garbage bytes return null instead of throwing, and `readSize` agrees with `decode` on orientation.
- `PdfExporterTest`: exports two fixtures and asserts the output starts with `%PDF` and holds two `/Type /Page` entries.

This is the suite that would have caught the shipped decode regression in seconds.

One finding pinned by the suite rather than fixed: `inSampleSize` is power-of-two, so the decoded long edge lands in [`EXPORT_MAX_EDGE`, 2x`EXPORT_MAX_EDGE`), not at the bound itself.
The page layout's destination rect does the final scaling, so this costs memory headroom, not correctness.

**Status: passing.**
All six ran green on the SM-T225 (Android 14) on 2026-08-14, `OK (6 tests)` in 1.2s.
The decode regression class moves from "no coverage" to "covered".
Note for flaky USB: if the Gradle connected task reports "No connected devices" or a streamed install fails with an empty error, push both APKs to `/data/local/tmp` and use `pm install -r` plus `am instrument -w dev.vighnesh.stackpage.test/androidx.test.runner.AndroidJUnitRunner` instead.

## What has no automated coverage

- `PdfExporter` cancellation and bitmap recycling under memory pressure.
- Every composable: the home screen, the empty state, the grid, drag reorder, the options sheet, and the three export overlays.
- The two system pickers and the URI grants they hand back.

## Enforcing the Android-free seam

`pdf/PageLayout.kt` must not import Android types.
There is no automated check for this yet.
Until there is, the manual check is:

```bash
grep -n "^import android" app/src/main/java/dev/vighnesh/stackpage/pdf/PageLayout.kt
```

It should print nothing.

## Manual QA matrix

Status as of the last hardware run on a Galaxy Tab A7 Lite (SM-T225, Android 12, dark theme).

| Case | Status | Notes |
| --- | --- | --- |
| Home screen renders, card opens the stack | Not run | New in M1, needs the device |
| Empty state renders | Pass | Type scale, palette and centring correct |
| Pick a single image | Pass | Photo Picker returns, thumbnail appears |
| Pick six images | Pass | Grid fills left to right, page numbers 1-6 correct |
| Export one image to A4 | Pass | Valid PDF, `/MediaBox [0 0 595 842]`, 1.02 MB |
| Auto orientation | Pass | A portrait source produced a portrait page |
| Export failure message | Pass | Reported clearly when decode failed |
| Drag to reorder | Not run | Needs a device pass |
| Options sheet | Not run | Page size, orientation, margin chips unverified |
| Non-A4 page sizes end to end | Not run | Only the unit tests cover Letter and Legal |
| Fit-to-image page size | Not run | |
| Light theme | Not run | Every screenshot so far is dark theme |
| Landscape device orientation | Not run | |
| Cancel mid-export | Not run | |
| Large export, 50+ pages | Not run | `PdfDocument` buffers the whole document in memory |
| Share and Open from the success card | Not run | |
| Back button dismisses each overlay | Not run | Implemented, unverified |
| Dynamic type at largest size | Not run | |
| Talkback labels | Not run | Content descriptions are written but unheard |
| Compress: 4MB photo to 200 KB within 10% | Not run | M2 gate |
| Compress: screenshot PNG converts and hits target | Not run | M2 gate |
| Compress: batch of 6 saves into the chosen folder | Not run | M2 gate |
| Compress: unreachable target says "best possible" | Not run | |
| Compress: single real photo, custom 20 KB target | Pass | 292 KB → 16 KB, saved via SAF tree, "All saved" shown |
| Convert: each format round-trips on device | Not run | M4 gate |
| Convert: HEIC from the camera converts | Not run | M4 gate |
| Convert: preset resize fits the box, no upscale | Not run | M4 gate; fitWithin is JVM-tested |
| Scan: paper to legible A4 under 500 KB with B&W filter | Not run | M3 gate |
| Scan: scanner activity launches under the stripped manifest | Pass | GMS DocumentScanningActivity confirmed in its own process |
| Scan: first-use model download completes | Blocked | One-time Play services download; the tablet had no internet, Play Store logged NETWORK_ERROR and the scan bounced to the store. Connect Wi-Fi once and retry |
| Scan: works offline after first-use download | Not run | M3 gate; airplane mode, then scan |
| Rotate from the grid lands rotated in the export | Not run | M3 gate; check /MediaBox and by eye |
| Process death behind the picker restores the route | Not run | See known issue 1a |
| Save-all batch duration is tolerable | Not run | searchPlan re-probes per image; seed from the previous winner if slow |

## Known issues

**1. First thumbnail does not fill its card.**
In the six-image grid run, page 1's thumbnail rendered letterboxed inside its card with a visible vertical seam, while pages 2-6 filled correctly.
Its remove button also rendered without the circular scrim behind it.
Suspected to be a Coil sizing or placeholder issue on the first composed item rather than anything in the layout, but it is unconfirmed.
Cosmetic, does not affect the exported PDF.
Re-logged after the M1 refactor: the grid now lives at `feature/stack/PageGrid.kt`, behaviour unchanged; reproduction needs the device, which is currently offline.

**1a. Navigation reset to home behind the photo picker, observed once.**
On the first-ever picker launch after installing 1.1 on the SM-T225, "Choose images" cold-started the picker (~14s covered), and the app came back on the *home* screen instead of the stack screen.
Suspicion: activity recreation while covered, with nav state not restored.
Not yet reproduced - the second launch behaved correctly.
Check on the next pass: kill the process while the picker is open (`am kill`) and confirm the stack route is restored on return.

**2. Release APK is unsigned.**
No keystore and no signing config. See `docs/RUNBOOK.md` section 6.

**3. Page count is unbounded.**
The picker caps a single selection at 100, but repeated "Add more" taps can exceed that, and `PdfDocument` holds the entire document in memory before writing.
No ceiling has been measured.

## Regression log

Bugs found and fixed, kept because each one names a trap worth remembering.

**Export failed on every image: "Could not read image 1 of 1".**
`ImageSource.decode` read the image bounds with `openInputStream(uri)?.use { decodeStream(it, null, bounds) } ?: return null`.
A bounds-only pass returns a null `Bitmap` by definition, so the elvis fired on every image however healthy it was.
The elvis has to guard the stream, not the block.
Invisible to the compiler and to every unit test; obvious in one run on hardware.
This is the case for the instrumented test named above.

**The export overlay did not block input.**
A coloured `Surface` does not participate in hit testing in Compose, so "Export PDF" stayed tappable underneath the progress card and relaunched the file picker mid-export.
Fixed with a pointer-input modifier that swallows events, plus a `BackHandler` so the overlay is never a dead end.
