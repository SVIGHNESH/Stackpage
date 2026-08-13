# Decision log

Why the project is shaped the way it is.
Read this before proposing an architectural change: several sensible-sounding suggestions were considered and rejected here for a stated reason.

Each entry records the decision, the alternative, and what would have to change for the decision to be revisited.

---

## 1. Native Kotlin and Compose, not Flutter or React Native

Everything this app does is file I/O, bitmap memory management, and platform storage APIs.
A cross-platform layer adds a bridge in front of exactly the parts that are hard, and the PDF writer would end up being a platform channel anyway.

**Rejected:** Flutter, React Native.
**Revisit if:** an iOS version becomes a requirement, at which point the page-geometry logic is the part worth porting and it is already isolated.

---

## 2. No permissions in the manifest, including no INTERNET

The Photo Picker (`PickMultipleVisualMedia`) hands back URIs for exactly the images the user chose.
SAF (`CreateDocument`) writes to exactly the file the user named.
Neither needs `READ_MEDIA_IMAGES` or `WRITE_EXTERNAL_STORAGE`.

Omitting `INTERNET` is the important half.
It turns "nothing leaves your device" from a promise into something the OS enforces, and it makes an accidental analytics or crash-reporting dependency fail loudly at build time rather than silently ship.

**Rejected:** requesting media permissions for a custom in-app gallery.
**Revisit if:** never, without an explicit decision from the repo owner. This is the product's one claim.

---

## 3. The platform PDF writer

`android.graphics.pdf.PdfDocument` ships with the OS, has no licence encumbrance, and needs no parser.

iText is AGPL, which is a real constraint on a public repo.
PDFBox is heavy and aimed at reading and editing rather than writing a stack of images.

**Rejected:** iText, PDFBox, a hand-rolled PDF writer.
**Revisit if:** OCR, encryption, or editing an existing PDF enters scope. None are currently planned.

---

## 4. Page geometry isolated in pure Kotlin

`pdf/PageLayout.kt` imports no Android types, so all of the aspect-fit, centring, margin and orientation logic runs under JVM unit tests in half a second.

This is where apps in this category actually go wrong, and the failures are silent: a stretched image or an eaten margin produces a PDF that opens fine and looks wrong.
Tests are the only practical defence, and tests are only practical if they do not need an emulator.

**Rejected:** doing the maths inline in `PdfExporter` alongside the `Canvas` calls.
**Revisit if:** never. This is a load-bearing invariant, listed as such in `CLAUDE.md`.

---

## 5. One module, not a `:core` / `:app` split

A separate Gradle module is the stronger way to enforce decision 4, since the compiler would reject an Android import outright.
It was rejected because the Android-free surface is a single file, and a module boundary to protect one file is ceremony that slows every build.

**Rejected:** a `:core` JVM module.
**Revisit if:** the Android-free logic grows past two or three files, or an iOS port makes a shared module worthwhile.

---

## 6. No persistence

The page stack lives in the ViewModel and dies with the process.

This is correct for a one-shot tool, and it has a privacy consequence worth stating: there is no cache, no database and no temp directory holding copies of the user's images.
The only file this app ever writes is the one the user explicitly named.

**Rejected:** a DataStore or Room-backed session that survives process death.
**Revisit if:** users report losing a large stack to a background kill. The fix would be persisting the URI list only, never the pixels.

---

## 7. Downsample to 2400px on the long edge

A 50MP photo at full resolution is roughly 200MB as ARGB and will OOM the app.
2400px is about 300dpi across an A4 short side, which is past what anyone prints or reads on screen.

Bitmaps also decode as `RGB_565`, because a printed page has no alpha and this halves the memory cost of every decode.

**Rejected:** full-resolution export, a user-facing quality slider.
**Revisit if:** someone wants archival-quality scans. The bound is a single constant, `ImageSource.EXPORT_MAX_EDGE`, deliberately.

---

## 8. Drag reorder written by hand

The interaction is a long-press detector, a hit test against `LazyGridState.layoutInfo`, and a list move.
About sixty lines.

A library would be a dependency whose API churns between majors, for something this small, in the one interaction most worth tuning.

**Rejected:** `sh.calvin.reorderable` and similar.
**Revisit if:** the interaction needs auto-scroll at the viewport edges and multi-select drag, at which point the line count argument stops holding.

---

## 9. No dynamic colour

Material You derives a palette from the wallpaper.
For this app that routinely produces a pastel primary that reads as "photo gallery", which is the wrong promise for a tool that makes documents.

The fixed palette is ink on paper: warm paper in light, near-black ink for text, one deep ink-blue for anything actionable, and green in exactly one place, the export-success state, so it never becomes decoration.

**Rejected:** `dynamicLightColorScheme` / `dynamicDarkColorScheme`.
**Revisit if:** the repo owner wants it. This is taste, not structure.

---

## 10. Plus Jakarta Sans bundled, not downloaded

Downloadable fonts would make the first launch of an offline-only utility depend on the network, which contradicts decision 2.
The variable font is one 176KB file covering every weight the app uses, smaller than shipping four statics.

**Rejected:** Google Fonts downloadable provider, four static weights.
**Revisit if:** APK size becomes a constraint, which at 19MB debug it is not.

---

## 11. Kotlin pinned to 2.4.x

Compose BOM 2025.09.01 pulls a `kotlin-stdlib` compiled with 2.4 metadata.
A 2.2 compiler cannot read it, and the resulting errors point at your own source files rather than at the real cause, which costs an hour if you have not seen it before.

**Rejected:** staying on Kotlin 2.2.20 to match a sibling project.
**Revisit if:** the Compose BOM is downgraded, which there is no reason to do.

---

## 12. Single screen, no navigation

There is one flow: pick, order, export.
A bottom navigation bar or a drawer would be furniture around a task that has no branches.

**Rejected:** a nav host with separate screens for the grid and the options.
**Revisit if:** the roadmap's editing features land. Crop and rotate need a per-image editor, which is the first genuine second destination.

---

## 13. The app opens on a home screen

The single-screen decision (12) is reversed: the roadmap turns Stackpage into a small collection of document tools, and every one of them needs somewhere to launch from.
`MainActivity` is now theme plus nav host, `home` is the start destination, and the PDF flow lives at `stack`.

The cost is one extra tap for the existing flow.
That tap is accepted as the price of every future tool having a front door, instead of the first tool owning the whole app.

**Rejected:** keeping `stack` as the start destination until a second tool exists, which would make the framework refactor invisible and unexercised.
**Revisit if:** usage shows the PDF flow is still effectively the only tool after the roadmap's first wave lands.
