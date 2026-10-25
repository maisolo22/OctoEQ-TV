package com.maisolo.octoeqtv

import org.junit.Assert.*
import org.junit.Test

class StudioToneTest {
    @Test fun automaticHeadroomCancelsLargestPositiveGain() {
        assertEquals(-400, StudioTone.headroom(StudioTone.preset("Basses +"), 0, true))
        assertEquals(-900, StudioTone.headroom(StudioTone.preset("Basses +"), -900, true))
        assertEquals(0, StudioTone.headroom(IntArray(10) { -200 }, 0, true))
        assertEquals(-300, StudioTone.headroom(StudioTone.preset("Rock"), -300, false))
    }
    @Test fun presetsAreBoundedAndHaveTenIndependentControls() {
        for (name in listOf("Plat", "Basses +", "Aigus +", "Vocal", "Rock", "Jazz", "Cinéma", "Concert")) {
            val curve = StudioTone.preset(name)
            assertEquals(10, curve.size)
            assertTrue(curve.all { it in -1200..1200 })
        }
        assertArrayEquals(IntArray(10), StudioTone.preset("Plat"))
    }
}
