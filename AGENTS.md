# AGENTS.md

Android TV port of [chiaki-ng](https://github.com/chiaki-ng/chiaki-ng) (PS4/PS5 Remote Play).
The app is Kotlin + Jetpack Compose; the streaming core is chiaki-ng's C library, built via NDK/CMake.

## Repository layout

```
app/                      Android TV app (namespace com.chiaking.tv)
  build.gradle            Gradle module; wires CMake to external/chiaki-ng/CMakeLists.txt
  src/main/java/com/chiaking/tv/
    common/               Room DB, Preferences, settings import/export, MAC type
    session/              StreamSession (StateFlow), StreamInput (gamepad + TV remote input)
    regist/               Console registration (PIN flow)
    discovery/            UDP discovery service wrapper
    ui/                   Compose TV UI: screens/, theme/ (dark PS-blue, D-pad focus)
  src/main/java/com/metallic/chiaki/lib/Chiaki.kt
                          JNI bindings — package MUST stay `com.metallic.chiaki.lib`
                          (chiaki-jni.h hardcodes BASE_PACKAGE "com/metallic/chiaki/lib")
external/chiaki-ng        git submodule = upstream C core + JNI (do not edit casually)
docker/Dockerfile.build   Linux build image (SDK 35, NDK 28.2, protoc, perl, python)
docs/UPSTREAM_SYNC.md     How to integrate new upstream chiaki-ng commits — READ before syncing
```

## Build

All building happens in Docker (host is Windows ARM64; NDK + aapt2 are x86_64-only,
so the image **must** be `--platform linux/amd64` — qemu emulation, builds are slow):

```powershell
# one-time after clone:
git submodule update --init external/chiaki-ng
git -C external/chiaki-ng submodule update --init third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl

# build image (once):
docker build --platform linux/amd64 -f docker/Dockerfile.build -t chiaki-tv-build-amd64 docker/

# build APK (~30 min cold, faster incrementally):
docker run --rm --platform linux/amd64 -v "${PWD}:/src" -w /src chiaki-tv-build-amd64 sh gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk` (arm64-v8a; add ABIs with `-Pabis=arm64-v8a,armeabi-v7a`).
Never run `gradlew` directly on the host — no local Android SDK/NDK is set up.

## Architecture rules

- **Native layer is read-through**: `external/chiaki-ng/lib` (C core) and
  `external/chiaki-ng/android/app/src/main/cpp` (JNI, MediaCodec, Oboe) are compiled
  as-is via CMake. Upstream changes apply automatically on submodule bump.
- **Kotlin layer is a manual port** of `external/chiaki-ng/android/app/src/main/java`.
  It is NOT kept in sync automatically — see `docs/UPSTREAM_SYNC.md` for the file-by-file
  mapping and the list of intentional divergences (StateFlow instead of LiveData,
  D-pad-as-controller input, no touch controls, package rename).
- New settings need three places: `common/Preferences.kt` (key + property),
  `ui/screens/SettingsScreen.kt` (row), and wiring into `Preferences.videoProfile`
  or the session if they affect streaming.

## Gotchas

- `Chiaki.kt` must stay in package `com.metallic.chiaki.lib` — the native `chiaki-jni.h`
  hardcodes `BASE_PACKAGE "com/metallic/chiaki/lib"`; renaming it causes `UnsatisfiedLinkError` at runtime.
- `ksp.useKSP2=false` in gradle.properties — Room 2.7.1 + Moshi 1.15.2 combo requires KSP1 here.
- Parcelize is applied as `id 'kotlin-parcelize'` (no version) — versioned alias breaks resolution.
- Room DAO methods must not be named `import` (use `importAll`); `@Transaction` concrete methods must be `open`.
- CMake options for the native build are passed in `app/build.gradle`; keep them aligned with
  `external/chiaki-ng/android/app/build.gradle` when bumping upstream.
- `app/.cxx/` and `app/build/` caches persist across runs on the mounted volume; delete them
  if CMake configure behaves oddly after a submodule bump.
- Native codegen needs `protoc` + `python3` + `protobuf` pip package + `perl` + `make` — all in the Docker image only.

## Verify changes

1. Build the APK (command above) — Kotlin/Room/KSP errors show as `e:` lines; grep for `FAILED|BUILD`.
2. Smoke-test on a TV/emulator: discovery finds console, registration completes with PIN,
   video+audio stream, gamepad and remote D-pad work, settings export/import round-trips.
