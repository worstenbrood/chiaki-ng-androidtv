# Integrating upstream chiaki-ng updates

The chiaki-ng C core and JNI layer live in `external/chiaki-ng` as a **git submodule**
(pinned commit). The Android TV app in `app/` is a separate, hand-maintained codebase
that consumes that tree.

## One-time setup (fresh clone)

```powershell
git submodule update --init --recursive external/chiaki-ng
```

Only these nested submodules are needed for the TV build (the rest — `oboe`,
`test/munit`, `cpp-steam-tools` — are unused or provided by Gradle prefab):

```powershell
git -C external/chiaki-ng submodule update --init third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl
```

## Updating to a newer upstream commit

```powershell
git -C external/chiaki-ng fetch origin
git -C external/chiaki-ng checkout <new-commit-or-tag>   # e.g. v1.10.1 or origin/main
git -C external/chiaki-ng submodule update --init third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl
git add external/chiaki-ng
git commit -m "Bump chiaki-ng to <version>"
```

Then follow the integration checklist below and rebuild (see "Verify").

## What is picked up automatically

The C core is built directly from `external/chiaki-ng` via CMake
(`native/CMakeLists.txt` → `add_subdirectory(external/chiaki-ng/lib)`), so a submodule
bump applies it with **no app changes**:

| Area | Upstream location |
|---|---|
| C core (session, takion, ctrl, discovery, regist, FEC, RPCrypt, ...) | `external/chiaki-ng/lib/` |
| Build options | `external/chiaki-ng/CMakeLists.txt` (mirrored in `native/CMakeLists.txt`) |

**Exception:** if upstream adds/renames a CMake option, mirror it in
`native/CMakeLists.txt` and the `externalNativeBuild { cmake { arguments ... } }` block
of `app/build.gradle`.

**Exception 2:** we carry local performance and co-op overrides for selected C core files **without dirtying the
submodule**. `native/overrides/` holds full copies that replace the upstream ones at build
time (`native/CMakeLists.txt` removes `src/gkcrypt.c`/`src/takion.c` from the `chiaki-lib`
SOURCES and appends ours, plus a `BEFORE PUBLIC` include dir so `overrides/include/chiaki/gkcrypt.h`
shadows the upstream header for `chiaki-lib` and all its consumers):

| Override | Divergence from upstream |
|---|---|
| `native/overrides/gkcrypt.c` + `include/chiaki/gkcrypt.h` | `chiaki_gkcrypt_gmac()` caches its `EVP_CIPHER_CTX` in the struct (`gmac_ctx` + `gmac_key_cached`, OpenSSL path only) and only redoes the AES-GCM key schedule on gmac key refresh — upstream did a fresh ctx + key schedule **per packet**. Free'd in `chiaki_gkcrypt_fini()`. Safe because each gkcrypt instance is used from one thread (remote: takion recv thread; local: callers hold `gkcrypt_local_mutex`). `chiaki_gkcrypt_decrypt()` uses a 1600-byte stack keystream instead of malloc-per-packet. |
| `native/overrides/takion.c` | recv loop reads into a reusable stack buffer — no `malloc`/`realloc` per datagram. `takion_handle_packet()` contract changed from "takes ownership of buf" to "buf is borrowed": the retain sites (`takion_handle_packet_message_data`, `takion_handle_packet_av` video-queue path, `takion_postpone_packet`) `malloc`+`memcpy` their own copy (with `payload`/`packet.data` rebased into the copy); the postponed-packet flush loop frees the copies. |
| `native/overrides/feedbacksender.c` + `multislot.c` + `include/chiaki/multislot.h` | Session-owned co-op sender with shared state/history sequence counters, per-event player tags, history retransmission and host-accepted join/leave lifecycle. The delegate preserves the upstream feedback-sender struct ABI. Run `native/tests/run_multislot_tests.py` after configuring the native build. |
| `native/overrides/ctrl.c` | Forwards co-op add/leave/profile/passcode replies into the session-owned state machine before the normal upstream dispatcher; keep the rest rebased on upstream. |


**On each submodule bump:** diff the upstream originals against our copies and re-apply our
divergences onto the new versions (compile errors from a stale `gkcrypt.h` are expected and
signal a needed re-base):

```powershell
git -C external/chiaki-ng diff HEAD~1 HEAD -- lib/src/gkcrypt.c lib/src/takion.c lib/include/chiaki/gkcrypt.h
# or compare directly:
git diff --no-index external/chiaki-ng/lib/src/gkcrypt.c native/overrides/gkcrypt.c
git diff --no-index external/chiaki-ng/lib/src/takion.c native/overrides/takion.c
git diff --no-index external/chiaki-ng/lib/include/chiaki/gkcrypt.h native/overrides/include/chiaki/gkcrypt.h
```

## JNI layer — manual sync

The JNI layer is **not** compiled from the submodule. `native/jni/` is a copy of
`external/chiaki-ng/android/app/src/main/cpp/` with local additions:

- `audio-output.{h,cpp}`: volume (software gain in the Oboe callback), output-device
  selection (`setDeviceId` with fallback to the default device) and buffer size
  (ms → Oboe buffer capacity + buffer size at open)
- `video-decoder.{h,c}`: frames/bytes/lost counters + `android_chiaki_video_decoder_get_stats`;
  **keep-alive surface handling**: `set_surface(null)` no longer kills the codec (upstream did, but
  double-locked the non-recursive `codec_mutex` → ANR; and killing loses reference frames → black
  screen until next IDR). Ours sets `surface_valid=false` + `AMediaCodec_setOutputSurface(NULL)`
  and keeps decoding with `releaseOutputBuffer(render=false)`; a new surface swaps via
  `setOutputSurface` + `surface_valid=true`. `kill_decoder` split into `kill_decoder_locked`
  (still used by `fini`). Per-frame "decoder not initialized" log downgraded to verbose.
- `audio-output.{h,cpp}`: `android_chiaki_audio_output_restart()` — drains the ring buffer and
  reopens the Oboe stream (the AudioTrack dies server-side when the app is backgrounded, the
  callback thread stalls in `obtainBuffer` and never recovers); `onErrorAfterClose` schedules a
  delayed auto-reopen (500 ms, `weak_ptr`-guarded) because a stream opened right at `onResume`
  gets disconnected again by the audio server; `AudioOutput` is `shared_ptr`-managed for that;
  buffer-overflow log → verbose
- `chiaki-jni.c`: `sessionSetVolume`, `sessionSetAudioDevice`, `sessionSetAudioBuffer`,
  `sessionGetStats`, `sessionRequestIDR`, `sessionRestartAudio` JNI functions + forwarding of
  `CHIAKI_EVENT_MOTION_RESET` (`eventMotionReset` → re-centers `StreamInput` motion sensors) +
  `enableIDROnFECFailure` connect-info field (`connect_info.enable_idr_on_fec_failure`) and
  forwarding of `CHIAKI_EVENT_VIDEO_FEC_FAILURE` (`eventVideoFecFailure(idrRequestSent)`)

When bumping upstream, diff the two directories and re-apply our additions:

```powershell
git diff --no-index external/chiaki-ng/android/app/src/main/cpp native/jni
```

## What must be ported manually

The Kotlin layer was copied into `app/src/main/java/com/chiaking/tv/` and adapted.
When upstream changes these files, diff and port by hand:

| Upstream file | Our file | Notes |
|---|---|---|
| `android/.../lib/Chiaki.kt` | `com/metallic/chiaki/lib/Chiaki.kt` | JNI bindings. **Package must remain `com.metallic.chiaki.lib`** (hardcoded in `chiaki-jni.h`). New `external fun`s / data classes must be mirrored **and** match the native signatures in `chiaki-jni.c`. New enums (e.g. extra codecs/targets) go here first. |
| `android/.../common/Preferences.kt` + `res/xml/preferences.xml` | `com/chiaking/tv/common/Preferences.kt` + `ui/screens/SettingsScreen.kt` | Every new upstream setting needs: a property in `Preferences.kt` (plain string keys, no `R.string`), and a row in `SettingsScreen.kt` (`TvSwitchRow` / `TvCycleRow`). Wire it into `videoProfile` if it affects streaming. |
| `android/.../session/StreamInput.kt` | `com/chiaking/tv/session/StreamInput.kt` | Keep our TV additions (see divergences below) when porting. |
| `android/.../session/StreamSession.kt` | `com/chiaking/tv/session/StreamSession.kt` | We use `StateFlow` instead of `LiveData` (see divergences). |
| `android/.../regist/*` | `com/chiaking/tv/regist/` | Registration flow; `RegistExecuteViewModel` uses `StateFlow`. |
| `android/.../common/SerializedSettings.kt` | `com/chiaking/tv/common/SerializedSettings.kt` | Import/export format. If upstream bumps `VERSION`, update ours and add a migration in `ImportDao`. |
| `CHIAKI_VERSION_*` in `external/chiaki-ng/CMakeLists.txt` | `versionName` in `app/build.gradle` + `UPSTREAM_CHIAKI_VERSION` in `ui/screens/SettingsScreen.kt` | Bump to `<major>.<minor>.<patch>-tv` on each sync. |

UI-only upstream changes (layouts, touch controls, MainActivity) are **not**
ported — the TV UI is Compose and independent.

## Intentional local divergences (do not revert when porting)

- Package rename `com.metallic.chiaki.*` → `com.chiaking.tv.*`, **except** `lib/Chiaki.kt`
  which must stay in `com.metallic.chiaki.lib` (JNI symbol names are hardcoded in `chiaki-jni.h`).
- `StreamSession`/`RegistExecuteViewModel` expose `MutableStateFlow` instead of `LiveData` (Compose).
- `StreamInput`: TV remote D-pad/`KEYCODE_MENU` → controller D-pad/OPTIONS mapping, gated by the
  `dpad_as_controller` preference.
- `Preferences`: plain string keys (no `R.string` resource keys), TV defaults
  (1080p/60/H.265), extra `dpad_as_controller` and `display_mode` keys.
- `SerializedSettings`: no `Activity`/`FileProvider` share code — export/import go
  through SAF in `MainActivity`; DAO method is `importAll` (Room rejects `import`).
- No touch-controls modules (`touchcontrols/`), no `MageTheme` PIN activity.

## Verify

```powershell
docker run --rm --platform linux/amd64 -v "${PWD}:/src" -w /src chiaki-tv-build-amd64 sh gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.
Smoke-test: discovery finds a console, registration completes, stream shows video,
gamepad + remote D-pad work, settings round-trip (export → import).
