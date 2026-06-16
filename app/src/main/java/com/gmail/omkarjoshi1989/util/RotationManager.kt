package com.gmail.omkarjoshi1989.util

import android.app.Activity
import android.content.Context
import android.hardware.SensorManager
import android.view.OrientationEventListener
import android.view.Surface

/**
 * Manages screen rotation for media viewing activities (images, videos, audio, PDFs).
 *
 * This utility ignores the device's screen rotation setting and instead manages rotation
 * based on the device's actual physical orientation, detected via the accelerometer/rotation sensor.
 *
 * Usage:
 *   1. Create instance: val rotationManager = RotationManager(activity)
 *   2. Enable in onCreate: rotationManager.enableAutoRotation()
 *   3. Disable in onDestroy: rotationManager.disableAutoRotation()
 */
class RotationManager(private val activity: Activity) {

    private var orientationListener: OrientationEventListener? = null

    /**
     * Enables auto-rotation based on device orientation.
     * This will ignore the system's "Auto-rotate" setting.
     */
    fun enableAutoRotation() {
        disableAutoRotation() // Ensure no previous listener is active

        orientationListener = object : OrientationEventListener(
            activity,
            SensorManager.SENSOR_DELAY_NORMAL
        ) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                // Map device orientation to screen orientation
                val screenOrientation = when {
                    // Portrait: 330-30 degrees
                    orientation in 330..360 || orientation in 0..30 -> {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    // Landscape: 60-120 degrees
                    orientation in 60..120 -> {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                    // Portrait (upside down): 150-210 degrees
                    orientation in 150..210 -> {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                    }
                    // Reverse landscape: 240-300 degrees
                    orientation in 240..300 -> {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    else -> return // In between ranges, don't change
                }

                // Only update if orientation changed to avoid excessive updates
                if (activity.requestedOrientation != screenOrientation) {
                    activity.requestedOrientation = screenOrientation
                }
            }
        }

        orientationListener?.enable()
    }

    /**
     * Disables auto-rotation and stops listening to orientation events.
     */
    fun disableAutoRotation() {
        orientationListener?.disable()
        orientationListener = null
    }

    /**
     * Force the activity to portrait orientation (landscape disabled).
     * Useful if you need to temporarily lock the orientation.
     */
    fun lockPortrait() {
        disableAutoRotation()
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    /**
     * Force the activity to landscape orientation (portrait disabled).
     * Useful if you need to temporarily lock the orientation.
     */
    fun lockLandscape() {
        disableAutoRotation()
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    /**
     * Release resources when activity is destroyed.
     */
    fun release() {
        disableAutoRotation()
    }
}

