# Per-Slot PSN Easy Sign-in (local co-op, player 2+)

Goal: seat a second (…sixth) player on one Remote Play session by authenticating each
controller slot with a real PSN account via Easy Sign-in, like Asobi (`com.asobi.droid`)
and PSPlay do (chiaki-ng #835).

Status: **planned** — all multi-slot plumbing was reverted (never committed); it must be
re-landed as part of Phase 3. Everything below marked *verified* was confirmed by RE of
`temp/libasobidroid.so` + live wire experiments against PS5-992 (Oct 2026).

## Why a guest join cannot work (verified)

- `CTRLADD_EX` (ctrl `0x0068`) with `rid=0` (guest) or `rid=<real account>` is acked with
  ctrl `0x8068` payload `[01 01]` and the console **never seats** the user, shows no
  user-select/passcode prompt, and keeps applying every input on the connection to player 1.
- Asobi's join API is `nativePanelCoopRequestJoin(panel, String rid, padHandle, slot)` —
  a signed-in PSN account rid is mandatory. Native strings: `handle_easy_signin`,
  `request_join`, `get_kamaji_anonymous_auth_code`, `get_auth_code_via_npsso`,
  `PSAppSignIn` (in-app authorize WebView), refusal tokens `account-already-joined`,
  `pad-already-joined`, `host-passcode`, `host-player-max`, `host-refused`,
  `no-profile-on-console`, `no-free-slot`, `join-in-flight`.
- Conclusion: seating requires an **auth code** obtained from PSN for that account and
  delivered to the console over ctrl (Easy Sign-in). There is no anonymous path.

## Wire recipe (verified this session)

Feedback state (takion): payload[0] = `0xa0 | slot` (slot 0 = upstream's hardcoded `0xa0`;
Asobi builder `0x52f148`: `mov w8,#0xa0; bfxil w8,w2,#0,#4; strb w8,[x1]`).
Feedback history: slot goes in the takion header byte `buf[3]` (chiaki's TODO byte).

CTRL framing: `[u32 BE size][u16 BE type][u16 zero][payload]`; the CTRL channel stays
alive during the stream (keyboard uses it). `chiaki_ctrl_send_message` is exported
(ctrl.h) — no ctrl.c override needed just to *send*.

- `CTRLADD_EX 0x0068` (app→console): 16-byte payload
  `[u64 rid LE][u8 playerId=slot][u8 ctrlType][6x0]`, ctrlType `0x0c`=DualSense,
  `0x08`=DS4 (Asobi sender `0x454f04`; log labels playerId=w2→payload[8],
  ctrlType=w3→payload[9]).
- `0x8068` reply (console→app): 2-byte payload, observed `[01 01]` for both guest and
  real-rid joins. Semantics unknown (S1).
- `CONTROLLER_DEL 0x0009`: 1-byte `[u8 playerId]`.
- `CONTROLLERCONNECTION` (takion protobuf): send per extra user with
  `controller_id = slot`.
- 7 slots total (Asobi state arrays: slot 0 at struct `0xae8`, slots 1–3 stride `0x44`
  at `0x634`, slots 4–6 at `0xd24`).

Asobi reference offsets in `temp/libasobidroid.so` (use `temp/re_lib.py`):
CTRLADD_EX sender `0x454f04`, CONTROLLER_DEL `0x45509c`, feedback builder `0x52f148`,
"Co-op: slot {} seated" xref `0x4502f4` (join-completion handler, func starts `0x44fef8`),
npsso auth-code HTTP template near `0xa9880` (`Host: ca.account.sony.com`,
`Cookie: npsso=`), coop strings cluster `0xa9300–0xa9400` (`handle_easy_signin`,
`request_join`, `add reply`).

## Phase 0 — RE spikes

- **S1 — `0x8068` reply semantics.** OPEN. Decode `[playerId][status]` status values
  (pending / easy-signin-required / refused-why). Empirical path now unblocked: probe with
  a real *unseated* second-account rid (Phase 1) and compare replies vs `[01 01]`.
- **S2 — auth-code delivery: RESOLVED (high confidence).** There is **no ctrl message
  carrying the code**. Asobi's ctrl send wrapper (`0x45481c`) has 11 callers, all small
  fixed payloads (`0x1fe` hb, `0x13`, `0x11`, `0xd`, `0x14`, `0x30`, `0x36`, `0x8`,
  `0x9` del, `0x68` add) — nothing string-sized. The mechanism is `PSAppSignIn`: the app
  completes a PSN **authorize** HTTP call with the chosen account's credentials and the
  console picks the approval up from PSN. JNI surface: `nativeBeginPSAppSignIn(panel,
  reauthAccountId)` → `nativeAwaitPSAppSignIn(panel, reauthAccountId)` (blocks, returns
  JSON) / `nativeAwaitPSAppSignInGuest(panel)` / `nativeCancelPSAppSignIn` /
  `nativePSAppSignInPresentation` (UI state JSON). Native: `handle_easy_signin`
  (`0x450a88`, arg = slot) notifies the UI layer; authorize fn references
  `PSAppSignIn: authorize context primed (signin host={}, HTTP {})` at `0x4b5020`,
  callback-state + different-account guards near `0x4beeac`/`0x4beff8`.
- **S3 — auth-code HTTP: PARTIAL (static RE exhausted).** Two paths in Asobi:
  `get_auth_code_via_npsso` (raw HTTP template fragments at `0xa9880`: host
  `ca.account.sony.com`, `Cookie: npsso=`, ` HTTP/1.1` — path + query built dynamically at
  runtime, not a static string) and the `PSAppSignIn` authorize (signin host + HTTP, code
  region `0x4b5020`–`0x4b5490`). The authorize URL/params and the console-binding nonce
  are constructed at runtime → **not extractable statically**. Needs either dynamic capture
  (proxy/hook Asobi) or empirical trial once plumbing exists. Our OAuth client is the
  official RP one (`PsnAuth.CLIENT_ID ba495a24-…`) — same token class the official app
  uses; test Bearer-token authorize first, fall back to npsso capture.

## Phase 1 — Multi-account PSN store (`common/psn/`)

- `PsnTokenStore` → keyed by rid: list of accounts (rid, access/refresh token,
  online ID, avatar URL). Existing single-account behaviour stays the default.
- `PsnLoginDialog`: "Add PSN account" (same OAuth browser flow), account list + remove,
  per-account refresh.
- Note: rid = userinfo `user_id` (numeric); we already store it LE-base64 as `accountId`
  (`PsnAuth.fetchAccountId`).

## Phase 2 — Easy Sign-in client (`common/psn/PsnEasySignIn.kt`)

- `suspend fun authCode(account: PsnAccount, console: ConsoleInfo): String` per S3.
- Refresh the account token on 401; surface failures as user-visible errors.

## Phase 3 — Native plumbing (re-land + extend)

Re-apply (all reverted; recipe above):
- `native/overrides/feedbacksender.c` + shadowed `feedbacksender.h` (per-slot
  state/history/seq, slot 0 byte-identical to upstream).
- `native/overrides/takion.c` slot variants (`..._send_feedback_state_slot`,
  `..._history_slot`).
- `native/overrides/multislot.c/.h` (`chiaki_session_add_user(session, slot, ctrl_type,
  rid)`, `remove_user`, `set_controller_state_slot`).
- Shadowed headers converting quoted includes to `<chiaki/...>` (session.h,
  streamconnection.h, audiosender.h, opusencoder.h) + CMake swap.
- **Gotcha**: ninja has no `.o.d` deps for submodule sources — after touching shadowed
  headers, `touch external/chiaki-ng/lib/src/*.c native/jni/*.c` or risk struct-layout
  SIGSEGV (`fault addr 0x3f800000`).

New on top:
- `native/overrides/ctrl.c`: parse `0x8068` → `CHIAKI_EVENT_CTRLADD_REPLY(slot, status)`;
  implement `sessionSendEasySignInCode(slot, code)` (type per S2); keep the INFO-level
  received-message logging added during debugging.
- JNI + `Chiaki.kt`: `sessionAddUser(ptr, slot, rid)`, `sessionSendEasySignInCode`,
  event forwarding like the existing `LoginPinRequest` path.

## Phase 4 — Kotlin wiring + UI

- **Home-screen co-op section**: assign PSN accounts to controller slots before
  connecting (Asobi-style picker); persist slot↔rid; show pad presence (PadManager) +
  account per slot.
- `StreamSession`: first secondary-pad input on an assigned slot → `addUser(slot, rid)`;
  on `CTRLADD_REPLY(easy-signin-required)` → `PsnEasySignIn.authCode` →
  `sendEasySignInCode(slot, code)`; on seated → mark joined.
- Handle refusals: `host-passcode` (per-slot PIN prompt reusing the LoginPin UI),
  `account-already-joined`, `no-profile-on-console` (banner text).
- `StreamInput`: re-enable slot routing (`MULTI_CONTROLLER_ENABLED`) gated on the slot
  having an assigned account; unassigned secondary pads stay ignored.

## Phase 5 — Gameplay completion

- Verify per-slot input tags reach the console once seated (tag `0xa0|slot`).
- Per-slot rumble routing (rumble packet player index — the "unknown" `buf[0]` byte is
  the candidate).
- Leave handling: `CONTROLLER_DEL` on pad disconnect / unassign.

## Verification

1. Two PSN accounts, both with PS5 profiles: pad 2 seats as player 2 via easy sign-in.
2. Passcode path: account without saved sign-in → `host-passcode` → per-slot PIN prompt.
3. Rejoin after leave; token expiry mid-session; pad disconnect/reconnect slot stability.
4. Single-pad regression: nothing changes for one-controller users (default path).
5. Build via `.\build.ps1` (full native after override re-land; remember the touch gotcha).

## Open unknowns

- `0x8068` status byte meanings (S1) — probe with a real unseated account rid.
- Exact `PSAppSignIn` authorize URL/params + what binds the approval to the console's
  pending request (S3 remainder) — RE `0x4b5020`–`0x4b5490` or capture the HTTP.
- Whether the console requires the easy-signin *before* or *after* `CTRLADD_EX`
  (Asobi logs "requesting join" then "add reply" then "seated" — suggests add first).
- Per-slot rumble byte.
