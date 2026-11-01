package com.maisolo.octoeqtv

/** No normalization, no dependence on the loudest band, no system-volume writes. */
object AudioPolicy {
    val centers = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val cutoffs = floatArrayOf(44f, 88f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 24000f)
    fun key(linear: Boolean, band: Int) = if (linear) "linear_band_$band" else "band_$band"
    fun mapCurve(sourceHz: IntArray, gains: IntArray, targetHz: IntArray, min: Int, max: Int): IntArray {
        require(sourceHz.isNotEmpty() && sourceHz.size == gains.size)
        require(sourceHz.all { it > 0 } && sourceHz.asList().zipWithNext().all { it.first < it.second })
        return IntArray(targetHz.size) { i ->
            val hz = targetHz[i]
            val value = when {
                hz <= sourceHz.first() -> gains.first()
                hz >= sourceHz.last() -> gains.last()
                else -> {
                    val upper = sourceHz.indexOfFirst { it >= hz }
                    val lower = upper - 1
                    val fraction = kotlin.math.ln(hz.toDouble() / sourceHz[lower]) / kotlin.math.ln(sourceHz[upper].toDouble() / sourceHz[lower])
                    kotlin.math.round(gains[lower] + fraction * (gains[upper] - gains[lower])).toInt()
                }
            }
            sanitize(value, min, max)
        }
    }
    fun sanitize(level: Int, min: Int, max: Int) = level.coerceIn(min, max)
    fun validSession(session: Int, packageName: String?) = session > 0 && !packageName.isNullOrBlank()
    // Apply only changed coefficients; never recreate/enable the effect per slider tick.
    class BandUpdates {
        private var previous = intArrayOf()
        fun changed(levels: IntArray): List<Pair<Int, Int>> {
            val changes = levels.indices.filter { it >= previous.size || previous[it] != levels[it] }
                .map { it to levels[it] }
            previous = levels.copyOf()
            return changes
        }
        fun invalidate() { previous = intArrayOf() }
    }
}

/** Tracks multiple cooperating players without ever adding the same session twice. */
class SessionRegistry(private val maximum: Int = 16) {
    private val owners = linkedMapOf<Int, MutableSet<String>>()
    fun open(session: Int, owner: String): Boolean {
        if (!AudioPolicy.validSession(session, owner)) return false
        owners[session]?.let { it.add(owner); return false }
        if (owners.size >= maximum) return false
        owners[session] = mutableSetOf(owner)
        return true
    }
    fun close(session: Int, owner: String): Boolean {
        val packages = owners[session] ?: return false
        if (!packages.remove(owner)) return false
        if (packages.isNotEmpty()) return false
        owners.remove(session); return true
    }
    fun forget(session: Int) { owners.remove(session) }
    fun ids(): Set<Int> = owners.keys.toSet()
    fun clear() = owners.clear()
}
