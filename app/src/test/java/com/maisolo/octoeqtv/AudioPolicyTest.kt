package com.maisolo.octoeqtv

import org.junit.Assert.*
import org.junit.Test

class AudioPolicyTest {
    @Test fun changingOneBandDoesNotRewriteOrNormalizeOthers() {
        val updates = AudioPolicy.BandUpdates()
        val gains = intArrayOf(100, -200, 0, 300, 0)
        updates.changed(gains)
        gains[2] = 400
        assertEquals(listOf(2 to 400), updates.changed(gains))
        assertArrayEquals(intArrayOf(100, -200, 400, 300, 0), gains)
        assertTrue(updates.changed(gains).isEmpty())
    }
    @Test fun hardwareLimitsClampOnlyTheAffectedBand() {
        val source = intArrayOf(31, 62, 125, 250)
        val gains = intArrayOf(0, 1200, -900, 100)
        assertArrayEquals(intArrayOf(0, 600, -900, 100), AudioPolicy.mapCurve(source, gains, source, -1200, 600))
    }
    @Test fun sessionsWithDifferentBandCountsUseTheSameCurve() {
        assertArrayEquals(intArrayOf(0, 300, 600), AudioPolicy.mapCurve(intArrayOf(100, 400), intArrayOf(0, 600), intArrayOf(100, 200, 400), -1200, 600))
    }
    @Test fun noDuplicateSessionAndNoPrematureReleaseForAnotherOwner() {
        val registry = SessionRegistry()
        assertTrue(registry.open(4, "player.a"))
        assertFalse(registry.open(4, "player.a"))
        assertFalse(registry.open(4, "player.b"))
        assertFalse(registry.close(4, "unknown"))
        assertFalse(registry.close(4, "player.a"))
        assertTrue(registry.close(4, "player.b"))
        assertTrue(registry.ids().isEmpty())
    }
    @Test fun ignoreGlobalAndInvalidBroadcastSessionsAndBoundResourceUse() {
        val registry = SessionRegistry(1)
        assertFalse(registry.open(0, "a"))
        assertFalse(registry.open(-1, "a"))
        assertFalse(registry.open(1, ""))
        assertTrue(registry.open(1, "a"))
        assertFalse(registry.open(2, "b"))
        registry.clear(); assertTrue(registry.open(2, "b"))
    }
    @Test fun reacquiredControlRestoresAllCoefficients() {
        val changes = AudioPolicy.BandUpdates()
        val gains = intArrayOf(0, 200)
        changes.changed(gains); changes.invalidate()
        assertEquals(listOf(0 to 0, 1 to 200), changes.changed(gains))
    }
}
