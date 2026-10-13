# Chiaki TV

Android TV port of [chiaki-ng](https://github.com/chiaki-ng/chiaki-ng) — free and open-source
PS4/PS5 Remote Play. Stream your PlayStation console to an Android TV device
(Google TV, Nvidia Shield, Xiaomi TV Box, ...) over your local network.

The streaming core is chiaki-ng's battle-tested C library (Takion/RPCrypt protocol stack,
MediaCodec hardware video decoding, Oboe low-latency audio). The UI is a native
**Jetpack Compose for TV** app with a modern dark PS-blue theme and full D-pad/gamepad navigation.

## Features

- **Console discovery** — automatically finds PS4/PS5 consoles on the LAN
- **Console registration** — PIN-based pairing flow (8-digit PIN from the console);
  selecting an unregistered discovered console opens registration with its IP and
  console type (PS5/PS4) prefilled
- **Remote Play** — H.264 / H.265 / H.265 HDR, up to 1080p60, auto or manual bitrate (2000–50000 kbps)
- **Wake-on-connect** — selecting a rest-mode console wakes it and auto-connects once it comes online
- **Auto-connect on startup** — pick a console in Settings; the app wakes and connects to it automatically on launch
- **Google TV home channel** — registered consoles appear as programs on a "Chiaki TV" preview
  channel on the home row (with per-console PS5/PS4 artwork); selecting one wakes and connects
- **Gamepad support** — full controller mapping incl. sticks, triggers, motion controls and rumble
- **TV remote support** — D-pad and menu button act as controller input while streaming (toggleable)
- **Manual hosts** — add consoles by IP when discovery is not enough
- **Audio controls** — stream volume (applies live), output-device selection
  (HDMI, Bluetooth, USB, ...) with automatic fallback to the system default, and
  audio buffer size (Auto or 10–100 ms in 10 ms steps, like the Qt app)
- **In-stream HUD** — press **L3 + R3** while streaming to toggle an interactive bar with live
  FPS, measured bitrate, packet loss %, ping, lost frames and the current video/audio config.
  Navigate the chips with the **D-pad** (gamepad D-pad *and* left stick are both understood):
  the **Audio** chip cycles the output device with **cross** and adjusts volume with **D-pad
  up/down**; the bar ends with a **✕** chip that opens the Sleep/Disconnect dialog. While the
  HUD is open only D-pad + cross are captured — every other button/stick still goes to the game.
  Back closes the HUD
- **Clean disconnect** — Back (TV remote) or the HUD ✕ shows a "Disconnecting" spinner
  and returns to the main screen once the session has fully shut down
- **Settings** — resolution, FPS, codec, bitrate, audio, controller options, verbose logging, session log viewer
- **Backup** — export/import registered + manual hosts as JSON (compatible with chiaki-ng's format)

## Requirements

- Android TV 7.0+ (API 24), arm64-v8a (e.g. Shield TV, most modern TV boxes)
- PS4 (system software 5.0+) or PS5 with Remote Play enabled
- A PSN **Account ID** (base64, 8 bytes) for registration — obtain it with the
  `psn-account-id.go` or `psn-account-id.py` script from
  [`external/chiaki-ng/scripts/`](external/chiaki-ng/scripts) (see
  [chiaki-ng's guide](https://github.com/chiaki-ng/chiaki-ng#obtaining-your-psn-accountid))

## Getting started

1. Install the APK on your TV (`adb connect <tv-ip>` then `adb install app-debug.apk`).
2. Enable Remote Play on the console (PS5: *Settings → System → Remote Play*; PS4: *Settings → Remote Play Connection Settings*).
3. Open Chiaki TV — discovered consoles appear as cards. Select one and enter the 8-digit
   pairing PIN shown on screen (or use *Register Console*).
4. Select the registered console to start streaming. A rest-mode console is woken automatically
   and the stream starts as soon as it comes online (Cancel with Back).
5. Optional: *Settings → General → Auto-connect on Startup* + pick a console — launching the app
   (or selecting its program on the "Chiaki TV" Google TV channel) then wakes and connects by itself.
6. While streaming, press **L3 + R3** on the controller to open the in-stream HUD
   (stats, live audio device/volume, and the **✕** disconnect chip).

> **Note (Shield TV):** the system intercepts the controller's **PS button** as HOME, so it
> cannot be forwarded to the console. The **Create/Share** button (which Android remaps to
> Back) is forwarded to the console as the PS button instead. The **touchpad click** is
> sent as a real touchpad click (not Create/Share).

## Building

All building happens in Docker — the Android NDK and aapt2 are x86_64-only, so on ARM hosts
the image runs under qemu emulation (slow but correct). Full details in [AGENTS.md](AGENTS.md).

```powershell
# one-time: fetch the chiaki-ng submodule (C core + JNI)
git submodule update --init external/chiaki-ng
git -C external/chiaki-ng submodule update --init third-party/nanopb third-party/jerasure third-party/gf-complete third-party/curl

# one-time: build the Linux build image
docker build --platform linux/amd64 -f docker/Dockerfile.build -t chiaki-tv-build-amd64 docker/

# build the APK (~9 min incremental, ~25 min cold — build.ps1 persists the Gradle cache in .docker-cache/)
.\build.ps1

# build the release APK (signed with the repo keystore, optimized native libs)
.\build.ps1 assembleRelease
```

Output: `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release.apk`.
The release APK is signed with the repo's keystore, so it installs over a debug build without data loss.
Other ABIs: append `-Pabis=arm64-v8a,armeabi-v7a`.

## Project layout

```
app/                    Android TV app (Kotlin + Compose, namespace com.chiaking.tv)
external/chiaki-ng      git submodule — upstream chiaki-ng (C core, built via CMake)
native/                 CMake entry point + our copy of the JNI layer
                        (adds audio volume, output-device and stream-stats on top of upstream)
docker/                 Docker build image definition
docs/UPSTREAM_SYNC.md   How to integrate new upstream chiaki-ng commits
AGENTS.md               Build/architecture notes for AI agents and contributors
```

The C core is compiled directly from the submodule; the JNI layer is our own copy in
`native/jni/` that must be re-diffed on upstream bumps (see `docs/UPSTREAM_SYNC.md`).
The Kotlin layer is a manual port of upstream's Android app adapted for TV.

## Troubleshooting

- **No consoles discovered** — TV and console must be on the same L2 network (mDNS/UDP broadcast);
  check router AP-isolation settings, or add the console as a manual host.
- **Registration fails** — double-check the PSN Account ID (base64, decodes to 8 bytes) and that
  the PIN was entered within its validity window; enable *Verbose Logs* and check *Session Logs*.
- **Stutter / high latency** — prefer 5 GHz Wi-Fi or Ethernet; try H.265 and lower the bitrate.
- **D-pad does nothing in game** — enable *Settings → Controller → D-pad sends controller input*.

## License

Licensed under the same terms as chiaki-ng: **AGPL-3.0-only with OpenSSL link exception**
(`LicenseRef-AGPL-3.0-only-OpenSSL`). This project is not affiliated with Sony or PlayStation.
