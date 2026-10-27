# Local co-op protocol and implementation

The Android TV implementation uses PS5 `CTRLADD_EX` joins with a separate saved PSN
account for each secondary controller. The account must already have a usable profile
on the console. Co-op is experimental until tested against a real PS5 with several
controllers; compilation and simulated host replies do not prove console acceptance.

## Verified packet layout

Checked directly against the local `temp/libasobidroid.so` reference:

| Packet | Player identification | Asobi reference |
| --- | --- | --- |
| CTRLADD_EX `0x0068` | 16 bytes: `[u64 RID little endian][u8 slot][u8 control type = 3][6 zero bytes]` | `0x34e598`, vtable `0xca2668`, builder `0x454f04` |
| CONTROLLER_DEL `0x0009` | `[u8 slot]` | `0x45509c` |
| Feedback state | Payload byte 0 is `0xa0 | slot`; UDP header byte 3 remains zero | `0x52f148`, `0x474960` |
| Button/trigger history | Each event starts with `0x80 | slot`; UDP header byte 3 remains zero | `0x52f4bc`, `0x52e314`, `0x474960` |
| Touch history | Contact/release event prefix is `0xd0 | slot` / `0xc0 | slot` | `0x52f2f4` |
| Sequence numbers | One state counter and one history counter per session, shared by all slots | `0x52edf8`, `0x52ee70` |

**Correction to the previous plan:** history does not put its slot in UDP header byte 3.
Also, a CTRLADD_EX success in the local send queue is not host acceptance.
The control type is **3**, independently of DualSense mode. Takion's
CONTROLLERCONNECTION values (8 for DS4, 12 for DualSense) belong to a separate
enum and must not be used in CTRLADD_EX. The Shield's previous request with type
8 received `0x8068 [1, 1]` (host refusal). After changing the type to 3,
the user confirmed that player 2 joins successfully on the Shield/PS5 setup.

## Join and leave state machine

- Only one join is outstanding, so legacy replies can be attributed safely.
- `0x8068` is `[slot, result]`; legacy `0x8008` carries only a result.
- Result 0 seats the player. Asobi maps 2/5 to player-limit, 3 to passcode and other
  nonzero results to host-refused (`0x44fef8`, refusal string table at `0xf58c0`).
- Only after a successful host reply do we send per-slot CONTROLLERCONNECTION and
  enable that player's feedback. Input is ignored while the request is pending.
- `0x61` signals an Easy Sign-in/profile requirement for the indicated slot; `0x64`
  signals a passcode requirement for the pending join. These notify the user and
  cancel the join, matching Asobi's co-op handler (`0x450a88`, dispatcher `0x398970`).
  They do **not** silently authenticate the account or bypass the console profile PIN.
  The user signs in/adds the profile on the console, then retries.
- Cancellation, unplug and the 30-second timeout keep an unresolved slot reserved.
  A late success is followed by CONTROLLER_DEL rather than activating input.
- A leave reserves the slot until `0x8009 [slot, result]` acknowledges removal.
  Unresolved/failed removals are not reused; reconnecting starts a fresh generation.

## Ownership and input

PSN settings can store seven extra accounts in addition to the primary account.
Each saved account is listed by account ID and can be removed individually.
EncryptedSharedPreferences keeps `extra_rids` as a set of account IDs, with
`extra_<rid>_access`, `_refresh`, and `_expiry` keys; display positions are never
storage keys. Removing an account deletes its credentials and index entry in one
editor transaction. Signing out of the primary account preserves the extras.
Token refreshes cannot restore a deleted account or overwrite a newer login.

`CoopPlayers.kt` serializes requests and shows a card picker for each joining controller.
Only that controller (or the TV remote) navigates its picker; existing players keep
playing. Used accounts remain visible but cannot be selected. The chosen RID is
revalidated before CTRLADD_EX, and the opening/selection button state is cleared so
it never reaches the game. Cancelling or unplugging closes the pending choice without
sending a join request. Cards show standard account icons and saved account IDs;
there is no PSN profile lookup or avatar download. Shared OAuth JSON transport lives
in `PsnHttpClient.kt` and logs status codes without URLs, tokens or response bodies.
`CoopPlayers.kt` also shows localized host failures and restores wanted players
after a new native session.
There is no anonymous RID=0 fallback. `PadSlots.kt` preserves assignments when Android
reorders devices or another controller disconnects; newly attached devices fill gaps.
Secondary pads do not navigate player 1's HUD.

`feedbacksender.c` delegates to one session-owned sender in `multislot.c`, retaining the
upstream struct ABI. A mutex-protected registry provides lookup only: it owns no global
player assignments. The sender holds all slot state, a common history ring/queue and
both sequence counters. Upstream's feedback-sender shutdown joins this one worker
before Takion closes. Repeated history datagrams keep the same sequence number so
loss recovery does not turn an old press into a new event. Touch down/up and quick
press/release snapshots are retained independently of state coalescing.

Do not copy upstream feedbacksender.c back over the local delegate when syncing.
CMake replaces feedbacksender.c, ctrl.c and takion.c and adds multislot.c. There are no
new shadowed session/streamconnection structs to keep in ABI sync.

## Automated verification

From the repository root on Windows:

```powershell
.\build.ps1 "assembleDebug testDebugUnitTest -Pabis=arm64-v8a"
docker exec -w /src chiaki-tv-build python3 native/tests/run_multislot_tests.py
```

The native tests use real upstream controller/history/thread code and generated Takion
protobufs, with a fake host and captured feedback. They cover acceptance, refusal,
legacy/stale replies, duplicate accounts, cancellation/late success, profile refusal,
timeout, rapid press/release, touch contacts, retransmissions, shared counters, teardown
and recreation. A second executable exercises the production Takion builders over a
local datagram socket for v9/v12 and slots 0..6; crypto alone is stubbed. UBSan is enabled.
AddressSanitizer is unavailable under this host's x86 QEMU runtime.

Kotlin tests cover stable slots, unplugging the primary/secondary pad, filling gaps,
duplicate device IDs and capacity limits.

## Console smoke test still required

1. Connect normally with player 1; verify buttons, sticks and touchpad.
2. Add a distinct extra PSN account in Settings, with its profile on the PS5.
3. Press a button on controller 2. Wait for the joined message; verify independent input.
4. Repeat with additional distinct accounts up to the console's supported player limit.
5. Open player 1's HUD: player 2 must retain D-pad/cross input to the game.
6. Unplug/replug a pad, cancel a pending join, and reconnect the stream. Other players
   must keep their slots; no input may survive a leave and no native sender may survive teardown.
7. Verify profile/passcode/max-player refusals produce useful messages without seating
   the controller. Check per-player rumble separately; it is outside the current input changes.
