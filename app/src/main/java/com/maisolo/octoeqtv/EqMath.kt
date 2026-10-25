package com.maisolo.octoeqtv

import kotlin.math.ln
import kotlin.math.roundToInt

object EqMath {
    private val frequencies = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    fun step(level: Int, delta: Int, min: Int, max: Int) = (level + delta).coerceIn(min, max)
    /** Interpolate on the log frequency axis rather than assuming evenly spaced hardware bands. */
    fun gainAt(hz: Float, gains: IntArray): Int {
        require(gains.size == frequencies.size)
        if (hz <= frequencies.first()) return gains.first()
        if (hz >= frequencies.last()) return gains.last()
        val upper = frequencies.indexOfFirst { it >= hz }
        val lower = upper - 1
        val weight = ln(hz / frequencies[lower]) / ln(frequencies[upper] / frequencies[lower])
        return (gains[lower] + weight * (gains[upper] - gains[lower])).roundToInt()
    }
}

/** Each band owns its edit state; leaving focus always returns to navigation. */
class BandNavigation {
    var editing = false
    fun confirm() { editing = !editing }
    fun leave() { editing = false }
}

