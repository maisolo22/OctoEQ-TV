package com.maisolo.octoeqtv

import android.content.SharedPreferences
import android.media.audiofx.*
import android.os.Build

/** Android owns the PCM. No capture, session guessing or root access. */
@android.annotation.SuppressLint("NewApi")
@Suppress("DEPRECATION")
internal class SessionEffects(val session: Int, private val prefs: SharedPreferences) {
    private var dynamics: DynamicsProcessing? = null
    var eq: Equalizer? = null; private set
    var bass: BassBoost? = null; private set
    var virtualizer: Virtualizer? = null; private set
    var lastError: String? = null; private set
    val advanced get() = dynamics != null
    val bands get() = if (advanced) 10 else runCatching { eq?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)
    val controlled get() = runCatching { bands > 0 && (dynamics ?: eq)?.hasControl() == true }.getOrDefault(false)
    val bassSupported get() = runCatching { bass?.hasControl() == true && bass?.strengthSupported == true }.getOrDefault(false)
    val spatialSupported get() = runCatching { virtualizer?.hasControl() == true && virtualizer?.strengthSupported == true }.getOrDefault(false)
    val engineName get() = if (advanced) "DynamicsProcessing · 10 bandes + préampli + limiteur" else "Equalizer Android · $bands bandes réelles"

    init {
        // Session zero is legacy-only. DP is attempted only on an announced player session.
        if (session > 0 && Build.VERSION.SDK_INT >= 28) {
            var candidate: DynamicsProcessing? = null
            try {
                candidate = DynamicsProcessing(0, session, null)
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    candidate.channelCount, true, 10, false, 0, false, 0, true).build()
                candidate.release()
                candidate = DynamicsProcessing(0, session, config)
                if (candidate.hasControl()) dynamics = candidate else candidate.release()
            } catch (_: RuntimeException) { runCatching { candidate?.release() } }
        }
        if (dynamics == null) eq = runCatching { Equalizer(0, session) }.getOrNull()
        bass = runCatching { BassBoost(0, session) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, session) }.getOrNull()
    }

    fun effects(): List<AudioEffect> = listOfNotNull(dynamics, eq, bass, virtualizer)
    fun apply(bypass: Boolean) {
        lastError = null
        val enabled = prefs.getBoolean("enabled", true) && !bypass
        val gains = StudioTone.gains(prefs)
        val headroom = StudioTone.headroom(gains, prefs.getInt("preamp", 0), prefs.getBoolean("safe_gain", true))
        dynamics?.let { effect ->
            try {
                if (effect.hasControl()) {
                    val pre = DynamicsProcessing.Eq(true, true, 10)
                    StudioTone.frequencies.forEachIndexed { index, hz ->
                        pre.setBand(index, DynamicsProcessing.EqBand(true, hz, gains[index] / 100f))
                    }
                    effect.setPreEqAllChannelsTo(pre)
                    effect.setInputGainAllChannelsTo(headroom / 100f)
                    effect.setLimiterAllChannelsTo(DynamicsProcessing.Limiter(true,
                        prefs.getBoolean("limiter", true), 0, 1f, 100f, 20f, -1f, 0f))
                    effect.enabled = enabled
                }
            } catch (e: RuntimeException) { lastError = "DSP : ${e.javaClass.simpleName}" }
        }
        eq?.let { effect ->
            try {
                if (effect.hasControl()) {
                    val range = effect.bandLevelRange
                    for (index in 0 until effect.numberOfBands.toInt()) {
                        val gain = EqMath.gainAt(effect.getCenterFreq(index.toShort()) / 1000f, gains)
                        // Legacy EQ has no true preamp; approximate headroom with a common cut.
                        effect.setBandLevel(index.toShort(), (gain + headroom)
                            .coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                    }
                    effect.enabled = enabled
                }
            } catch (e: RuntimeException) { lastError = "EQ : ${e.javaClass.simpleName}" }
        }
        bass?.let { effect -> try {
            if (effect.hasControl()) {
                val strength = prefs.getInt("bass", 0).coerceIn(0, 1000)
                if (effect.strengthSupported) effect.setStrength(strength.toShort())
                effect.enabled = enabled && effect.strengthSupported && strength > 0
            }
        } catch (e: RuntimeException) { lastError = "Basses : ${e.javaClass.simpleName}" } }
        virtualizer?.let { effect -> try {
            if (effect.hasControl()) {
                val strength = prefs.getInt("surround", 0).coerceIn(0, 1000)
                if (effect.strengthSupported) effect.setStrength(strength.toShort())
                effect.enabled = enabled && effect.strengthSupported && strength > 0
            }
        } catch (e: RuntimeException) { lastError = "Spatialisation : ${e.javaClass.simpleName}" } }
    }
    fun release() { effects().forEach { runCatching { it.release() } } }
}
