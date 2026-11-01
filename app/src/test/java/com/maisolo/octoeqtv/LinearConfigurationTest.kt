package com.maisolo.octoeqtv

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LinearConfigurationTest {
    @Test fun faithfulEngineHasNoCompressionLimiterOrAutomaticInputAttenuation() {
        val config = LinearConfiguration.create()
        LinearConfiguration.validate(config)
        assertTrue(config.isPreEqInUse)
        assertFalse(config.isMbcInUse)
        assertFalse(config.isPostEqInUse)
        assertFalse(config.isLimiterInUse)
        assertEquals(10, config.preEqBandCount)
        for (channel in 0 until 2) {
            assertEquals(0f, config.getInputGainByChannelIndex(channel), 0f)
            val eq = config.getPreEqByChannelIndex(channel)
            for (i in AudioPolicy.centers.indices) {
                assertTrue(eq.getBand(i).isEnabled)
                assertEquals(0f, eq.getBand(i).gain, 0f)
                assertEquals(AudioPolicy.cutoffs[i], eq.getBand(i).cutoffFrequency, 0f)
            }
        }
    }
    @Test(expected = IllegalStateException::class)
    fun rejectAnOemConfigWithUnexpectedGlobalAttenuation() {
        val config = LinearConfiguration.create()
        config.setInputGainAllChannelsTo(-6f)
        LinearConfiguration.validate(config)
    }
}
