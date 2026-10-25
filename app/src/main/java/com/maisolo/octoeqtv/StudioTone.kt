package com.maisolo.octoeqtv

import android.content.SharedPreferences

internal object StudioTone {
    val frequencies = floatArrayOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    fun gains(prefs: SharedPreferences) = IntArray(10) { prefs.getInt("gain_$it", 0).coerceIn(-1200, 1200) }
    fun headroom(gains: IntArray, preamp: Int, safe: Boolean): Int {
        val requested = preamp.coerceIn(-1800, 0)
        return if (safe) minOf(requested, -(gains.maxOrNull() ?: 0).coerceAtLeast(0)) else requested
    }
    // Conservative equivalents of the octoStream tone curves; no doubled bass/spatial boost.
    fun preset(name: String): IntArray = when (name) {
        "Basses +" -> intArrayOf(400,400,300,100,0,0,0,0,0,0)
        "Aigus +" -> intArrayOf(0,0,0,0,0,0,100,200,300,400)
        "Vocal" -> intArrayOf(-200,-200,-100,0,100,300,300,100,0,-100)
        "Pop" -> intArrayOf(200,100,0,-100,0,100,200,200,100,100)
        "Rock" -> intArrayOf(300,300,200,0,-100,-100,100,200,300,200)
        "Jazz" -> intArrayOf(100,200,100,0,0,100,200,100,100,0)
        "Cinéma" -> intArrayOf(200,100,0,0,100,200,200,100,0,0)
        "Concert" -> intArrayOf(100,100,0,0,0,100,100,100,100,0)
        else -> IntArray(10)
    }
}
