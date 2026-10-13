# 4K (2160p) support plan

Status: **planned / experimental** — not implemented yet.

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
