package com.chiaking.tv.session

import android.content.Context
import android.hardware.*
import android.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.chiaking.tv.common.settings.Preferences
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.lib.ControllerTouch

class StreamInput(val context: Context, val preferences: Preferences)
{
	companion object
	{
		private const val GYRO_SAMPLING_PERIOD_US = 4000
	}

	// 7-slot gamepad tracking; only the primary pad drives the session until the protocol
	// exposes multi-controller support (then the slot becomes the userId).
	val padManager = PadManager(context)

	var controllerStateChangedCallback: ((ControllerState) -> Unit)? = null

	// Fires once per secondary-pad slot on its first key event (co-op join trigger).
	var secondaryPadInputCallback: ((Int) -> Unit)? = null
	private val secondaryPadSeen = mutableSetOf<Int>()
	var hudToggleCallback: (() -> Unit)? = null

	fun dispose()
	{
		padManager.stop()
	}

	private var pressedComboKeys = mutableSetOf<Int>()
	private var hudComboFired = false

	@Volatile private var orientRef: FloatArray? = null
	private var isRotated90 = false

	// read at sensor rate (250 Hz) — never look the service up per call
	private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

	// cached: read on every key event, the shortcut only changes in settings
	private var comboShortcut: Preferences.MenuShortcut? = null
	private var comboKeysCached: Set<Int> = emptySet()
	private val comboKeys: Set<Int> get()
	{
		val shortcut = preferences.menuShortcut
		if(comboShortcut != shortcut)
		{
			comboShortcut = shortcut
			comboKeysCached = when(shortcut)
			{
				Preferences.MenuShortcut.L3_R3 -> setOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR)
				Preferences.MenuShortcut.SELECT_START -> setOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)
				Preferences.MenuShortcut.SHOULDERS -> setOf(KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
					KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2)
			}
		}
		return comboKeysCached
	}

	fun resetMotionControls()
	{
		var x = sensorControllerState.orientX
		var y = sensorControllerState.orientY
		var z = sensorControllerState.orientZ
		val w = sensorControllerState.orientW
		if(isRotated90)
		{
			x *= -1.0f
			z *= -1.0f
		}
		orientRef = if(w != 0f) floatArrayOf(x, y, z, w) else floatArrayOf(0f, 0f, 0f, 1f)
	}

	val controllerState: ControllerState get()
	{
		val controllerState = sensorControllerState or keyControllerState or motionControllerState

		@Suppress("DEPRECATION")
		isRotated90 = windowManager.defaultDisplay.rotation == Surface.ROTATION_90
		if(isRotated90)
		{
			controllerState.accelX *= -1.0f
			controllerState.accelZ *= -1.0f
			controllerState.gyroX *= -1.0f
			controllerState.gyroZ *= -1.0f
			controllerState.orientX *= -1.0f
			controllerState.orientZ *= -1.0f
		}

		// re-centered orientation: q_rel = conj(ref) * cur (identity when no reset requested)
		orientRef?.let { ref ->
			val cx = controllerState.orientX
			val cy = controllerState.orientY
			val cz = controllerState.orientZ
			val cw = controllerState.orientW
			val ax = -ref[0]
			val ay = -ref[1]
			val az = -ref[2]
			val aw = ref[3]
			controllerState.orientX = aw * cx + ax * cw + ay * cz - az * cy
			controllerState.orientY = aw * cy - ax * cz + ay * cw + az * cx
			controllerState.orientZ = aw * cz + ax * cy - ay * cx + az * cw
			controllerState.orientW = aw * cw - ax * cx - ay * cy - az * cz
		}

		// prioritize motion controller's l2 and r2 over key
		// (some controllers send only key, others both but key earlier than full press)
		if(motionControllerState.l2State > 0U)
			controllerState.l2State = motionControllerState.l2State
		if(motionControllerState.r2State > 0U)
			controllerState.r2State = motionControllerState.r2State

		return controllerState or touchControllerState
	}

	private val sensorControllerState = ControllerState() // from Motion Sensors
	private val keyControllerState = ControllerState() // from KeyEvents
	private val motionControllerState = ControllerState() // from MotionEvents
	var touchControllerState = ControllerState()
		set(value)
		{
			field = value
			controllerStateUpdated()
		}

	private val swapCrossMoon = preferences.swapCrossMoon

	private val sensorEventListener = object: SensorEventListener {
		override fun onSensorChanged(event: SensorEvent)
		{
			when(event.sensor.type)
			{
				Sensor.TYPE_ACCELEROMETER -> {
					sensorControllerState.accelX = event.values[1] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelY = event.values[2] / SensorManager.GRAVITY_EARTH
					sensorControllerState.accelZ = event.values[0] / SensorManager.GRAVITY_EARTH
				}
				Sensor.TYPE_GYROSCOPE -> {
					sensorControllerState.gyroX = event.values[1]
					sensorControllerState.gyroY = event.values[2]
					sensorControllerState.gyroZ = event.values[0]
				}
				Sensor.TYPE_ROTATION_VECTOR -> {
					val q = floatArrayOf(0f, 0f, 0f, 0f)
					SensorManager.getQuaternionFromVector(q, event.values)
					sensorControllerState.orientX = q[2]
					sensorControllerState.orientY = q[3]
					sensorControllerState.orientZ = q[1]
					sensorControllerState.orientW = q[0]
				}
				else -> return
			}
			controllerStateUpdated()
		}

		override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
	}

	private val motionLifecycleObserver = object: LifecycleObserver {
		@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
		fun onResume()
		{
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			listOfNotNull(
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
				sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
			).forEach {
				sensorManager.registerListener(sensorEventListener, it, GYRO_SAMPLING_PERIOD_US)
			}
		}

		@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
		fun onPause()
		{
			val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
			sensorManager.unregisterListener(sensorEventListener)
		}
	}

	fun observe(lifecycleOwner: LifecycleOwner)
	{
		if(preferences.motionEnabled)
			lifecycleOwner.lifecycle.addObserver(motionLifecycleObserver)
	}

	private fun controllerStateUpdated()
	{
		controllerStateChangedCallback?.let { it(controllerState) }
	}

	private fun isGamepadEvent(event: KeyEvent): Boolean = PadManager.isGamepad(event.device)

	fun onHudComboKeyEvent(event: KeyEvent): Boolean
	{
		if(!padManager.isPrimary(event.deviceId))
			return false
		val combo = comboKeys
		if(event.keyCode !in combo)
			return false
		if(event.action == KeyEvent.ACTION_DOWN)
		{
			pressedComboKeys.add(event.keyCode)
			if(pressedComboKeys.containsAll(combo) && !hudComboFired)
			{
				hudComboFired = true
				clearComboButtons(combo)
				controllerStateUpdated()
				hudToggleCallback?.invoke()
				return true
			}
		}
		else
		{
			pressedComboKeys.remove(event.keyCode)
			if(!pressedComboKeys.containsAll(combo))
				hudComboFired = false
		}
		return false
	}

	private fun clearComboButtons(combo: Set<Int>)
	{
		var mask = 0U
		for(key in combo)
			mask = mask or when(key)
			{
				KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
				KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
				KeyEvent.KEYCODE_BUTTON_SELECT -> ControllerState.BUTTON_TOUCHPAD
				KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
				KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
				KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
				else -> 0U
			}
		keyControllerState.buttons = keyControllerState.buttons and mask.inv()
		if(KeyEvent.KEYCODE_BUTTON_L2 in combo)
			keyControllerState.l2State = 0U
		if(KeyEvent.KEYCODE_BUTTON_R2 in combo)
			keyControllerState.r2State = 0U
		if(KeyEvent.KEYCODE_BUTTON_SELECT in combo)
			keyControllerState.touches[0] = ControllerTouch(id = -1)
	}

	fun dispatchKeyEvent(event: KeyEvent): Boolean
	{
		//Log.i("StreamSession", "key event $event")
		if(event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP)
			return false

		// secondary pads are tracked but must not drive player 1 (until multi-controller lands)
		if(PadManager.isGamepad(event.device) && !padManager.isPrimary(event.deviceId))
		{
			val slot = padManager.slotOf(event.deviceId)
			if(slot != null && slot > 0 && secondaryPadSeen.add(slot))
				secondaryPadInputCallback?.invoke(slot)
			return true
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
			return false
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
