package com.example.nino_home

import android.view.KeyEvent

/**
 * Lets Now Playing intercept phone volume keys and drive robot volume
 * instead of the system media volume slider.
 */
object VolumeKeyDispatcher {
    @Volatile
    var handler: ((keyCode: Int, action: Int) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP &&
            code != KeyEvent.KEYCODE_VOLUME_DOWN &&
            code != KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return false
        }
        return handler?.invoke(code, event.action) == true
    }
}
