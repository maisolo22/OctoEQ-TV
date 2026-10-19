package com.maisolo.octoeqtv
import org.junit.Assert.*
import org.junit.Test

class EqMathTest {
    @Test fun presetUsesActualFrequency() {
        val gains = intArrayOf(0,100,200,300,400,500,600,700,800,900)
        assertEquals(500, EqMath.gainAt(1000f, gains))
        assertEquals(550, EqMath.gainAt(kotlin.math.sqrt(1000f * 2000f), gains))
        assertEquals(0, EqMath.gainAt(10f, gains))
        assertEquals(900, EqMath.gainAt(24000f, gains))
    }
    @Test fun gainDoesNotExceedHardwareLimits() {
        assertEquals(300, EqMath.step(250,100,-300,300))
        assertEquals(-300, EqMath.step(-250,-100,-300,300))
    }
    @Test fun confirmationAndFocusExitDoNotDependOnGain() {
        val state = BandNavigation()
        state.confirm(); assertTrue(state.editing)
        state.confirm(); assertFalse(state.editing)
        state.confirm(); state.leave(); assertFalse(state.editing)
    }
}
