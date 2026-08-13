# CLAUDE.md

Guidance for Claude Code (claude.ai/code) and for anyone picking this repo up cold.

## What this is

An offline Android utility that turns images into a PDF.
One screen, one flow: pick images, order them, export.
Package `dev.vighnesh.stackpage`, minSdk 26, targetSdk 36.

The product's single claim is that nothing leaves the device.
That claim is structural, not a promise: the manifest declares no permissions at all, including no `INTERNET`.
Any change that adds a permission, an analytics SDK, a crash reporter, or a network call breaks the only thing this app promises.
Do not add one without the repo owner saying so explicitly.

## Current state

The core loop is built and verified on hardware: pick, reorder, export.
15 JVM unit tests cover the page-layout arithmetic and pass.
Debug and release both assemble.

Not built: crop, rotate, resize, compress, format conversion.
See `docs/ROADMAP.md` before starting any of them.

Known open issues are listed in `docs/TESTING.md` under "Known issues".
Read that before reporting anything as new.

## Documents

Four documents carry the reasoning; this file only points at them.

- `docs/ARCHITECTURE.md` - the module layout, the data flow, and the invariants that hold the export path together.
- `docs/DECISIONS.md` - the decision log. Read it before proposing an architectural change, because several "obvious" suggestions were considered and rejected here for a stated reason.
- `docs/RUNBOOK.md` - build, install, drive the app on a real device over adb, cut a release, and the toolchain traps that will otherwise cost an hour.
- `docs/TESTING.md` - what is covered by tests, what needs a device, the manual QA matrix, and the known issues.

## Load-bearing invariants

Breaking any of these breaks either correctness or the product's one claim.

- **No permissions, ever.** The Photo Picker returns URIs the user chose and SAF writes to the file the user named. Neither needs a permission. There is no `INTERNET` permission, so a network call cannot be added by accident.
- **`pdf/PageLayout.kt` imports no Android types.** The page arithmetic is where this app category's bugs live, and keeping it free of `Bitmap` and `Context` is what lets it be tested in under a second. Do not import `android.*` into that file, and do not move the maths into `PdfExporter`.
- **Every image is downsampled before it reaches the PDF.** A 50MP photo at full resolution is roughly 200MB of ARGB and will OOM the app. `ImageSource.EXPORT_MAX_EDGE` is the bound and it exists for that reason.
- **EXIF rotation is applied on decode.** Phone cameras store the sensor orientation and put the real one in EXIF. Skip this and portrait photos export sideways.
- **Bitmaps are recycled after each page.** `PdfExporter` recycles in a `finally`. A 100-page export depends on it.
- **The export overlay must block input.** A coloured `Surface` does not participate in hit testing in Compose. Without the pointer-input modifier in `Scrim`, "Export PDF" stays tappable underneath and relaunches the file picker mid-export.

## Toolchain

Pinned to what the local Gradle cache holds: Gradle 9.5.1, Kotlin 2.4.10, AGP 8.13.0, Compose BOM 2025.09.01.

Kotlin must stay on 2.4.x.
Compose BOM 2025.09.01 pulls a `kotlin-stdlib` compiled with 2.4 metadata, and a 2.2 compiler cannot read it.
Downgrading Kotlin fails the build with dozens of "class was compiled with an incompatible version" errors that point at your own files rather than at the real cause.

The system JDK (GraalVM 25) builds this fine.
No `JAVA_HOME` juggling is needed.

Configuration cache is off.
The Kotlin plugin cannot serialise `KotlinCompile` on this Gradle version.

Before bumping anything, check `~/.gradle/caches/modules-2/files-2.1/` for what is already present.
The network on this machine has stalled part-way through large downloads before.

## Commands

```bash
./gradlew test                    # page-layout unit tests, no device needed
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # R8 and resource shrinking
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`docs/RUNBOOK.md` has the full adb sequence for driving the pick-reorder-export flow on a device, including how to seed synthetic test images so no personal photo ends up in a screenshot.

## House rules for changes

- Reproduce a bug end to end on a device before touching the implementation. The one export bug found so far was invisible to the compiler, invisible to the unit tests, and obvious in one run on hardware.
- Anything that changes page geometry gets a test in `PageLayoutTest` first. That file is the specification for what a correct page looks like.
- After any UI change, install it and look at it. Screenshots go in `docs/TESTING.md`, not just in the commit message.
- Do not hand-edit generated output. There is none in this repo yet; keep it that way.
