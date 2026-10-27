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
                          StreamInput (gamepad + TV remote input), PadManager (7-slot gamepad
                          tracking mirroring the protocol's userId model — PS5 co-op uses a distinct saved
                          PSN account per secondary player),
                          AwayController, RumbleFeedback
    connect/              WakeConnectController, RemoteConnectController (PSN hole-punch),
                          LocalConnectController (deep-link + auto-connect)
    regist/               Console registration (PIN flow)
    discovery/            UDP discovery service wrapper
    ui/                   Compose TV UI: screens/, theme/ (PS5-style dark theme, Inter font via
                          theme/Type.kt, D-pad focus), components/ (one file per widget:
                          TvFocusCard, PsButton, PsIconButton, PsAlertDialog, settings rows),
                          navigation/Screen.kt (screen stack model), FocusRestore.kt
                          (rememberFocusRestore + FocusRequester.tryRequestFocus)
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
git submodule update --init native/third-party/opus

# build image (once):
docker build --platform linux/amd64 -f docker/Dockerfile.build -t chiaki-tv-build-amd64 docker/

# build APK — use build.ps1 (mounts .docker-cache/gradle so dependencies persist between builds).
# It keeps a persistent container (chiaki-tv-build) so the Gradle daemon survives between runs:
# first build ~4 min, incremental ~40 s.
.\build.ps1                          # assembleDebug
.\build.ps1 assembleRelease          # release APK (signed with the repo debug.keystore)
.\build.ps1 "assembleDebug --offline" # extra gradlew args are passed through
.\build.ps1 -SkipNative              # skip CMake/NDK tasks (~25 s) — only when native/ and external/ are unchanged
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
  forwarding, the `sessionRequestIDR` / `sessionRestartAudio` JNI functions, and the
  `enableIDROnFECFailure` connect-info field + `CHIAKI_EVENT_VIDEO_FEC_FAILURE` forwarding
   (`idr_on_fec_failure` pref, default on), and the mic sender (`sessionStartMic` /
   `sessionFeedMicFrame` / `sessionToggleMicrophone` on a per-session `ChiakiOpusEncoder`,
   48 kHz stereo 480-sample frames; needs `CHIAKI_LIB_ENABLE_OPUS=ON`, which builds the
   `native/third-party/opus` submodule satisfied via the `native/cmake/FindOpus.cmake` shim).
   `sessionStartMic` also takes `complexity` + `bandwidth` (from the mic settings) and applies
   them with `opus_encoder_ctl` on the raw `OpusEncoder*` (the `chiaki-jni` target links `opus`
   + the shim include dir directly); bitrate/bit-depth stay fixed at 32 kbps / 16-bit because
   `audiosender.c` hardcodes 40-byte frames. Upstream `ctrl.c` sends the mic-mute toggle
   **inverted** (`0` = unmute on the PS5), so the JNI flips the flag before
    `chiaki_session_toggle_microphone`.
    **Mic pitch shift** (experimental): `native/jni/mic-pitch.c` wraps the vendored
    `native/third-party/sonic` library (waywardgeek/sonic, Apache-2.0, added to the `chiaki-jni`
    target) in a `ChiakiMicPitch` class — one Sonic stream (48 kHz stereo) + a ring buffer so the
    opus encoder keeps getting exact 10 ms frames despite the shifter's variable output;
    mutex-guarded, a no-op when disabled. `sessionFeedMicFrame` shifts in place before encoding and
    a new `sessionSetMicPitch(ptr, enabled, factor)` JNI applies it; `sessionStartMic` lazily
    creates the stream and `sessionFree` frees it. Kotlin: `session/MicPitch.kt` (StateFlow
    enabled/semitones, equal-temperament factor `2^(st/12)`, persists to Preferences, pushes live to
    the session); HUD **Pitch** chip toggles on cross + steps ±1 semitone on up/down.
    Kotlin mic side: `session/MicCapture.kt` (AudioRecord VOICE_COMMUNICATION + platform
    AEC/NS/AGC + a ~2 s silence probe that aborts on a phantom mic recording only zeros),
    owned by `StreamSession` — starts on ConnectedEvent, stops on shutdown/pause.
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
  mutes + arms the 60 s away timer (`disconnect_action` pref Ask/Sleep/Do Nothing → goto-bed
  via the live session, or via a short-lived reconnect session if it died); `onResume`
  restores volume + `restartAudio()`. Never call `session.pause()`/shutdown while the surface
  may still be attached from another thread — teardown races the surface callbacks.
- **Connect flows live in `connect/` controllers**, not `MainActivity`: `WakeConnectController`
  (wakeup packet + READY polling + `wakingHost`/`bannerText` state), `RemoteConnectController`
  (PSN hole-punch flow + `cancel()` tearing down the regist session), `LocalConnectController`
  (deep-link + startup auto-connect). Progress for both is one modal `TvProgressDialog`
  (spinner + text + Cancel; Back cancels) from `ui/components/dialogs/TvProgressDialog.kt`, shown in `ScreenHome` —
  home cards are never disabled during wake/connect.
- **CMake entry point** is `native/CMakeLists.txt` (a trimmed copy of upstream's root
  CMakeLists: third-party + lib + our `native/jni`). It must keep `CHIAKI_ENABLE_ANDROID=ON`
  semantics and the `CHIAKI_LIB_*_EXTERNAL_PROJECT` includes.
- **`native/overrides/`** holds our copies of selected C core files (`gkcrypt.c`, `takion.c`,
  `chiaki/gkcrypt.h` — perf: cached GMAC EVP ctx, stack keystream, reusable takion recv buffer).
  `native/CMakeLists.txt` swaps them into the `chiaki-lib` target (SOURCES + a `BEFORE PUBLIC`
  include dir) so the submodule stays clean. Re-base them onto upstream at every bump — see
  `docs/UPSTREAM_SYNC.md`.
- **Kotlin layer is a manual port** of `external/chiaki-ng/android/app/src/main/java`.
  It is NOT kept in sync automatically — see `docs/UPSTREAM_SYNC.md` for the file-by-file
  mapping and the list of intentional divergences (StateFlow instead of LiveData,
  D-pad-as-controller input, no touch controls, package rename).
- New settings need four places: `common/settings/Preferences.kt` (key + property),
  `ui/screens/settings/SettingsViewModel.kt` + category panel (row), the string in **both** `res/values/strings.xml`
  and `res/values-nl/strings.xml` (Dutch is a full translation — a missing key silently
  falls back to English), and wiring into `Preferences.videoProfile` or the session if
  they affect streaming.
- **UI style**: PlayStation-inspired. **Everything themeable lives in `ui/theme/`** — colors +
  shapes (`CardShape`/`SelectionShape`/`ButtonShape`) + focus motion tokens (`FocusScale*`/
  `FocusGlow*`) in `Theme.kt`; all text uses `MaterialTheme.typography.*` (Inter `PsFontFamily`,
  sizes/weights/letterSpacing defined per-role in `Type.kt` — never hardcode `fontSize`/`sp` in
  components; `PsSectionHeaderStyle()`/`PsHudLabelStyle()` cover the two tracked-label roles).
  A theme manager only needs to swap the `Typography` + color/shape/motion objects. Home = thin top bar (clock + `PsIconButton`s) over a radial `PsBlue` glow,
  tile rows only (no hero). Settings = two-pane: left `SettingsCategory` nav **changes category
  on focus** (D-pad up/down switches panes immediately), Left from a row returns to the nav's
  selected item via the shared `navFocus` requester; focus memory keys `settings_cat` +
  `settings_row_<ordinal>`. Selections are grey translucent (`Color.White` alpha ~0.14,
  HUD chips 0.6 slate) with `SelectionShape` — do not use solid white fills, they burn on TV.
  Numeric settings use `TvStepperRow` (D-pad Left/Right steps with hold-acceleration, cross
  resets to the default) rather than `TvCycleRow`; discrete pickers (device lists, enums) stay
  `TvCycleRow`. Focus feedback: `TvFocusCard` and `PsIconButton` scale up + glow on focus via
  `animateFloatAsState`/`animateDpAsState` driven by an `onFocusEvent` observer that MUST be the
  first modifier in the chain (see Gotchas); the card scale is non-uniform (`focusScaleX`/
  `focusScaleY` params, currently 1.04/1.10) with `zIndex` so it draws above neighbors, and the
  home rows carry 12dp horizontal scroll padding so a focused edge card is not clipped.

## Gotchas

- The focus observer in `TvFocusCard`/`PsIconButton` (`onFocusEvent { focused = it.hasFocus }`)
  MUST be the **first** modifier in the chain. Placed after `.clip()`/`.clickable()` it never
  observes focus, so `focused` stays false and the scale/glow silently never apply (only Compose's
  built-in focus highlight shows) — this cost hours to diagnose.
- `Chiaki.kt` must stay in package `com.metallic.chiaki.lib` — the native `chiaki-jni.h`
  hardcodes `BASE_PACKAGE "com/metallic/chiaki/lib"`; renaming it causes `UnsatisfiedLinkError` at runtime.
- `ksp.useKSP2=false` in gradle.properties — Room 2.7.1 + Moshi 1.15.2 combo requires KSP1 here.
- Parcelize is applied as `id 'kotlin-parcelize'` (no version) — versioned alias breaks resolution.
- Room DAO methods must not be named `import` (use `importAll`); `@Transaction` concrete methods must be `open`.
- CMake options for the native build are passed in `app/build.gradle`; keep them aligned with
  `external/chiaki-ng/android/app/build.gradle` when bumping upstream.
- `app/.cxx/` and `app/build/` caches persist across runs on the mounted volume; delete them
  if CMake configure behaves oddly after a submodule bump.
- `build.ps1 -SkipNative` hardcodes the default ABIs (`arm64-v8a,armeabi-v7a`) in its `-x`
  exclusions, so it **fails** when combined with `-Pabis=arm64-v8a` (task-not-found for the
  v7a CMake task). For an arm64-only build just run `.\build.ps1 "assembleDebug -Pabis=arm64-v8a"`
  (native tasks stay up-to-date when `native/` is unchanged).
- Native codegen needs `protoc` + `python3` + `protobuf` pip package + `perl` + `make` — all in the Docker image only.
- `chiaki_session_stop` must **never** be called twice (SIGABRT in `chiaki_mutex_lock` on the
  destroyed mutex). `StreamSession.shutdown` is guarded by a `stopped` flag (reset in `resume()`),
  and `MainActivity.disconnectStream` by a `disconnecting` flag — the QuitEvent that arrives
  during shutdown re-enters the quit handling, so both guards are required.
- Release builds are signed with the repo `debug.keystore` (sideload convenience: release installs
  over debug without data loss). Replace with a real keystore before publishing anywhere.
- Shield TV intercepts the gamepad **PS button** (KEYCODE_BUTTON_MODE/HOME) at system level — the app
  never sees it. The DualSense **Create** button arrives as `KEYCODE_BACK` from the gamepad device;
  `StreamInput` maps gamepad-sourced BACK to `BUTTON_PS` (check `event.device` SOURCE_JOYSTICK via
  `PadManager.isGamepad` — no vendor IDs hardcoded). Remote-sourced BACK still opens the Sleep/Disconnect dialog. The DualSense
  **touchpad click** arrives as `KEYCODE_BUTTON_SELECT` and is sent as a real touchpad click
  (`BUTTON_TOUCHPAD` + center touch), not Create/Share.
- While the stream HUD is visible, only D-pad + cross are captured for HUD navigation
  (`MainActivity.dispatchKeyEvent`); every other button/stick event is routed to the game
  (gamepad sticks are forwarded in `dispatchGenericMotionEvent` because Compose's `horizontalScroll`
  would otherwise eat them). The DualSense **D-pad is HAT_X/HAT_Y motion axes**, not DPAD key
  events — `hudStickNavigate` synthesizes DPAD key events from the hat while the HUD is open.
  HUD chips share one `hudFocusIdx` state for the grey focus highlight and use `clickable`
  with `indication = null` (a ripple would linger after cross-press; plain `focusable()` never
  receives focus in this layout). All HUD UI + state (scroll, focus-restore from `FocusMemory`,
  stats polling, `HudItem`/`HudDivider`) lives in `ui/screens/stream/StreamHud.kt`, not `StreamScreen.kt`.
  The scroll edge arrows key off `hudFocusIdx` bounds (not `hudScroll.value`) because auto-scroll
  to a remembered item does not always reach the scroll edge. The open/close combo is configurable (`menu_shortcut` pref:
  L3+R3 / Select+Start / L1+R1+L2+R2) via `StreamInput.onHudComboKeyEvent`, which clears the
  combo's controller buttons/triggers/touch so the game never sees them. On open,
  `requestFocus()` is retried in a loop because the `FocusRequester` is only attached to the
  selected chip after recomposition — a single call races it and leaves no chip focused
  (D-pad dead until a cross-press grabs focus).
- Wake-on-connect (`MainActivity.wakeupAndConnect`) sends the wakeup packet, polls discovery for
  READY ≤25 s, then auto-starts the stream; used by the home-screen card, startup auto-connect
  (`auto_connect_enabled`/`auto_connect_mac` prefs) and the TV-channel deep link
  (`EXTRA_AUTO_CONNECT_MAC`, handled via `pendingDeepLinkMac` so it runs with the ViewModel).
- TV home channel (`platform/tv/TvChannel.kt`, androidx.tvprovider): programs need a `previewVideoUri`
  (FileProvider URI to `res/raw/tv_preview_*.mp4`) or Google TV renders nothing; delete programs
  by row ID (`ContentUris.withAppendedId`) — `delete()` with a WHERE selection throws
  `SecurityException: Selection not allowed`.

## Verify changes

1. Build the APK (command above) — Kotlin/Room/KSP errors show as `e:` lines; grep for `FAILED|BUILD`.
2. Smoke-test on a TV/emulator: discovery finds console, registration completes with PIN,
   video+audio stream, gamepad and remote D-pad work, settings export/import round-trips.

## Kotlin ownership

- Screen ViewModels are created with `ui/navigation/rememberRouteViewModel` so leaving a route clears their jobs and native resources.
- `ui/screens/settings/SettingsViewModel` owns settings actions and account state; category panels only render state and forward actions.
- `common/psn/PsnTokenStore` only persists credentials/metadata. `PsnAccountRepository` coordinates login/refresh; `PsnAuth` handles OAuth and `PsnProfileClient` handles profiles. UI receives token-free account summaries.
- `session/CoopPlayers` owns player assignments and native join status. Android picker input, profile loading and notifications belong to `ui/screens/stream/`.
- Backup DTOs do not query Room. `SettingsBackupRepository` maps between backup DTOs and DAO records; Activity-result handling lives in `ui/screens/settings/SettingsTransferController`.
- Host management lives in `ui/screens/hosts/`, PSN login in `ui/screens/psn/`, launcher integration in `platform/tv/`, avatar loading in `ui/images/`.
