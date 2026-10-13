# 4K (2160p) support plan

Status: **CLOSED — not feasible.** Reverse-engineering the official app proved there is no 4K
negotiation to reverse: the official PS Remote Play app itself never streams above 1080p.

## RE findings (official TV app `com.playstation.remoteplay.tv` 9.5.0, jadx decompile)

- Protocol core = `com.playstation.newcore.RpNativeCoreLibrary` (native, string-encrypted).
- Stream resolution enum `RPCoreResolutionType` = {360, 540, 720, **1080**} — no 2160 value exists.
- Quality presets (decompiled): low=360p@2M, standard=540p@6M, high=720p@10M,
  **very_high=1080p@15M** — Sony's own max is 1080p @ 15 Mbps.
- `RP_CORE_CLIENT_TELEMETRY_MAX_RESOLUTION_TYPE_1440P/_2160P` exist but are *telemetry-only*
  (client display capability reporting), never used for stream requests.
- Conclusion: the console's Remote Play encoder caps at 1080p on all official clients;
  4K Remote Play does not exist in the protocol. chiaki-ng's 50 Mbps @ 1080p already
  exceeds the official quality ceiling. "4K" on a 4K TV = 1080p stream + TV upscaling.

---

*(original plan below, kept for reference)*

## Background / findings

- chiaki-ng upstream (latest main, v1.10.0) has **no 4K support**: the C library's max preset is
  `CHIAKI_VIDEO_RESOLUTION_PRESET_1080p` (`external/chiaki-ng/lib/include/chiaki/session.h`),
  and neither the Qt nor the upstream Android app exposes 2160p.
- The official PS Remote Play app does 4K on PS5, but the negotiation it uses (if any beyond the
  plain video profile) has never been reverse-engineered into chiaki.
- **However**: our JNI passes `ConnectVideoProfile.width/height` verbatim into
  `connect_info.video_profile` (`native/jni/chiaki-jni.c`, the `videoProfile` field read), and the
  C library sends those values in the stream request. So a 3840×2160 request can be attempted
  **purely from Kotlin, no native changes**.
- Whether the PS5 honors a 2160p request from a third-party client is **unverified** — it may
  stream 4K, silently clamp to 1080p, or reject the session.

## Plan

1. `common/Preferences.kt`
   - Add `RES_2160P("2160p", "2160p (PS5)")` to the `Resolution` enum.
   - The enum currently maps to `VideoResolutionPreset` (caps at 1080p) — for 2160p build the
     profile directly: `ConnectVideoProfile(3840, 2160, fps.preset.value, defaultBitrate, codec)`
     instead of `ConnectVideoProfile.preset(...)` in `videoProfileDefaultBitrate` / `videoProfile`.
   - Default bitrate for 2160p: 50000 kbps (slider max; official 4K uses ~38 Mbps).
2. `ui/screens/SettingsScreen.kt` — resolution row cycles the enum, so 2160p appears automatically;
   label it "2160p (PS5)".
3. No native changes needed:
   - Decoder is initialized from the requested width/height (`chiaki-jni.c` `video_decoder_init`),
     Shield decodes 4K HEVC60 fine; `video_profile_auto_downgrade` is already enabled.
   - HUD shows the real stream resolution from the console's stream header → easy verification.

## Verification / exit criteria

- Connect to a PS5 (console video output set to 4K, H.265/HDR codec) with the 2160p setting:
  - HUD shows 3840×2160 → works, ship it (PS5-only note in UI).
  - Console clamps to 1080p or session fails → true 4K needs protocol RE; remove/hide the option
    and track upstream chiaki-ng for a real implementation.

## Caveats

- PS5 only — PS4/PS4 Pro Remote Play caps at 1080p.
- Console video output resolution must be 4K for the official app's 4K path; unclear if that
  matters for a forced request.
- 4K HDR requires the H.265 HDR codec.
- Bandwidth: expect ~30–50 Mbps; keep Ethernet/5 GHz.
