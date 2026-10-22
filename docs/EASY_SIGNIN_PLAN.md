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

## Phase 0 — RE spikes (do first, gate the rest)

- **S1 — `0x8068` reply semantics.** Decode `[playerId][status]` status values
  (pending / easy-signin-required / refused-why). Method: RE the add-reply handler
  (ctrl dispatch is a jump table; find the ctrl recv loop via the heartbeat-reply send,
  dump case targets) or empirically once S3 works (join with a real 2nd-account rid and
  compare replies).
- **S2 — auth-code delivery message.** Identify the ctrl type + payload Asobi uses to
  hand the auth code to the console (and whether the console pushes a per-slot
  easy-signin request first). Method: jump-table RE around `handle_easy_signin`;
  cross-check against known types (`LOGIN_PIN_REQ 0x4`, `LOGIN_PIN_REP 0x8004`,
  `LOGIN 0x5`).
- **S3 — auth-code HTTP.** Pin the `ca.account.sony.com/api/authz/v3/authorize` consent
  call: does a Bearer OAuth access token work (kamaji path) or is the npsso cookie
  required? Extract clientId/scope/nonce params from Asobi strings near `0xa9880`.
  Our OAuth client is the official RP one (`PsnAuth.CLIENT_ID ba495a24-…`,
  `remoteplay.dl.playstation.net` redirect) — same token class the official app uses.

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

- `0x8068` status byte meanings (S1).
- Exact ctrl type/payload for auth-code delivery (S2).
- Whether Bearer-token authorize works or npsso is mandatory (S3) — if npsso-only,
  Phase 1 needs an in-app WebView capture of npsso (Asobi's `PSAppSignIn` path).
- Whether the console requires the easy-signin *before* or *after* `CTRLADD_EX`
  (Asobi logs "requesting join" then "add reply" then "seated" — suggests add first).
- Per-slot rumble byte.
