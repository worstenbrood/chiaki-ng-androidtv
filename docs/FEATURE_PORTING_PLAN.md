# Qt App → Android TV Feature Porting Plan

Companion to `docs/UPSTREAM_SYNC.md`. Source of truth for Qt features: `external/chiaki-ng/gui/`
(QML UI in `gui/src/qml/`, C++ in `gui/src/`). Current TV app: `app/src/main/java/com/chiaking/tv/`.

Pick-up instructions for a new session: each item has **What** (Qt behavior + reference files),
**Status** here, **Verdict** (PORT / PARTIAL / MAYBE / SKIP), and **How** (concrete steps).
New settings always need the 3 places from AGENTS.md: `common/Preferences.kt` key+property,
`ui/screens/SettingsScreen.kt` row, wiring into `videoProfile`/session.

Legend:
- **PORT** = clearly worth it, feasible on Android TV
- **PARTIAL** = port a reduced/simplified version
- **MAYBE** = depends on hardware/UX fit, decide later
- **SKIP** = desktop-only or no Android equivalent

Key native fact: `lib/src/remote/holepunch.c` + `rudp.c` **are already compiled** into our
`libchiaki` (they are unconditional in `external/chiaki-ng/lib/CMakeLists.txt:90-92`), but
`native/jni/chiaki-jni.c` exposes none of it. PSN remote play is therefore "JNI + Kotlin work",
not "new native dependency work" (curl/jsonc already linked).

---

## Already ported (baseline — do not re-plan)

Discovery + wake-up on standby, registration (PIN, all targets), manual hosts (add/edit/delete),
stream session with video profile (res/fps/codec/bitrate), audio volume/output-device/buffer-size
(our JNI additions), stats HUD (L3+R3), login PIN dialog, Sleep-vs-Disconnect dialog, goto-bed,
gamepad input incl. motion + rumble + PS-button remap + touchpad click, D-pad-as-controller,
per-session log files (keep 5) + LogsScreen, settings export/import (SAF), screen-on keep-alive.

---

## 1. Streaming quality & network

### 1.1 Per-console-type + local/remote video profiles — **PORT (high priority)**
- **What**: Qt keeps separate resolution/fps/bitrate/codec per `PS4/PS5 × local/remote`
  (`gui/include/settings.h:308-343`, `SettingsDialog.qml:1165-1503`). Remote play over internet
  needs much lower bitrates than LAN; PS4 maxes at 1080p60 H.265 differently than PS5.
- **Status**: single global `resolution/fps/bitrate/codec` in `Preferences.kt`.
- **Verdict**: PORT. Even without PSN remote play, PS4-vs-PS5 profiles are useful today.
- **How**:
  1. Key scheme `resolution_local_ps5`, `fps_local_ps5`, `bitrate_local_ps5`, `codec_ps5`, … (remote variants later).
  2. `Preferences.videoProfile` becomes `videoProfile(isPS5: Boolean, remote: Boolean = false)`.
  3. Call sites: `StreamSession.start` (has `RegisteredHost`/`ManualHost` → know PS5 flag via `Target`),
     `StreamScreen` HUD target-Mbps label.
  4. `SettingsScreen`: restructure Stream section into PS4 / PS5 groups (cycle rows already exist — just duplicate per group).
  5. `SerializedSettings.kt`: bump envelope version, migrate old flat keys on import.
- **Effort**: S-M. No native changes.

### 1.2 Finer bitrate control — **PORT**
- **What**: Qt uses a continuous slider (kbps) per profile; we cycle Auto→5k→10k→20k→50k.
- **Status**: coarse cycle in `SettingsScreen.kt`.
- **How**: replace cycle row with a +/- stepper row (D-pad friendly: left/right decrement/increment by 500 or 1000 kbps, long-press faster). Keep Auto as first stop.
- **Effort**: S.

### 1.3 Packet loss max (`packetLossReportedMax`) — **PORT**
- **What**: max packet loss % reported to console before console lowers bitrate
  (`settings.h:386`, fed to `ChiakiConnectInfo.packet_loss_max`, `streamsession.cpp:477`).
- **Status**: absent; `ConnectInfo` in `Chiaki.kt` likely doesn't carry it — verify against `native/jni/chiaki-jni.c` connect-info parsing.
- **How**: add field to Kotlin `ConnectInfo` + parse in `native/jni/chiaki-jni.c` `Java_..._sessionStart` connect-info extraction (this is a **native/jni change** — note in UPSTREAM_SYNC divergence list); Preferences key + SettingsScreen cycle row (e.g. 0/2/5/10 %).
- **Effort**: S-M (touches our JNI copy).

### 1.4 IDR-on-FEC-failure (`iDROnFECFailureEnabled`) — **PORT**
- **What**: request keyframe when FEC fails instead of waiting (`settings.h:302`, `streamsession.cpp:475, 2400-2412`; lib event `CHIAKI_EVENT_VIDEO_FEC_FAILURE` if present in `lib/include/chiaki/session.h`).
- **Status**: absent.
- **How**: set flag in connect info (JNI addition as in 1.3); optionally surface FEC-failure event in HUD "frames lost" line. Check `session.h` event enum for exact name before starting.
- **Effort**: S-M.

### 1.5 Weak-WiFi warning — **MAYBE**
- **What**: spinner warning when measured loss > `wifiDroppedNotif` (`StreamView.qml:301`).
- **How**: HUD already polls `session.getStats()`; add a warning badge when lost-delta ratio exceeds threshold. No native work.
- **Effort**: S. Do alongside 2.1.

### 1.6 Audio/video disabled modes — **SKIP**
- Debug oddity (`settings.h:241`), not a real TV feature.

### 1.7 Port guessing for remote play (`portGuessingEnabled/Count/SocketCount`) — **DEFER**
- Only matters with PSN remote play (10.x). Fold into that project.

---

## 2. Session HUD & stream menu

### 2.1 Richer stats HUD — **PORT**
- **What**: Qt overlay shows Mbps, avg packet loss %, dropped/lost frames, queue depth
  (`qmlmainwindow.cpp:104-289` StatsOverlayWidget; `streamsession.cpp:669-693, 2752-2760` 200 ms polling of congestion control + `chiaki_session_request_idr`).
- **Status**: HUD shows fps/Mbps/total-lost only (`StreamScreen.kt`, 1 s poll).
- **How**:
  1. Extend `sessionGetStats` in `native/jni/chiaki-jni.c` (our copy) to also return dropped-packet/packet-loss figures from `ChiakiPacketStats`/`ChiakiCongestionControl` (see `lib/include/chiaki/packetstats.h`, `congestioncontrol.h`).
  2. HUD: packet-loss % + dropped frames rows; keep 1 s poll (TV, not dev tool).
  3. Add "Request IDR" hidden action? — skip manual IDR; auto (1.4) covers it.
- **Effort**: S-M (JNI stats extension pattern already exists).

### 2.2 Stream menu (slide-over quick settings during stream) — **PORT (high priority)**
- **What**: Qt slide-up menu: volume slider, mic mute, zoom/stretch, video preset, display settings
  (`StreamView.qml:448-752`, `StreamMenuWindow.qml`).
- **Status**: we only have the HUD (stats + disconnect). Volume is fixed at stream start
  (`setVolumePercent()` exists in `StreamSession.kt` but is uncalled).
- **How**:
  1. Reuse HUD scaffold in `StreamScreen.kt`; add a second panel "Quick Settings" (same D-pad focus rules — remember MainActivity skips `streamInput` while overlay visible).
  2. Rows: Volume stepper → `session.setVolumePercent()` (wire the dead function); Output device (cycle, `session.setAudioDevice` live — verify Oboe device switch at runtime in `native/jni/audio-output.cpp`, else apply-on-reconnect); Audio buffer (apply-on-reconnect); toggle D-pad-as-controller; toggle motion.
  3. Open via existing L3+R3 combo (cycle HUD → menu → close) or new KEYCODE_MENU long-press.
- **Effort**: M. Mostly Compose.

### 2.3 Go Home from menu — **PORT**
- **What**: `chiaki_session_go_home` (`streamsession.cpp:876-879`).
- **How**: JNI: add `sessionGoHome` in `native/jni/chiaki-jni.c` next to `sessionGotoBed`; Kotlin wrapper; menu item "Go Home". Trivial.
- **Effort**: S.

### 2.4 Quit-action preference (`disconnectAction`: Ask / Sleep / Do Nothing) — **PORT**
- **What**: on disconnect, optionally auto-sleep console or never ask (`settings.h:498`, `qmlbackend.cpp:1240-1264`).
- **How**: Preferences key `disconnect_action` (ASK/SLEEP/NOTHING); branch in `StreamScreen` disconnect dialog trigger / `MainActivity.disconnectStream`.
- **Effort**: S.

### 2.5 Auto-reconnect on early error — **PORT**
- **What**: if session quits with an error before connecting within 20 s, retry automatically
  (`streamsession.cpp:2336-2345`, `SESSION_RETRY_SECONDS=20`).
- **How**: in `StreamSession` QuitEvent handling: if quit happened < 20 s after start and reason is error → auto `shutdown + resumeDelayed(2000)` once (add `retried` flag). Keep manual Reconnect button as fallback.
- **Effort**: S.

### 2.6 Console PIN storage + auto-entry — **PORT (high priority)**
- **What**: store 4-digit Remote Play PIN per registered host; auto-submit when session requests login PIN
  (`ConsolePinDialog.qml`, `qmlbackend.cpp:1183-1188, 1299-1306`, `host.cpp:43-46`).
- **Status**: PIN dialog is always manual (`StreamScreen.kt` LoginPinRequestEvent).
- **How**:
  1. `RegisteredHost` Room entity: add `rpPin: String?` column (Room migration!), `RegistScreen`/`ManageHostsScreen` field to set/edit it.
  2. `StreamSession`: on `LoginPinRequestEvent`, if stored PIN present call `session.setLoginPin` immediately; show dialog only on `pinIncorrect` or when unset.
  3. Include in `SerializedSettings` export/import.
- **Effort**: S-M (Room migration + regist flow touch).

### 2.7 "Can't display" notice — **SKIP/MAYBE**
- Qt surfaces `cantDisplay` (HDR unsupported). Our HDR codec path is minimal; revisit if HDR issues appear.

---

## 3. Console management & automation

### 3.1 Auto-connect on startup — **PORT (high priority)**
- **What**: pick a console (`autoConnectMac`); on launch, when discovery reports it READY (wake it if standby, wait ≤25 s), connect automatically (`qmlbackend.cpp:2351-2360, 847, 1530-1552`; `AutoConnectView.qml` "Waiting for console…" + cancel).
- **How**:
  1. Preferences: `auto_connect_mac: String?` + `auto_connect_enabled: Boolean`.
  2. `MainViewModel`: after first discovery batch (or timeout ~3 s), if target found → standby? send wakeup + poll up to 25 s → navigate to stream. Show a "Waiting for console… [Cancel]" card (reuse HomeScreen empty state).
  3. Cancel = clear for this launch only.
- **Effort**: M. No native work (wakeup already exposed).

### 3.2 Wake-on-connect for standby hosts — **PORT**
- **What**: Qt wakes a standby console then connects (`qmlbackend.cpp:1530-1552`). We wake but don't auto-connect after wake completes.
- **How**: in `HomeScreen`/`MainViewModel` click handler: after `sendWakeup`, poll discovery for READY up to 25 s then start stream (same machinery as 3.1 — build 3.1's helper first).
- **Effort**: S (with 3.1).

### 3.3 Hide discovered console — **PORT**
- **What**: hide unregistered discovered consoles from list; unhide in settings (`settings.h:710-714`, `MainView.qml:232-233`, `SettingsDialog.qml:2096-2169`).
- **How**: `Preferences.hiddenMacs: MutableSet<String>` (SharedPreferences string set); filter in `MainViewModel` host list; card long-press → "Hide"; SettingsScreen row cycling hidden entries with Unhide.
- **Effort**: S.

### 3.4 Running-app / title-ID display — **MAYBE**
- **What**: discovery packets carry running-app title ID; Qt shows "playing X" (`discoverymanager.h:14-25`).
- **Blocker**: `DiscoveryHost` in `Chiaki.kt` may not expose `running_app`/`rustype` fields — check `native/jni` discovery callback; title-ID→name mapping needs a database (Qt ships none; it shows the raw title ID).
- **How**: expose `runningApp` in JNI discovery event → show "In-Game"/title ID badge. Name lookup out of scope.
- **Effort**: S-M. Low value unless title-ID table added.

### 3.5 Settings profiles — **MAYBE**
- **What**: multiple named settings files, switch/delete (`settings.cpp:984-2152`, `ProfileDialog.qml`).
- **TV angle**: useful for shared family TV (kid vs adult bitrate/PIN profiles) but big surface: every pref + DB row must become profile-scoped.
- **How (if ever)**: profile = suffix on SharedPreferences name + Room DB filename; selector on HomeScreen header. Defer until core list is done.
- **Effort**: L.

### 3.6 Import semantics fix (Qt parity) — **PORT (small)**
- Qt import replaces settings; ours is additive-only and silently swallows errors (`SerializedSettings.kt`).
- **How**: add "Replace existing?" choice on import; surface parse failures via a snackbar/dialog.
- **Effort**: S.

---

## 4. PSN / remote play over the internet

### 4.1 PSN OAuth login + token storage/refresh — **PORT (big, flagship)**
- **What**: OAuth via `psnaccountid.h` (LOGIN_URL/TOKEN_URL/CLIENT_ID/SECRET, generated DUID via
  `chiaki_holepunch_generate_client_device_uid`); tokens+expiry+account-id persisted; auto-refresh
  1 min before expiry (`psntoken.cpp`, `qmlbackend.cpp:1675-1710, 2524-2569`).
- **TV constraint**: no browser on TV and typing is hell. Port the **manual URL flow**: show short URL
  + code, user completes on phone, then paste redirect URL via Android TV soft keyboard (or pair-code flow if PSN offers one — verify current PSN endpoints still work; Qt's client id/secret are in `psnaccountid.h:17-25`).
- **How**:
  1. JNI: wrap `chiaki_holepunch_generate_client_device_uid`, token exchange/refresh are plain HTTPS — do them in Kotlin (OkHttp/Retrofit already? check deps; else HttpURLConnection) — no native needed for OAuth itself.
  2. `common/psn/PsnAuth.kt`: token model in EncryptedSharedPreferences (tokens are credentials — do NOT put in plain prefs or export file).
  3. SettingsScreen "PlayStation Network" section: login/logout, account ID display, expiry.
- **Effort**: L. **Verify PSN flow viability first** (spend 1 session spike: does the Qt flow still authenticate in 2026?).

### 4.2 Remote console list + remote connect (holepunch) — **PORT (big, depends on 4.1)**
- **What**: `chiaki_holepunch_list_devices` for PS5 devices; connect flow states Linking→Registering→DataConnection with cancel; failure hints for symmetric NAT (`qmlbackend.cpp:2571-2641`, `PsnView.qml`, `streamsession.cpp:2671-2747`).
- **How**:
  1. JNI additions in `native/jni/chiaki-jni.c`: `holepunchListDevices`, `holepunchLinkConsole`/`registerConsole`, `holepunchCreateDataSession` (mirror `streamsession.cpp` usage of `lib/include/chiaki/remote/holepunch.h`), pass returned session pointer into `ConnectInfo` (new `holepunchSession` field, native side).
  2. Kotlin `PsnViewModel` with state machine mirroring `PsnConnectState` (`qmlbackend.h:99-113`).
  3. HomeScreen: PSN section cards; `StreamSession` accepts PSN connect info.
  4. Then 1.1 remote profiles + 1.7 port guessing become relevant.
- **Effort**: XL. Highest-value differentiator (no other Android TV client does remote play away from LAN).

### 4.3 Auto-registration over PSN — **PORT (with 4.2)**
- `auto_regist` flag in connect info (`streamsession.cpp:478`, `qmlbackend.cpp:1417-1474`). Falls out of 4.2 with a flag.

### 4.4 Auto-connect over PSN (wait for internet, retry ≤6) — **PORT (with 4.2)**
- `qmlbackend.cpp:347-372, 604-629`. Extend 3.1 with PSN fallback when LAN discovery finds nothing.

---

## 5. Input & controllers

### 5.1 Rumble intensity + haptic override — **PORT**
- **What**: `rumbleHapticsIntensity` (Off→VeryStrong) and `hapticOverride` % (`settings.h:270, 305`, `streamsession.cpp:2358-2381`).
- **Status**: binary `rumbleEnabled`; amplitude = raw avg in `StreamSession` vibrator handling.
- **How**: Preferences `rumble_intensity` (0-4) multiplier applied to existing `Vibrator` amplitude. Real DualSense HD haptics/adaptive triggers: **SKIP** — needs native USB/BT HID access (Qt uses SDL/DualSense USB), not feasible for a sideloaded TV app.
- **Effort**: S.

### 5.2 Button haptics (wire the dead setting) — **PORT (small)**
- `buttonHapticEnabled` stored but unused. Either implement (click feedback on UI focus changes via `View.performHapticFeedback`) or remove the row. Implement — cheap polish.
- **Effort**: S.

### 5.3 Stream-menu gamepad shortcut config — **PORT (small)**
- **What**: configurable button combo to open stream menu (`settings.h:460-473`).
- **How**: Preferences `menu_shortcut` cycle: L3+R3 / Select+Start / L1+R1+L2+R2; implement combo detection in `StreamInput` (it already detects L3+R3).
- **Effort**: S.

### 5.4 D-pad → touchpad emulation — **MAYBE**
- **What**: D-pad moves a virtual touch point on PS touchpad, with increment + shortcut combos (`streamsession.cpp:1049-1139`, `settings.h:442-458`). For games using touchpad as buttons.
- **TV angle**: niche; TV users mostly have real gamepads whose touchpads already work (we map touchpad click). Defer; revisit if requested.
- **Effort**: M (Compose-free, all in `StreamInput`).

### 5.5 Keyboard / mouse input — **SKIP**
- Qt keyboard-as-controller and mouse→touchpad assume desktop peripherals; Android TV already routes keyboard/gamepad via `StreamInput` for the basics. Add plain keyboard → controller mapping only if someone actually plugs a BT keyboard into the TV (unlikely priority).

### 5.6 Per-controller button remapping UI — **SKIP**
- Qt remaps SDL mappings (`ControllerMappingDialog.qml`); we use Android `KeyEvent`/`MotionEvent` — remapping layer would be a from-scratch project with little payoff given `swapCrossMoon` + dpadAsController cover the common cases.

### 5.7 Multiple simultaneous controllers — **MAYBE**
- Qt supports N controllers (`controllermanager.cpp:201-351`). Our `StreamInput` assumes one active device. Local co-op over Remote Play isn't supported by Sony's protocol anyway (single controller per session) — verify before investing; likely **SKIP**.

### 5.8 Motion-control reset event — **PORT (small)**
- Console can request motion reset (`CHIAKI_EVENT_MOTION_RESET`, `streamsession.cpp:2413-2438`). Check if our JNI forwards it; if not, add event and re-center `StreamInput` sensors.
- **Effort**: S.

---

## 6. Audio

### 6.1 Live volume control — **PORT**
- Covered by 2.2 stream menu (wire `setVolumePercent`).

### 6.2 Microphone streaming (mic to console) — **MAYBE (big)**
- **What**: opus-encoded mic uplink, mute toggle, DualSense mic LED sync (`streamsession.cpp:824-868, 1396-1719`; `startMicUnmuted`, `audioInDevice`, speex AEC `settings.h:483-491`).
- **TV angle**: TV mics are far-field/noisy; AEC (speexdsp) not compiled in our build. Native work: expose `chiaki_session_connect_microphone`/opusencoder via JNI + AudioRecord capture in Kotlin.
- **Verdict**: MAYBE — only if a concrete use case (PS5 voice chat with TV headset mic) is desired. L effort.

### 6.3 Audio device hot-switch during stream — **PORT (small, verify)**
- We set device at start only. `native/jni/audio-output.cpp` is ours — check if `sessionSetAudioDevice` can restart Oboe stream live; if yes expose in stream menu (2.2).
- **Effort**: S-M.

---

## 7. Video rendering / display

### 7.1 libplacebo/Vulkan renderer, presets (FSR, deband, tone-mapping), ~60 tuning knobs, HDR display targets — **SKIP**
- Entire placebo pipeline (`qmlmainwindow.cpp` render half, `PlaceboSettingsDialog.qml`, `PlaceboColorMappingDialog.qml`) is desktop Vulkan. Android uses MediaCodec + SurfaceView. HDR passthrough on Android TV is a separate (MediaFormat-level) project, not a Qt port.

### 7.2 Zoom / stretch / black-bar modes — **PORT**
- **What**: `windowType` Fullscreen/Zoom/Stretch/SelectedResolution (`settings.h:367`, `qmlbackend.cpp:1554-1574`); live zoom + zoom-factor slider in stream menu.
- **How**: Compose `SurfaceView` scale mode: Fit (current) / Fill (crop, matrix scale) / Stretch (distort matrix). Settings key `display_mode` — note the dead `displayModeKey` constant in `Preferences.kt` was reserved for exactly this; implement it. Live toggle in stream menu (2.2).
- **Effort**: S-M.

### 7.3 4K / 120 fps profiles — **MAYBE (verify upstream)**
- Qt tops at 1080p60 too (360p-1080p enums). Check `lib` `ChiakiConnectVideoProfile` limits + console support before promising 4K. Likely upstream limitation → **SKIP** unless upstream adds it.

### 7.4 vSync / frame-pacing knobs — **SKIP**
- Android SurfaceFlinger owns pacing; MediaCodec output to Surface has no equivalent knob.

---

## 8. System integration

### 8.1 Console sleep when TV sleeps (`suspendAction`) — **PORT**
- **What**: host going to sleep sends console to sleep (`settings.h:501`, `qmlbackend.cpp:528-538`).
- **TV angle**: TVs don't "sleep" mid-session, but app `onStop`/process death leaves console streaming. Current behavior: session pause on stop. Add option: on `onStop` timeout (e.g. 60 s backgrounded) → `gotoBed()` + stop session. Preferences `sleep_console_on_exit` (Never / Ask / Always).
- **Effort**: S.

### 8.2 Sleep inhibition — **DONE**
- `FLAG_KEEP_SCREEN_ON` already set. (Qt's systemd/IOKit inhibit has no more Android equivalent.)

### 8.3 Steam shortcut, CLI mode, window geometry, macOS mic permission, WebEngine login — **SKIP**
- Desktop-only.

### 8.4 Startup "direct stream" intent — **PORT (small)**
- **What**: Qt `--profile`/direct-stream launch used by Steam shortcuts (`main.cpp:325-330`). Android analog: launcher shortcut per console (long-press app icon → `ShortcutManager` dynamic shortcuts, or Leanback launcher support) that deep-links straight into a stream of a given MAC.
- **How**: `MainActivity` intent extra `auto_connect_mac`; dynamic shortcuts built from `RegisteredHost` DAO.
- **Effort**: S-M. Nice TV ergonomics.

---

## 9. UI / misc

### 9.1 Streamer mode (hide IP/MAC/PIN echo) — **MAYBE**
- `settings.h:276`. Low value on TV; trivial HUD filter if wanted. Defer.

### 9.2 Error toast / error surface polish — **PORT (small)**
- Qt shows quit-reason strings via `chiaki_quit_reason_is_error/string` (`qmlbackend.cpp:1147-1181`). We have `quitReasonToString` in JNI already — ensure every QuitEvent shows a friendly localized-ish message + reason string in the error dialog (mostly there; audit strings).
- **Effort**: S.

### 9.3 First-run reminders — **SKIP**
- Tied to Steam/PSN onboarding; revisit PSN one only if 4.1 lands.

### 9.4 About dialog / app version row — **PORT (trivial)**
- Add version + upstream chiaki-ng version row to SettingsScreen.
- **Effort**: XS.

### 9.5 Translations — **SKIP**
- Qt is effectively English-only (no translator wiring found). Out of scope.

### 9.6 Log sanitize option (`logSanitize`) — **MAYBE**
- Redacts regist keys/PSN tokens from log files (`settings.h:261`). Cheap privacy win if logs get shared: redact in `LogManager.kt`. S.

---

## Suggested phasing

**Phase 1 — polish what exists (no native changes, ~small PRs each)**
1. Console PIN storage + auto-entry (2.6)
2. Auto-connect + wake-on-connect (3.1 + 3.2)
3. Stream menu: live volume, display mode zoom/stretch (2.2 + 7.2 + 6.1)
4. Per-PS4/PS5 video profiles + finer bitrate (1.1 + 1.2)
5. Quit-action pref, auto-retry, Go Home, rumble intensity, button-haptics wiring, import UX (2.3-2.5, 5.1, 5.2, 3.6, 9.2, 9.4)

**Phase 2 — JNI additions (touch `native/jni`, update UPSTREAM_SYNC divergence list)**
6. Richer stats + weak-WiFi warning (2.1 + 1.5)
7. packet_loss_max + IDR-on-FEC (1.3 + 1.4)
8. Motion reset event (5.8); audio device hot-switch check (6.3)
9. Hidden hosts, launcher deep-link shortcuts (3.3, 8.4)

**Phase 3 — flagship (spike first)**
10. PSN spike: verify OAuth + holepunch still work from this codebase (4.1)
11. PSN login/token UI (4.1) → remote list/connect (4.2) → auto-regist (4.3) → PSN auto-connect (4.4) → remote profiles + port guessing (1.1 remote keys, 1.7)

**Backlog / undecided**: mic streaming (6.2), dpad→touchpad (5.4), profiles (3.5), running-app badge (3.4), streamer mode (9.1), log sanitize (9.6).

**Never**: placebo/Vulkan rendering, SDL controller mapping UI, Steam integration, CLI, desktop windowing.
