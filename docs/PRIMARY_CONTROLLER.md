# Primary controller selection

Android's input-device enumeration order does not necessarily match the intended
player order. A TV remote advertised as a joystick can otherwise claim slot 0
before a connected DualSense. Input from that DualSense then enters the secondary
player flow and asks for another PSN account. **D-pad sends controller input**
controls remote input forwarding, not player assignment.

## Settings

Under **Settings → Controller**:

- **Primary controller (player 1)** defaults to **Auto (DualSense first)**.
  Select a connected gamepad to override automatic selection. The selection is
  stored by Android's persistent device descriptor, rather than its runtime ID.
- **Navigation-only device** defaults to **None**. Select the TV remote here if
  Android lists it as a gamepad. Its menu, HUD and Back navigation remains
  available, but it cannot take a player slot or request a co-op account.

Automatic selection recognizes Sony DualSense (`054c:0ce6`) and DualSense Edge
(`054c:0df2`), including the generic Bluetooth name "Wireless Controller".
The DualSense name is a fallback when vendor metadata is absent. A disconnected
manual selection remains saved; an available DualSense is preferred meanwhile.
Without either candidate, existing stable-slot behavior is retained.

The selected pad can take slot 0 when connected after streaming has started.
Only that pad and the displaced primary change slots; other players retain
their positions. Existing callbacks clear input state and account assignments
for changed slots. Changing the primary during co-op may require an affected
secondary player to choose their account again.

Android permits identical descriptors for indistinguishable devices. The current
primary is retained among matching candidates; otherwise the lowest runtime ID
breaks ties. This cannot guarantee persistent physical-device distinction when
Android itself provides identical descriptors.

## Diagnostics

Each session input manager start, device change and controller-preference change
logs the full input-device inventory and resulting slot map:

```sh
adb logcat -v threadtime PadManager:I '*:S'
```

Device lines include ID, name, descriptor, vendor/product IDs, hexadecimal source
mask, virtual/gamepad/DualSense/navigation classification, available axes and
player/protocol slot. Untracked devices are included. Assignment and removal
lines identify slot changes. No PSN credentials or tokens are logged.

Expected: DualSense has `player=1 protocolSlot=0`. A configured navigation-only
remote has `navigationOnly=true player=null protocolSlot=null`, even if its raw
`gamepad` classification is true. A conventional non-joystick remote needs no
override. Navigation-only classification is applied consistently to slot
allocation, key/motion forwarding, HUD navigation and the co-op account picker.

## Regression and hardware checks

JVM tests cover late connection, reconnecting with a new runtime ID, manual
selection, two DualSense pads, capacity, stable secondary slots, virtual and
non-joystick collections, navigation-only remotes, secondary join requests and
independent HUD hat input from multiple devices.

```powershell
.\build.ps1 -Local 'testDebugUnitTest assembleRelease'
```

Still verify on the affected TV and PS5: player 1 uses the stream owner's account
without a co-op prompt; remote HUD/Back navigation; Bluetooth reconnection; saved
selection after restarting; another real gamepad joins through the existing
distinct-account co-op flow. A JVM regression and emulator startup do not prove
the concrete TCL device classification or console acceptance.

Authentication, the native protocol and the seven-slot multiplayer model are
unchanged.
