package com.maisolo.octoeqtv

import android.content.Intent
import android.media.audiofx.AudioEffect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionLifecycleTest {
    @Test fun announcedSessionsReplaceGlobalAndCloseRestoresFallback() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        service.getSharedPreferences("studio", 0).edit().putString("mode", "auto").commit()
        service.announce(42, "test.player")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf(42), service.attachedSessions)
        service.sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, 42).putExtra(AudioEffect.EXTRA_PACKAGE_NAME, "test.player"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(setOf(0), service.attachedSessions)
        controller.destroy()
    }
    @Test fun malformedSessionsAreIgnoredAndSessionCountIsBounded() {
        val controller = Robolectric.buildService(AudioService::class.java).create()
        val service = controller.get()
        service.announce(-1, "test.player"); service.announce(0, "test.player"); service.announce(1, "")
        assertTrue(service.announcedSessions.isEmpty())
        for (id in 1..20) service.announce(id, "test.player")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(8, service.announcedSessions.size)
        assertFalse(service.attachedSessions.contains(0))
        controller.destroy()
    }
}
