# PSN / Remote Play over Internet — feasibility & plan

Status: **planned** — covers plan items 4.1–4.4.

**Verdict: feasible — and mostly already in the codebase.** The C library's entire remote
stack (`remote/holepunch.c`, `rudp.c`) is **already compiled into our APK** (curl is built with
`ENABLE_WEBSOCKETS ON`, json-c + miniupnpc via FetchContent — see `native/CMakeLists.txt` +
`external/chiaki-ng/third-party/CMakeLists.txt`). `ChiakiConnectInfo` already has
`holepunch_session` / `psn_account_id` / `auto_regist` fields, and our `RegistInfo` already
carries `psnAccountId`. What's missing: OAuth in Kotlin, JNI bindings for the holepunch API,
and TV UI.

## Can login be automated?

- **Fully automated: no.** PSN authorize requires interactive login (password + CAPTCHA + 2FA);
  no device-code flow exists for this client.
- **Semi-automated: yes** — the redirect is to Sony's public page
  (`https://remoteplay.dl.playstation.net/remoteplay/redirect?code=...`), so: TV shows a
  **QR code** → user scans with phone → logs in → phone lands on the redirect URL → user copies
  it → pastes on TV (Gboard cross-device clipboard makes this one long-paste, no typing).
  Token exchange / refresh / device-listing / connect are then **100% automated**.
- **Endpoint viability: verified 2026-10** — the authorize URL still renders Sony's login page
  with chiaki's client_id (`ba495a24-818c-472b-b12d-ff231c1b5745`, secret in
  `external/chiaki-ng/gui/include/psnaccountid.h:18-19`).

## Key upstream references

- OAuth endpoints + flow: `gui/include/psnaccountid.h` (LOGIN_URL/TOKEN_URL/CLIENT_ID/SECRET,
  scopes, redirect), `gui/src/psnaccountid.cpp` (code→token→userinfo), `gui/src/psntoken.cpp`
  (refresh grant), `gui/src/qmlbackend.cpp:1675-1710` (login URL w/ duid, paste-redirect parsing),
  `qmlbackend.cpp:2506+` (open browser), `qmlbackend.cpp:862-1233` (PsnConnectState machine).
- Holepunch API: `lib/include/chiaki/remote/holepunch.h` (usage order documented at top),
  orchestration in `gui/src/streamsession.cpp:537-550` (connect_info wiring) and
  `2671-2750` (InitiatePsnConnection / ConnectPsnConnection / Cancel).
- Account-ID derivation: `user_id` from `GET TOKEN_URL/<access_token>` → 8-byte little-endian →
  base64 (`psnaccountid.cpp:41-48`).

## Phase 0 — Spike (~1 session)

1. Manual end-to-end on PC: open LOGIN_URL, grab `?code=`,
   `curl -u client:secret -d "grant_type=authorization_code&code=...&scope=...&redirect_uri=..."`
   against TOKEN_URL → confirm tokens; GET `TOKEN_URL/<access_token>` → `user_id` →
   base64(8-byte LE) = Account-ID.
2. Refresh grant works; note `expires_in` (~7.5 days).
3. First real holepunch test from the Shield (NAT behavior is the main runtime risk).

## Phase 1 — PSN auth in Kotlin (`common/psn/`)

- JNI: one new function `generateClientDeviceUid()`
  (wraps `chiaki_holepunch_generate_client_device_uid`).
- `PsnAuth.kt` (HttpURLConnection, no new HTTP dep): `loginUrl(duid)` (LOGIN_URL + `duid=`),
  `exchangeCode(code)`, `refresh(refreshToken)`, `fetchAccountId(token)` — mirror
  `psnaccountid.cpp` / `psntoken.cpp` exactly (scopes, redirect_uri, Basic auth).
- `PsnTokenStore.kt`: **EncryptedSharedPreferences** (new dep `androidx.security:security-crypto`) —
  access/refresh/expiry/account-id/duid; never in the export file. Auto-refresh 1 min before
  expiry (call from `MainActivity.onResume` + before any PSN op).
- Settings UI: "PlayStation Network" section — login dialog with QR (new dep `com.google.zxing:core`)
  + URL text + paste field (accepts the full redirect URL **or** a bare code), account status + logout.

## Phase 1b (optional) — WebView login path with email prefill

Secondary login option so no phone is needed; QR + paste stays the primary path.

- Load the PSN authorize URL in an Android `WebView`; a `WebViewClient.shouldOverrideUrlLoading`
  catches the `...remoteplay/redirect?code=...` URL → code extracted automatically (no paste).
- **Email prefill**: the TV's Google account email is NOT readable (Android 8+ hides Google
  accounts from third-party apps), so instead persist the PSN email after the first successful
  login and prefill it via `evaluateJavascript` — set the input's `value` AND dispatch `input`/
  `change` events (Sony's login page is React; plain value assignment won't stick without them).
- Caveats: PSN login often shows a CAPTCHA (near-impossible with D-pad) and password typing on
  the soft keyboard is slow — hence optional/fallback, shown after QR path fails or on request.
- Cookie persistence in the WebView `CookieManager` may let repeat logins skip straight to the
  password step (or even auto-complete when the session cookie is still valid).

## Phase 2 — JNI holepunch bindings (`native/jni/chiaki-jni.c`)

- `holepunchListDevices(token, ps5)` → array (name, duid, remoteplay_enabled) —
  wraps `chiaki_holepunch_list_devices`.
- `holepunchSessionCreate(token, duid, ps5, portGuessing)` — mirrors
  `StreamSession::ConnectPsnConnection` (`streamsession.cpp:2692-2745`): upnp_discover →
  session_create → create_offer → session_start → punch_hole(CTRL); returns session ptr.
  All blocking → Kotlin `Dispatchers.IO`.
- `holepunchCancel(ptr, stopThread)`, `holepunchFini(ptr)` (fini **after streaming ends**,
  not after punch).
- Extend `ConnectInfo` (Kotlin + JNI): `psnAccountId: ByteArray?`, `holepunchSessionPtr: Long`,
  `autoRegist: Boolean` → fill `chiaki_connect_info` (host field = dummy when holepunch set, like Qt).
- Forward `data_holepunch.finished` event → Kotlin event (progress UI).

## Phase 3 — Remote connect flow (Kotlin/UI)

- `StreamSession`: accept remote ConnectInfo; on quit/stop call `holepunchFini`;
  surface `DataHolepunchProgress`.
- `PsnViewModel` state machine mirroring `PsnConnectState`
  (LinkingConsole → RegisteringConsole → DataConnectionStart/Finished → failed states with
  symmetric-NAT hint).
- HomeScreen: PSN console cards (device list cached, pull-to-refresh); connect → wake via session
  start (console must be in rest mode; no WoL over internet).
- StreamManager/keep-alive work unchanged (same `StreamSession`).

## Phase 4 — Registration + auto-connect

- 4.3: `auto_regist=true` for consoles on the same PSN account
  (JNI regist already accepts `psnAccountId`).
- 4.4: extend auto-connect — if LAN discovery finds nothing and PSN token is valid,
  connect via PSN (retry ≤6).

## Dependencies to add

- `androidx.security:security-crypto` (EncryptedSharedPreferences)
- `com.google.zxing:core` (QR generation)

## Effort

P0 S, P1 M, P2 M, P3 L, P4 M (total XL).

## Risks

- Sony revoking the public client (spike validates).
- Symmetric NAT (mitigated by port-guessing knobs:
  `chiaki_holepunch_session_force_port_guessing` / `set_port_guessing_ports` / `set_port_guessing_socks`).
- WebSocket-over-curl runtime on Android (untested — first holepunch test is the gate).
