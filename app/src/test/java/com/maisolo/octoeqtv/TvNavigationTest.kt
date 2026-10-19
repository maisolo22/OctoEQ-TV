package com.maisolo.octoeqtv

import android.content.ComponentName
import android.content.Intent
import org.junit.Before
import org.junit.After
import org.robolectric.Shadows.shadowOf
import org.robolectric.RuntimeEnvironment
import android.view.KeyEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TvNavigationTest {
    private lateinit var service: org.robolectric.android.controller.ServiceController<AudioService>
    @Before fun connectRealServiceBinder() {
        service = Robolectric.buildService(AudioService::class.java).create()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, AudioService::class.java), service.get().onBind(Intent()))
    }
    @After fun releaseService() { service.destroy() }

    @Test fun remoteConfirmsOnReleaseAndBackExitsWithoutChangingGain() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val sliderClass = Class.forName("com.maisolo.octoeqtv.MainActivity\$BandSlider")
        val constructor = sliderClass.declaredConstructors.first().apply { isAccessible = true }
        var gain = 0
        val changed: (Int) -> Unit = { gain = it }
        val slider = constructor.newInstance(activity, activity, "1k", 0, -1200, 1200, -1, changed) as View
        fun key(action: Int, code: Int) = slider.dispatchKeyEvent(KeyEvent(action, code))
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(100, gain)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(100, gain) // Navigation must not require max gain.
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertTrue(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(100, gain)
        controller.pause().stop().destroy()
    }
    @Test fun preferencesSurviveActivityRecreation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val prefs = controller.get().getSharedPreferences("studio", 0)
        prefs.edit().putInt("band_0", 300).putInt("bass", 400).putInt("surround", 200).putBoolean("enabled", false).commit()
        controller.recreate()
        val restored = controller.get().getSharedPreferences("studio", 0)
        assertEquals(300, restored.getInt("band_0", 0))
        assertEquals(400, restored.getInt("bass", 0))
        assertEquals(200, restored.getInt("surround", 0))
        assertFalse(restored.getBoolean("enabled", true))
        controller.pause().stop().destroy()
    }
}
