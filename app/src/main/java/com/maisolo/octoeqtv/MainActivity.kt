package com.maisolo.octoeqtv

import android.app.Activity
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Global audio session 0 is deprecated. Not all TV audio routes/apps support these effects. */
@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private val prefs by lazy { getSharedPreferences("eq", MODE_PRIVATE) }
    private val ink = Color.WHITE
    private val accent = Color.rgb(41, 210, 151)
    private lateinit var board: LinearLayout
    private lateinit var state: TextView
    private var enabled = true

    private fun label(parent: LinearLayout, s: String, size: Float = 17f): TextView =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(ink); gravity = Gravity.CENTER
            setPadding(8, 9, 8, 9); parent.addView(this)
        }

    private fun button(parent: LinearLayout, s: String, action: () -> Unit): Button =
        Button(this).apply {
            text = s; isFocusable = true
            setOnClickListener { action() }
            parent.addView(this)
        }

    private fun column(parent: LinearLayout, title: String, min: Int, max: Int, initial: Int, step: Int, apply: (Int) -> Unit) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(7, 8, 7, 8)
        }
        parent.addView(col, LinearLayout.LayoutParams(118, -2))
        label(col, title, 14f)
        var current = initial.coerceIn(min, max)
        val value = label(col, formatDb(current), 15f)
        val meter = TextView(this).apply {
            gravity = Gravity.CENTER; textSize = 17f; setTextColor(accent)
            setBackgroundColor(0xff1b2930.toInt())
        }
        col.addView(meter, LinearLayout.LayoutParams(78, 124))
        fun refresh() {
            val fraction = if (max == min) 0f else (current - min).toFloat() / (max - min)
            val blocks = (fraction * 10).toInt().coerceIn(0, 10)
            meter.text = (0 until 10).joinToString("\n") { if (it < 10 - blocks) "·" else "▰" }
            value.text = formatDb(current)
        }
        button(col, "＋") { current = (current + step).coerceAtMost(max); apply(current); refresh() }
        button(col, "－") { current = (current - step).coerceAtLeast(min); apply(current); refresh() }
        refresh()
    }

    private fun formatDb(mb: Int) = String.format(java.util.Locale.US, "%+.1f dB", mb / 100f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(26, 18, 26, 16)
            setBackgroundColor(0xff10131b.toInt())
        }
        setContentView(root)
        label(root, "OCTOEQ TV  •  STUDIO AUDIO", 25f)
        state = label(root, "Initialisation…", 15f)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(actions)
        enabled = prefs.getBoolean("enabled", true)
        val power = button(actions, "") {
            enabled = !enabled; prefs.edit().putBoolean("enabled", enabled).apply()
            applyEnabled(); updatePower()
        }
        funPower@ run { /* controls initialized before effect discovery */ }
        val reset = button(actions, "Remise à zéro") {
            val effect = eq ?: return@button
            for (i in 0 until effect.numberOfBands.toInt()) {
                runCatching { effect.setBandLevel(i.toShort(), 0) }
                prefs.edit().putInt("band_$i", 0).apply()
            }
            runCatching { bass?.setStrength(0); virtualizer?.setStrength(0) }
            prefs.edit().putInt("bass", 0).putInt("surround", 0).apply()
            recreate()
        }
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(presets)
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        board = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        scroll.addView(board)
        try {
            eq = Equalizer(0, 0)
            bass = runCatching { BassBoost(0, 0) }.getOrNull()
            virtualizer = runCatching { Virtualizer(0, 0) }.getOrNull()
            val effect = eq!!
            val count = effect.numberOfBands.toInt()
            val min = effect.bandLevelRange[0].toInt()
            val max = effect.bandLevelRange[1].toInt()
            val step = 100
            fun preset(name: String, shape: (Int, Int) -> Int) {
                button(presets, name) {
                    for (i in 0 until count) {
                        val level = shape(i, count).coerceIn(min, max)
                        runCatching { effect.setBandLevel(i.toShort(), level.toShort()) }
                        prefs.edit().putInt("band_$i", level).apply()
                    }
                    recreate()
                }
            }
            preset("Plat") { _, _ -> 0 }
            preset("Basses +") { i, n -> if (i < n / 3) 500 else 0 }
            preset("Aigus +") { i, n -> if (i >= n * 2 / 3) 500 else 0 }
            preset("Vocal") { i, n -> if (i in n / 3..n * 2 / 3) 300 else -100 }
            for (i in 0 until count) {
                val band = i.toShort()
                val freq = effect.getCenterFreq(band) / 1000
                val name = if (freq >= 1000) "${freq / 1000f}k" else "${freq}Hz"
                val current = prefs.getInt("band_$i", 0).coerceIn(min, max)
                runCatching { effect.setBandLevel(band, current.toShort()) }
                column(board, name, min, max, current, step) {
                    runCatching { effect.setBandLevel(band, it.toShort()) }
                    prefs.edit().putInt("band_$i", it).apply()
                }
            }
            bass?.let { b ->
                val current = prefs.getInt("bass", 0).coerceIn(0, 1000)
                runCatching { b.setStrength(current.toShort()) }
                column(board, "BASS", 0, 1000, current, 100) {
                    runCatching { b.setStrength(it.toShort()) }
                    prefs.edit().putInt("bass", it).apply()
                }
            }
            virtualizer?.let { v ->
                val current = prefs.getInt("surround", 0).coerceIn(0, 1000)
                runCatching { v.setStrength(current.toShort()) }
                column(board, "WIDE", 0, 1000, current, 100) {
                    runCatching { v.setStrength(it.toShort()) }
                    prefs.edit().putInt("surround", it).apply()
                }
            }
            applyEnabled()
            state.text = "Mode global expérimental • $count bandes matérielles • Réglages mémorisés, effet après fermeture non garanti"
        } catch (e: Exception) {
            state.text = "Effets audio indisponibles sur cette TV : ${e.javaClass.simpleName}"
        }
        power.text = if (enabled) "EQ ACTIVÉ" else "EQ DÉSACTIVÉ"
        label(root, "◀ ▶ : changer de commande     OK : ajuster     •     Pas de traitement garanti pour toutes les applications", 13f)
    }

    private fun updatePower() {
        val root = (findViewById<android.view.View>(android.R.id.content) as android.view.ViewGroup)
        val main = root.getChildAt(0) as? LinearLayout ?: return
        val actions = main.getChildAt(2) as? LinearLayout ?: return
        (actions.getChildAt(0) as? Button)?.text = if (enabled) "EQ ACTIVÉ" else "EQ DÉSACTIVÉ"
    }
    private fun applyEnabled() {
        runCatching { eq?.enabled = enabled }
        runCatching { bass?.enabled = enabled }
        runCatching { virtualizer?.enabled = enabled }
    }
    override fun onDestroy() {
        eq?.release(); bass?.release(); virtualizer?.release()
        super.onDestroy()
    }
}
