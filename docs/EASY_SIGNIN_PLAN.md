# Local co-op protocol and implementation

The Android TV implementation uses PS5 `CTRLADD_EX` joins with a separate saved PSN
account for each secondary controller. The account must already have a usable profile
on the console. Co-op is experimental until tested against a real PS5 with several
controllers; compilation and simulated host replies do not prove console acceptance.

## Verified packet layout

Checked directly against the local `temp/libasobidroid.so` reference:

| Packet | Player identification | Asobi reference |
| --- | --- | --- |
| CTRLADD_EX `0x0068` | 16 bytes: `[u64 RID little endian][u8 slot][u8 controller type][6 zero bytes]` | `0x454f04` |
| CONTROLLER_DEL `0x0009` | `[u8 slot]` | `0x45509c` |
| Feedback state | Payload byte 0 is `0xa0 | slot`; UDP header byte 3 remains zero | `0x52f148`, `0x474960` |
| Button/trigger history | Each event starts with `0x80 | slot`; UDP header byte 3 remains zero | `0x52f4bc`, `0x52e314`, `0x474960` |
| Touch history | Contact/release event prefix is `0xd0 | slot` / `0xc0 | slot` | `0x52f2f4` |
| Sequence numbers | One state counter and one history counter per session, shared by all slots | `0x52edf8`, `0x52ee70` |

**Correction to the previous plan:** history does not put its slot in UDP header byte 3.
Also, a CTRLADD_EX success in the local send queue is not host acceptance.

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

`CoopPlayers.kt` serializes requests, assigns distinct extra accounts deterministically,
shows localized host failures and restores wanted players after a new native session.
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
