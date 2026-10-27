package com.chiaking.tv.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.LifecycleOwner
import com.metallic.chiaki.lib.ControllerState

/** Owns sensor registration, display rotation and orientation recentering. */
internal class StreamMotionSensors(private val context: Context, private val enabled: Boolean, private val onChanged: () -> Unit) {
    companion object { private const val GYRO_SAMPLING_PERIOD_US = 4000 }
    val sensorControllerState = ControllerState()
    @Volatile private var orientRef: FloatArray? = null
    private var isRotated90 = false

    // read at sensor rate (250 Hz) — never look the service up per call
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    fun reset()
    {
        var x = sensorControllerState.orientX
        val y = sensorControllerState.orientY
        var z = sensorControllerState.orientZ
        val w = sensorControllerState.orientW
        if(isRotated90)
        {
            x *= -1.0f
            z *= -1.0f
        }
        orientRef = if(w != 0f) floatArrayOf(x, y, z, w) else floatArrayOf(0f, 0f, 0f, 1f)
    }

    fun applyTo(controllerState: ControllerState) {
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
    }

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
            onChanged()
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val binding = ResumedResourceBinding(
        start = {
            listOfNotNull(
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ).forEach { sensorManager.registerListener(sensorEventListener, it, GYRO_SAMPLING_PERIOD_US) }
        },
        stop = { sensorManager.unregisterListener(sensorEventListener) }
    )

    fun observe(owner: LifecycleOwner) {
        if(enabled) binding.attach(owner)
    }
    fun unobserve(owner: LifecycleOwner) = binding.detach(owner)
    fun dispose() = binding.dispose()
}
