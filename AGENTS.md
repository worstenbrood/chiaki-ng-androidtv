# AGENTS.md

Android TV port of [chiaki-ng](https://github.com/chiaki-ng/chiaki-ng) (PS4/PS5 Remote Play).
The app is Kotlin + Jetpack Compose; the streaming core is chiaki-ng's C library, built via NDK/CMake.

## Repository layout

```
app/                      Android TV app (namespace com.chiaking.tv)
  build.gradle            Gradle module; wires CMake to external/chiaki-ng/CMakeLists.txt
  src/main/java/com/chiaking/tv/
    common/               Room DB, Preferences, settings import/export, MAC type
    session/              StreamManager (process-wide session owner), StreamSession (StateFlow),
                          StreamInput (gamepad + TV remote input)
    regist/               Console registration (PIN flow)
    discovery/            UDP discovery service wrapper
    ui/                   Compose TV UI: screens/, theme/ (dark PS-blue, D-pad focus)
  src/main/java/com/metallic/chiaki/lib/Chiaki.kt
                          JNI bindings — package MUST stay `com.metallic.chiaki.lib`
                          (chiaki-jni.h hardcodes BASE_PACKAGE "com/metallic/chiaki/lib")
external/chiaki-ng        git submodule = upstream C core + JNI (do not edit casually)
native/                   Our CMake entry point + copy of the JNI layer (audio volume/device/buffer/restart + video stats + surface keep-alive additions)
docker/Dockerfile.build   Linux build image (SDK 35, NDK 28.2, protoc, perl, python)
docs/UPSTREAM_SYNC.md     How to integrate new upstream chiaki-ng commits — READ before syncing
docs/FEATURE_PORTING_PLAN.md  Qt→TV feature port roadmap (status per feature)

## Build

All building happens in Docker (host is Windows ARM64; NDK + aapt2 are x86_64-only,
so the image **must** be `--platform linux/amd64` — qemu emulation, builds are slow):

```powershell
# one-time after clone:
git submodule update --init external/chiaki-ng
git -C external/chiaki-ng submodule update --init third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl

# build image (once):
docker build --platform linux/amd64 -f docker/Dockerfile.build -t chiaki-tv-build-amd64 docker/

# build APK — use build.ps1 (mounts .docker-cache/gradle so dependencies persist between builds).
# It keeps a persistent container (chiaki-tv-build) so the Gradle daemon survives between runs:
# first build ~4 min, incremental ~40 s.
.\build.ps1                          # assembleDebug
.\build.ps1 assembleRelease          # release APK (signed with the repo debug.keystore)
.\build.ps1 "assembleDebug --offline" # extra gradlew args are passed through
.\build.ps1 -Fresh                   # recreate the container (after an image rebuild or repo move)
.\build.ps1 -Stop                    # stop/remove the build container

# equivalent raw command (no daemon reuse):
docker run --rm --platform linux/amd64 -v "${PWD}:/src" -v "${PWD}/.docker-cache/gradle:/root/.gradle" -w /src chiaki-tv-build-amd64 sh gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release.apk`
(arm64-v8a; add ABIs with `-Pabis=arm64-v8a,armeabi-v7a`).
Never run `gradlew` directly on the host — no local Android SDK/NDK is set up.

## Architecture rules

- **Native layer**: `external/chiaki-ng/lib` (C core) is compiled as-is via CMake — upstream
  changes apply automatically on submodule bump. The **JNI layer is our own copy** in
  `native/jni/` (copied from `external/chiaki-ng/android/app/src/main/cpp`) with local
  additions: audio **volume** (software gain in the Oboe callback), **output device**
  selection with default-device fallback, audio **buffer size** (ms → Oboe buffer capacity),
  video **stats counters** (frames/bytes/lost for the HUD), `CHIAKI_EVENT_MOTION_RESET`
  forwarding and the `sessionRequestIDR` / `sessionRestartAudio` JNI functions.
  **Keep-alive rework** in `video-decoder.c` (required by StreamManager — do not revert):
  `set_surface(null)` no longer kills the codec (upstream did, and double-locked the
  non-recursive `codec_mutex` → ANR); it sets `surface_valid=false` +
  `AMediaCodec_setOutputSurface(NULL)` and keeps decoding with render-off, and a new surface
  swaps via `setOutputSurface` (+ `requestIDR()` from Kotlin as insurance). `fini` releases
  the window ref (upstream leaked it). In `audio-output.cpp`: `AudioOutput` is `shared_ptr`-
  managed, `restart()` drains + reopens the Oboe stream, and `onErrorAfterClose` schedules a
  delayed auto-reopen (the audio server kills the track while the app is backgrounded, and a
  stream opened right at `onResume` gets killed again ~80 ms later). When bumping upstream,
  diff and re-apply our changes to those files (details in `docs/UPSTREAM_SYNC.md`).
- **StreamManager** (`session/StreamManager.kt`) is the process-wide owner of the active
  `StreamSession` (survives Activity recreation + backgrounding; `MainActivity` reads
  `StreamManager.session/input/displayHost`). Keep-alive across backgrounding depends on the
  `video-decoder.c` keep-alive rework above — do not revert it. `onPause` with a live stream
  mutes + arms the 60 s away timer (`sleep_console_on_exit` pref Never/Ask/Always → goto-bed
  via the live session, or via a short-lived reconnect session if it died); `onResume`
  restores volume + `restartAudio()`. Never call `session.pause()`/shutdown while the surface
  may still be attached from another thread — teardown races the surface callbacks.
- **CMake entry point** is `native/CMakeLists.txt` (a trimmed copy of upstream's root
  CMakeLists: third-party + lib + our `native/jni`). It must keep `CHIAKI_ENABLE_ANDROID=ON`
  semantics and the `CHIAKI_LIB_*_EXTERNAL_PROJECT` includes.
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
- `chiaki_session_stop` must **never** be called twice (SIGABRT in `chiaki_mutex_lock` on the
  destroyed mutex). `StreamSession.shutdown` is guarded by a `stopped` flag (reset in `resume()`),
  and `MainActivity.disconnectStream` by a `disconnecting` flag — the QuitEvent that arrives
  during shutdown re-enters the quit handling, so both guards are required.
- Release builds are signed with the repo `debug.keystore` (sideload convenience: release installs
  over debug without data loss). Replace with a real keystore before publishing anywhere.
- Shield TV intercepts the gamepad **PS button** (KEYCODE_BUTTON_MODE/HOME) at system level — the app
  never sees it. The DualSense **Create** button arrives as `KEYCODE_BACK` from the gamepad device;
  `StreamInput` maps gamepad-sourced BACK to `BUTTON_PS` (check `event.device` vendor 0x054c /
  SOURCE_GAMEPAD). Remote-sourced BACK still opens the Sleep/Disconnect dialog. The DualSense
  **touchpad click** arrives as `KEYCODE_BUTTON_SELECT` and is sent as a real touchpad click
  (`BUTTON_TOUCHPAD` + center touch), not Create/Share.
- While the stream HUD is visible, only D-pad + cross are captured for HUD navigation
  (`MainActivity.dispatchKeyEvent`); every other button/stick event is routed to the game
  (gamepad sticks are forwarded in `dispatchGenericMotionEvent` because Compose's `horizontalScroll`
  would otherwise eat them). The DualSense **D-pad is HAT_X/HAT_Y motion axes**, not DPAD key
  events — `hudStickNavigate` synthesizes DPAD key events from the hat while the HUD is open.
  HUD chips share one `hudFocusIdx` state for the grey focus highlight and use `clickable`
  with `indication = null` (a ripple would linger after cross-press; plain `focusable()` never
  receives focus in this layout). The open/close combo is configurable (`menu_shortcut` pref:
  L3+R3 / Select+Start / L1+R1+L2+R2) via `StreamInput.onHudComboKeyEvent`, which clears the
  combo's controller buttons/triggers/touch so the game never sees them.
- Wake-on-connect (`MainActivity.wakeupAndConnect`) sends the wakeup packet, polls discovery for
  READY ≤25 s, then auto-starts the stream; used by the home-screen card, startup auto-connect
  (`auto_connect_enabled`/`auto_connect_mac` prefs) and the TV-channel deep link
  (`EXTRA_AUTO_CONNECT_MAC`, handled via `pendingDeepLinkMac` so it runs with the ViewModel).
- TV home channel (`common/TvChannel.kt`, androidx.tvprovider): programs need a `previewVideoUri`
  (FileProvider URI to `res/raw/tv_preview_*.mp4`) or Google TV renders nothing; delete programs
  by row ID (`ContentUris.withAppendedId`) — `delete()` with a WHERE selection throws
  `SecurityException: Selection not allowed`.

## Verify changes

1. Build the APK (command above) — Kotlin/Room/KSP errors show as `e:` lines; grep for `FAILED|BUILD`.
2. Smoke-test on a TV/emulator: discovery finds console, registration completes with PIN,
   video+audio stream, gamepad and remote D-pad work, settings export/import round-trips.
