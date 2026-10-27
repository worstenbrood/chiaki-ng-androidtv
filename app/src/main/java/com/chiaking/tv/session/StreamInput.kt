package com.chiaking.tv.session

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.LifecycleOwner
import com.chiaking.tv.common.settings.Preferences
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.lib.ControllerTouch

class StreamInput(val context: Context, val preferences: Preferences)
{

    // Stable gamepad slots; secondary input is sent only after the host accepts its account.
    val padManager = PadManager(context)

    var controllerStateChangedCallback: ((ControllerState) -> Unit)? = null

    // Join coordinator deduplicates requests and retries failed joins after a cooldown.
    var secondaryPadInputCallback: ((Int) -> Unit)? = null
    var padSlotsChangedCallback: ((Map<Int, Int>) -> Unit)? = null

    // Per-slot input for co-op secondary pads (slot -> state fed to the console).
    var slotStateChangedCallback: ((Int, ControllerState) -> Unit)? = null
    private val slotKeyStates = HashMap<Int, ControllerState>()
    private val slotMotionStates = HashMap<Int, ControllerState>()
    var hudToggleCallback: (() -> Unit)? = null
    private var previousSlots = padManager.slots

    init
    {
        padManager.slotsChangedCallback = { slots ->
            for((slot, device) in previousSlots)
                if(slots[slot] != device)
                {
                    slotKeyStates.remove(slot)
                    slotMotionStates.remove(slot)
                    if(slot == 0)
                    {
                        keyControllerState = ControllerState()
                        motionControllerState = ControllerState()
                        hudShortcut.reset()
                        controllerStateUpdated()
                    }
                }
            previousSlots = slots
            padSlotsChangedCallback?.invoke(slots)
        }
    }

    fun resetSecondaryStates()
    {
        slotKeyStates.clear()
        slotMotionStates.clear()
    }

    fun resetSecondaryState(slot: Int)
    {
        slotKeyStates.remove(slot)
        slotMotionStates.remove(slot)
    }

    fun slotControllerState(slot: Int): ControllerState
    {
        val keys = slotKeyState(slot)
        val motion = slotMotionState(slot)
        return (keys or motion).also {
            if(motion.l2State > 0U) it.l2State = motion.l2State
            if(motion.r2State > 0U) it.r2State = motion.r2State
        }
    }

    fun dispose()
    {
        sensors.dispose()
        hudShortcut.reset()
        padManager.stop()
    }

    private val hudShortcut = HudShortcut(preferences, { keyControllerState }) {
        controllerStateUpdated()
        hudToggleCallback?.invoke()
    }

    fun resetMotionControls() = sensors.reset()

    val controllerState: ControllerState get()
    {
        val controllerState = sensors.sensorControllerState or keyControllerState or motionControllerState

        sensors.applyTo(controllerState)

        // prioritize motion controller's l2 and r2 over key
        // (some controllers send only key, others both but key earlier than full press)
        if(motionControllerState.l2State > 0U)
            controllerState.l2State = motionControllerState.l2State
        if(motionControllerState.r2State > 0U)
            controllerState.r2State = motionControllerState.r2State

        return controllerState or touchControllerState
    }

    private val sensors = StreamMotionSensors(context, preferences.motionEnabled, ::controllerStateUpdated)
    private var keyControllerState = ControllerState() // from KeyEvents
    private var motionControllerState = ControllerState() // from MotionEvents
    var touchControllerState = ControllerState()
        set(value)
        {
            field = value
            controllerStateUpdated()
        }

    private val swapCrossMoon = preferences.swapCrossMoon

    fun observe(owner: LifecycleOwner) = sensors.observe(owner)

    fun unobserve(owner: LifecycleOwner) = sensors.unobserve(owner)

    private fun controllerStateUpdated()
    {
        controllerStateChangedCallback?.let { it(controllerState) }
    }

    private fun isGamepadEvent(event: KeyEvent): Boolean = PadManager.isGamepad(event.device)

    fun onHudComboKeyEvent(event: KeyEvent): Boolean {
        if(!padManager.isPrimary(event.deviceId)) return false
        if(event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        return hudShortcut.handle(event)
    }

    private fun slotKeyState(slot: Int) = slotKeyStates.getOrPut(slot) { ControllerState() }
    private fun slotMotionState(slot: Int) = slotMotionStates.getOrPut(slot) { ControllerState() }

    private fun emitSlotState(slot: Int)
    {
        slotStateChangedCallback?.invoke(slot, slotControllerState(slot))
    }

    private fun dispatchSlotKey(slot: Int, event: KeyEvent): Boolean
    {
        val st = slotKeyState(slot)
        when(event.keyCode)
        {
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                st.l2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
                emitSlotState(slot)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                st.r2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
                emitSlotState(slot)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                if(event.action == KeyEvent.ACTION_DOWN)
                {
                    st.buttons = st.buttons or ControllerState.BUTTON_TOUCHPAD
                    st.touches[0] = ControllerTouch(
                        x = (ControllerState.TOUCHPAD_WIDTH / 2U).toUShort(),
                        y = (ControllerState.TOUCHPAD_HEIGHT / 2U).toUShort(),
                        id = 0
                    )
                }
                else
                {
                    st.buttons = st.buttons and ControllerState.BUTTON_TOUCHPAD.inv()
                    st.touches[0] = ControllerTouch(id = -1)
                }
                emitSlotState(slot)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if(!isGamepadEvent(event))
                    return false
                st.buttons = st.buttons.run {
                    when(event.action)
                    {
                        KeyEvent.ACTION_DOWN -> this or ControllerState.BUTTON_PS
                        else -> this and ControllerState.BUTTON_PS.inv()
                    }
                }
                emitSlotState(slot)
                return true
            }
        }

        val buttonMask: UInt = when(event.keyCode)
        {
            KeyEvent.KEYCODE_DPAD_LEFT -> ControllerState.BUTTON_DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerState.BUTTON_DPAD_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> ControllerState.BUTTON_DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> ControllerState.BUTTON_DPAD_DOWN
            KeyEvent.KEYCODE_BUTTON_A -> if(swapCrossMoon) ControllerState.BUTTON_MOON else ControllerState.BUTTON_CROSS
            KeyEvent.KEYCODE_BUTTON_B -> if(swapCrossMoon) ControllerState.BUTTON_CROSS else ControllerState.BUTTON_MOON
            KeyEvent.KEYCODE_BUTTON_X -> if(swapCrossMoon) ControllerState.BUTTON_PYRAMID else ControllerState.BUTTON_BOX
            KeyEvent.KEYCODE_BUTTON_Y -> if(swapCrossMoon) ControllerState.BUTTON_BOX else ControllerState.BUTTON_PYRAMID
            KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
            KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
            KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
            KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
            KeyEvent.KEYCODE_BUTTON_C -> ControllerState.BUTTON_PS
            KeyEvent.KEYCODE_BUTTON_MODE -> ControllerState.BUTTON_PS
            else -> return false
        }
        st.buttons = st.buttons.run {
            when(event.action)
            {
                KeyEvent.ACTION_DOWN -> this or buttonMask
                KeyEvent.ACTION_UP -> this and buttonMask.inv()
                else -> this
            }
        }
        emitSlotState(slot)
        return true
    }

    private fun dispatchSlotMotion(slot: Int, event: MotionEvent): Boolean
    {
        val st = slotMotionState(slot)
        val previous = st.copy()
        fun Float.signedAxis() = (this * Short.MAX_VALUE).toInt().toShort()
        fun Float.unsignedAxis() = (this * UByte.MAX_VALUE.toFloat()).toUInt().toUByte()
        st.leftX = event.getAxisValue(MotionEvent.AXIS_X).signedAxis()
        st.leftY = event.getAxisValue(MotionEvent.AXIS_Y).signedAxis()
        st.rightX = event.getAxisValue(MotionEvent.AXIS_Z).signedAxis()
        st.rightY = event.getAxisValue(MotionEvent.AXIS_RZ).signedAxis()
        st.l2State = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).unsignedAxis()
        st.r2State = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).unsignedAxis()
        st.buttons = st.buttons.let {
            val dpadX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val dpadY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val dpadButtons =
                (if(dpadX > 0.5f) ControllerState.BUTTON_DPAD_RIGHT else 0U) or
                        (if(dpadX < -0.5f) ControllerState.BUTTON_DPAD_LEFT else 0U) or
                        (if(dpadY > 0.5f) ControllerState.BUTTON_DPAD_DOWN else 0U) or
                        (if(dpadY < -0.5f) ControllerState.BUTTON_DPAD_UP else 0U)
            it and (ControllerState.BUTTON_DPAD_RIGHT or
                    ControllerState.BUTTON_DPAD_LEFT or
                    ControllerState.BUTTON_DPAD_DOWN or
                    ControllerState.BUTTON_DPAD_UP).inv() or
                    dpadButtons
        }
        // Joining requires a deliberate press/movement, never a release or stick drift.
        val joinIntent = st.buttons != 0U || st.l2State > 32U || st.r2State > 32U ||
            kotlin.math.abs(st.leftX.toInt()) > 16000 || kotlin.math.abs(st.leftY.toInt()) > 16000 ||
            kotlin.math.abs(st.rightX.toInt()) > 16000 || kotlin.math.abs(st.rightY.toInt()) > 16000
        if(st != previous && joinIntent) secondaryPadInputCallback?.invoke(slot)
        emitSlotState(slot)
        return true
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean
    {
        //Log.i("StreamSession", "key event $event")
        if(event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP)
            return false

        // secondary pads drive their own co-op slot (player 2+)
        if(PadManager.isGamepad(event.device) && !padManager.isPrimary(event.deviceId))
        {
            val slot = padManager.slotOf(event.deviceId)
            if(slot == null || slot < 1)
                return true
            if(event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)
                secondaryPadInputCallback?.invoke(slot)
            return dispatchSlotKey(slot, event)
        }

        when(event.keyCode)
        {
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                keyControllerState.l2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
                return true
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                keyControllerState.r2State = if(event.action == KeyEvent.ACTION_DOWN) UByte.MAX_VALUE else 0U
                return true
            }
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                // Touchpad click arrives as BUTTON_SELECT; send it as a real touchpad click
                // (button flag + a touch in the middle of the touchpad) instead of Create/Share
                if(event.action == KeyEvent.ACTION_DOWN)
                {
                    keyControllerState.buttons = keyControllerState.buttons or ControllerState.BUTTON_TOUCHPAD
                    keyControllerState.touches[0] = ControllerTouch(
                        x = (ControllerState.TOUCHPAD_WIDTH / 2U).toUShort(),
                        y = (ControllerState.TOUCHPAD_HEIGHT / 2U).toUShort(),
                        id = 0
                    )
                }
                else
                {
                    keyControllerState.buttons = keyControllerState.buttons and ControllerState.BUTTON_TOUCHPAD.inv()
                    keyControllerState.touches[0] = ControllerTouch(id = -1)
                }
                controllerStateUpdated()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                // Android remaps the DualSense Create button to BACK; when it comes from the
                // gamepad itself, forward it to the console as the PS button instead.
                if(!isGamepadEvent(event))
                    return false
                keyControllerState.buttons = keyControllerState.buttons.run {
                    when(event.action)
                    {
                        KeyEvent.ACTION_DOWN -> this or ControllerState.BUTTON_PS
                        else -> this and ControllerState.BUTTON_PS.inv()
                    }
                }
                controllerStateUpdated()
                return true
            }
        }

        // On TV devices without a gamepad, the remote D-pad and menu keys are mapped
        // directly onto the controller when enabled in settings.
        if(preferences.dpadAsController)
        {
            val dpadMask: UInt? = when(event.keyCode)
            {
                KeyEvent.KEYCODE_DPAD_LEFT -> ControllerState.BUTTON_DPAD_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerState.BUTTON_DPAD_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> ControllerState.BUTTON_DPAD_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> ControllerState.BUTTON_DPAD_DOWN
                KeyEvent.KEYCODE_MENU -> ControllerState.BUTTON_OPTIONS
                else -> null
            }
            if(dpadMask != null)
            {
                keyControllerState.buttons = keyControllerState.buttons.run {
                    when(event.action)
                    {
                        KeyEvent.ACTION_DOWN -> this or dpadMask
                        KeyEvent.ACTION_UP -> this and dpadMask.inv()
                        else -> this
                    }
                }
                controllerStateUpdated()
                return true
            }
        }

        val buttonMask: UInt = when(event.keyCode)
        {
            // dpad handled by MotionEvents (gamepads) or key events (TV remote, see above)
            KeyEvent.KEYCODE_BUTTON_A -> if(swapCrossMoon) ControllerState.BUTTON_MOON else ControllerState.BUTTON_CROSS
            KeyEvent.KEYCODE_BUTTON_B -> if(swapCrossMoon) ControllerState.BUTTON_CROSS else ControllerState.BUTTON_MOON
            KeyEvent.KEYCODE_BUTTON_X -> if(swapCrossMoon) ControllerState.BUTTON_PYRAMID else ControllerState.BUTTON_BOX
            KeyEvent.KEYCODE_BUTTON_Y -> if(swapCrossMoon) ControllerState.BUTTON_BOX else ControllerState.BUTTON_PYRAMID
            KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
            KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
            KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
            KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
            KeyEvent.KEYCODE_BUTTON_C -> ControllerState.BUTTON_PS
            KeyEvent.KEYCODE_BUTTON_MODE -> ControllerState.BUTTON_PS
            else -> return false
        }

        keyControllerState.buttons = keyControllerState.buttons.run {
            when(event.action)
            {
                KeyEvent.ACTION_DOWN -> this or buttonMask
                KeyEvent.ACTION_UP -> this and buttonMask.inv()
                else -> this
            }
        }

        controllerStateUpdated()
        return true
    }

    fun onGenericMotionEvent(event: MotionEvent): Boolean
    {
        if(event.source and InputDevice.SOURCE_CLASS_JOYSTICK != InputDevice.SOURCE_CLASS_JOYSTICK)
            return false
        if(!padManager.isPrimary(event.deviceId))
        {
            val slot = padManager.slotOf(event.deviceId)
            if(slot != null && slot > 0 && PadManager.isGamepad(event.device))
                return dispatchSlotMotion(slot, event)
            return false
        }
        fun Float.signedAxis() = (this * Short.MAX_VALUE).toInt().toShort()
        fun Float.unsignedAxis() = (this * UByte.MAX_VALUE.toFloat()).toUInt().toUByte()
        motionControllerState.leftX = event.getAxisValue(MotionEvent.AXIS_X).signedAxis()
        motionControllerState.leftY = event.getAxisValue(MotionEvent.AXIS_Y).signedAxis()
        motionControllerState.rightX = event.getAxisValue(MotionEvent.AXIS_Z).signedAxis()
        motionControllerState.rightY = event.getAxisValue(MotionEvent.AXIS_RZ).signedAxis()
        motionControllerState.l2State = event.getAxisValue(MotionEvent.AXIS_LTRIGGER).unsignedAxis()
        motionControllerState.r2State = event.getAxisValue(MotionEvent.AXIS_RTRIGGER).unsignedAxis()
        motionControllerState.buttons = motionControllerState.buttons.let {
            val dpadX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val dpadY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val dpadButtons =
                (if(dpadX > 0.5f) ControllerState.BUTTON_DPAD_RIGHT else 0U) or
                        (if(dpadX < -0.5f) ControllerState.BUTTON_DPAD_LEFT else 0U) or
                        (if(dpadY > 0.5f) ControllerState.BUTTON_DPAD_DOWN else 0U) or
                        (if(dpadY < -0.5f) ControllerState.BUTTON_DPAD_UP else 0U)
            it and (ControllerState.BUTTON_DPAD_RIGHT or
                    ControllerState.BUTTON_DPAD_LEFT or
                    ControllerState.BUTTON_DPAD_DOWN or
                    ControllerState.BUTTON_DPAD_UP).inv() or
                    dpadButtons
        }
        //Log.i("StreamSession", "motionEvent => $motionControllerState")
        controllerStateUpdated()
        return true
    }
}
