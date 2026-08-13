# Runbook

Operating procedures: build, install, drive the app on hardware, cut a release, and get unstuck.

## 1. Build

```bash
./gradlew test                    # page-layout unit tests, ~1s, no device
./gradlew :app:assembleDebug      # debug APK
./gradlew :app:assembleRelease    # R8 and resource shrinking
```

Output lands at `app/build/outputs/apk/debug/app-debug.apk`.

The system JDK builds this.
Do not set `JAVA_HOME` to something else on a hunch; AGP 8.13.0 is fine on GraalVM 25 here.

## 2. Install on a device

```bash
ADB=~/Android/Sdk/platform-tools/adb
$ADB devices -l                   # confirm the device is listed
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n dev.vighnesh.stackpage/.MainActivity
```

If `adb devices` is empty, the USB cable dropped or the device revoked debugging.
The transport id changes on reconnect, which is harmless, but a command issued during the gap fails with `no devices/emulators found`.
Re-run `$ADB wait-for-device` before a scripted sequence.

There is no AVD and no system image on this machine, and no `cmdline-tools` to fetch one.
A physical device is currently the only way to see this app run.

## 3. Seed synthetic test images

Do this before taking any screenshot that might be shared.
It keeps personal photos out of the picker's first rows and makes runs reproducible.

```bash
ADB=~/Android/Sdk/platform-tools/adb
python3 - <<'EOF'
import struct, zlib
OUT="/tmp/stackpage-fixtures"
import os; os.makedirs(OUT, exist_ok=True)
def png(path, w, h, fn):
    rows=[]
    for y in range(h):
        r=bytearray([0])
        for x in range(w):
            r+=bytes(fn(x/w, y/h))
        rows.append(bytes(r))
    raw=b"".join(rows)
    def ch(t,d):
        c=t+d
        return struct.pack(">I",len(d))+c+struct.pack(">I",zlib.crc32(c)&0xffffffff)
    open(path,"wb").write(
        b"\x89PNG\r\n\x1a\n"
        + ch(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
        + ch(b"IDAT", zlib.compress(raw, 6))
        + ch(b"IEND", b"")
    )
pal=[(30,58,95),(51,65,85),(21,128,61),(143,179,222),(180,90,60),(90,70,140)]
for i,(r,g,b) in enumerate(pal):
    # alternate portrait and landscape so AUTO orientation gets exercised
    w,h = (900,1200) if i%2==0 else (1200,900)
    png(f"{OUT}/doc{i+1}.png", w, h,
        lambda u,v,r=r,g=g,b=b: (int(r+(255-r)*v*0.8), int(g+(255-g)*v*0.8), int(b+(255-b)*(u*0.5+v*0.4))))
print("wrote 6 fixtures to", OUT)
EOF

for i in 1 2 3 4 5 6; do
  $ADB push /tmp/stackpage-fixtures/doc$i.png /sdcard/Pictures/zz_doc$i.png
  $ADB shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d file:///sdcard/Pictures/zz_doc$i.png
done
```

The `zz_` prefix and the media scan put them at a predictable place in the picker.
Alternating portrait and landscape is deliberate: it is what exercises `PageOrientation.AUTO`.

Clean up afterwards:

```bash
$ADB shell rm /sdcard/Pictures/zz_doc*.png
```

## 4. Drive the flow over adb

Screen coordinates below are for a Galaxy Tab A7 Lite (SM-T225) in portrait, 800x1340 as reported by `screencap`.
Re-derive them for any other device; do not assume they transfer.

```bash
ADB=~/Android/Sdk/platform-tools/adb
shot() { $ADB shell screencap -p /sdcard/s.png && $ADB pull -q /sdcard/s.png "$1"; }

$ADB shell am force-stop dev.vighnesh.stackpage
$ADB shell am start -n dev.vighnesh.stackpage/.MainActivity
sleep 3; shot /tmp/01-empty.png

$ADB shell input tap 400 837          # Choose images
sleep 6; shot /tmp/02-picker.png      # ALWAYS screenshot before tapping into the picker
```

Two habits that save a lot of wasted runs:

- **Screenshot before every tap into a system UI.** The Photo Picker takes several seconds to appear, and its grid order changes as new screenshots land in the gallery. Tapping blind selects the wrong image or dismisses the sheet.
- **Verify the foreground activity** rather than guessing whether a sheet opened:

  ```bash
  $ADB shell dumpsys activity activities | grep -o "topResumedActivity=.*" | head -1
  ```

  `com.google.android.photopicker` means the picker is up. Your own package means the tap missed.

Continue: tap the fixture thumbnails, tap `Done`, then `Export PDF`, then `SAVE` in the SAF sheet.

## 5. Verify an exported PDF

Do not trust the success card alone.
Pull the file and read its structure.

```bash
ADB=~/Android/Sdk/platform-tools/adb
$ADB shell ls -la /sdcard/Download/ | grep -i stackpage
$ADB pull "/sdcard/Download/Stackpage.pdf" /tmp/out.pdf

python3 - <<'EOF'
import re
d = open('/tmp/out.pdf','rb').read()
print('header  :', d[:8])
print('producer:', re.findall(rb'/Producer\s*\(([^)]*)\)', d))
print('pages   :', len(re.findall(rb'/Type\s*/Page[^s]', d)))
for box in re.findall(rb'/MediaBox\s*\[[^\]]*\]', d):
    print('mediabox:', box.decode())
EOF
```

What good looks like:

- Header `%PDF-1.4`, producer `Skia/PDF ...`.
- One `/MediaBox` per page.
- A4 portrait is `[0 0 595 842]` and A4 landscape is `[0 0 842 595]`. Letter is 612x792, Legal is 612x1008.
- A **0-byte file is the signature of a failed export**, not a partial one. The writer only opens the output stream after every page has been built, so nothing is written if any image fails to decode.

## 6. Release build

```bash
./gradlew :app:assembleRelease
```

There is no release keystore in this repo and no signing config in `app/build.gradle.kts`.
The release APK is therefore unsigned and will not install.

Before the first real release, decide where the keystore lives.
Without a persistent one, rebuilds will not install over each other with `adb install -r`.
That is a decision for the repo owner, not something to generate silently: a throwaway keystore that is later lost means the app can never be updated in place.

## 7. Troubleshooting

**Dozens of "class was compiled with an incompatible version of Kotlin" errors pointing at your own files.**
The Kotlin version was downgraded below 2.4.
Compose BOM 2025.09.01 pulls a `kotlin-stdlib` with 2.4 metadata that a 2.2 compiler cannot read.
The errors name your files but the cause is the version in `gradle/libs.versions.toml`.
Keep `kotlin = "2.4.10"`.

**Gradle stalls part-way through a large download.**
The network here has done this before.
Check `~/.gradle/caches/modules-2/files-2.1/` for what is already present and prefer a cached version over a newer one.

**Configuration cache errors from `KotlinCompile`.**
It is off on purpose in `gradle.properties`.
The Kotlin plugin cannot serialise its classpath snapshot on this Gradle version.
Do not turn it back on without testing a clean build.

**"Export failed: Could not read image N of M."**
`ImageSource.decode` returned null.
Historically this was a bug in `decode` itself rather than a bad image: a bounds-only `BitmapFactory.decodeStream` returns a null `Bitmap` by definition, so an elvis placed after the `use` block aborted on every image.
Check that the elvis guards the stream and not the block before blaming the file.
Genuine causes are an unreadable URI or a format the platform decoder does not support.

**The app is not in the foreground after a tap.**
The SAF and picker sheets can drop you to the launcher if a tap lands outside them.
Re-launch with `am start` and take a screenshot before continuing.
