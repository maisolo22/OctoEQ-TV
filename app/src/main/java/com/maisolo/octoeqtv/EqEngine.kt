package com.maisolo.octoeqtv

import android.annotation.TargetApi
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.os.Build

/** One EQ, one session. All native writes are owned by AudioService. */
@Suppress("DEPRECATION")
abstract class EqEngine(val session: Int) {
    abstract val nativeEffect: AudioEffect
    abstract val frequencies: IntArray
    abstract val linear: Boolean
    abstract val label: String
    abstract val bandLevelRange: ShortArray
    val numberOfBands: Short get() = frequencies.size.toShort()
    val gainKeys get() = frequencies.indices.map { AudioPolicy.key(linear, it) }
    private val updates = AudioPolicy.BandUpdates()
    private var lastEnabled: Boolean? = null
    fun getCenterFreq(band: Short) = frequencies[band.toInt()] * 1000
    fun hasControl() = nativeEffect.hasControl()
    fun invalidate() { updates.invalidate(); lastEnabled = null }
    fun apply(levels: IntArray, enabled: Boolean) {
        require(levels.size == frequencies.size)
        try {
            for ((index, gain) in updates.changed(levels)) writeBand(index, gain)
            if (lastEnabled != enabled) {
                check(nativeEffect.setEnabled(enabled) == AudioEffect.SUCCESS) { "Activation refusée" }
                lastEnabled = enabled
            }
        } catch (e: RuntimeException) { invalidate(); throw e }
    }
    protected abstract fun writeBand(index: Int, level: Int)
    fun release() = nativeEffect.release()

    companion object {
        fun create(session: Int, faithful: Boolean): Pair<EqEngine?, String?> {
            var reason: String? = null
            if (faithful) {
                if (Build.VERSION.SDK_INT >= 28) {
                    try { return LinearEq(session) to null }
                    catch (e: RuntimeException) { reason = "DynamicsProcessing refusé (${e.javaClass.simpleName})" }
                } else reason = "Mode fidèle : Android 9 minimum"
            }
            return try { LegacyEq(session) to reason }
            catch (e: RuntimeException) { null to listOfNotNull(reason, "Equalizer refusé (${e.javaClass.simpleName})").joinToString(" • ") }
        }
    }
}

@Suppress("DEPRECATION")
private class LegacyEq(session: Int) : EqEngine(session) {
    private val effect = Equalizer(0, session)
    override val nativeEffect get() = effect
    override val linear = false
    override val label = "Equalizer Android • compensation constructeur possible"
    override val frequencies: IntArray
    override val bandLevelRange: ShortArray
    init {
        try {
            frequencies = IntArray(effect.numberOfBands.toInt()) { effect.getCenterFreq(it.toShort()) / 1000 }
            check(frequencies.isNotEmpty()) { "Aucune bande" }
            bandLevelRange = effect.bandLevelRange
        } catch (e: RuntimeException) { effect.release(); throw e }
    }
    override fun writeBand(index: Int, level: Int) { effect.setBandLevel(index.toShort(), level.toShort()) }
}

/** Only the pre-EQ stage is present. No MBC, post-EQ, limiter, or gain normalization. */
@TargetApi(28)
private class LinearEq(session: Int) : EqEngine(session) {
    override val linear = true
    override val label = "Fidèle • DynamicsProcessing • sans compression configurée"
    override val frequencies = AudioPolicy.centers.copyOf()
    // Moderate positive boosts; no automatic preamp reduction when any band changes.
    override val bandLevelRange = shortArrayOf(-1200, 600)
    private val effect: DynamicsProcessing
    override val nativeEffect get() = effect
    init {
        val config = LinearConfiguration.create()
        effect = DynamicsProcessing(0, session, config)
        try {
            effect.setInputGainAllChannelsTo(0f)
            check(effect.config.preEqBandCount == frequencies.size) { "Configuration EQ rejetée" }
        } catch (e: RuntimeException) { effect.release(); throw e }
    }
    override fun writeBand(index: Int, level: Int) {
        effect.setPreEqBandAllChannelsTo(index, DynamicsProcessing.EqBand(true, AudioPolicy.cutoffs[index], level / 100f))
    }
}

@TargetApi(28)
internal object LinearConfiguration {
    fun create(): DynamicsProcessing.Config {
        val preEq = DynamicsProcessing.Eq(true, true, AudioPolicy.centers.size)
        for (i in AudioPolicy.centers.indices) preEq.setBand(i, DynamicsProcessing.EqBand(true, AudioPolicy.cutoffs[i], 0f))
        return DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION, 2,
            true, AudioPolicy.centers.size, false, 0, false, 0, false)
            .setInputGainAllChannelsTo(0f).setPreEqAllChannelsTo(preEq).build()
    }
}
