package com.maisolo.octoeqtv

import android.app.Activity
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Standalone Android TV equalizer companion; compatibility depends on the device audio HAL.
 * Global mix effects (session 0) are deprecated and might have no audible effect.
 * For reliable processing inside octoStream use its existing PCM StudioAudioProcessor.
 */
class MainActivity : Activity() {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private val prefs by lazy { getSharedPreferences("eq", MODE_PRIVATE) }
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 24)
            setBackgroundColor(0xff10131b.toInt())
        }
        val title = TextView(this).apply {
            text = "OctoEQ TV     •     Égaliseur & scène sonore"
            textSize = 27f
            setTextColor(-1)
        }
        root.addView(title)
        status = text(root, "Initialisation…", 15f)
        val scroller = ScrollView(this).apply { isFillViewport = true }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 16, 0, 0) }
        scroller.addView(content)
        root.addView(scroller, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        try {
            @Suppress("DEPRECATION")
            val audioSession = 0
            eq = Equalizer(0, audioSession)
            bass = runCatching { BassBoost(0, audioSession) }.getOrNull()
            virtualizer = runCatching { Virtualizer(0, audioSession) }.getOrNull()
            eq?.enabled = true
            bass?.enabled = true
            virtualizer?.enabled = true
            status.text = "Mode expérimental : effets globaux Android. Certaines TV ignorent ces réglages. Testez à l'oreille."
            val bands = eq?.numberOfBands?.toInt() ?: 0
            val range = eq!!.bandLevelRange
            text(content, "Égaliseur ($bands bandes disponibles)", 21f)
            for (i in 0 until bands) {
                val band = i.toShort()
                val hz = eq!!.getCenterFreq(band) / 1000
                val min = range[0].toInt()
                val max = range[1].toInt()
                addSlider(content, "${hz} Hz", min, max, prefs.getInt("band_$i", eq!!.getBandLevel(band).toInt())) {
                    eq?.setBandLevel(band, it.toShort())
                    prefs.edit().putInt("band_$i", it).apply()
                }
            }
            text(content, "Graves et spatialisation", 21f)
            bass?.let { effect ->
                addSlider(content, "Bass Boost", 0, 1000, prefs.getInt("bass", 0)) {
                    effect.setStrength(it.toShort()); prefs.edit().putInt("bass", it).apply()
                }
            } ?: text(content, "Bass Boost non disponible sur cet appareil.", 16f)
            virtualizer?.let { effect ->
                addSlider(content, "Surround virtuel (dépend de la sortie audio)", 0, 1000, prefs.getInt("surround", 0)) {
                    effect.setStrength(it.toShort()); prefs.edit().putInt("surround", it).apply()
                }
            } ?: text(content, "Virtualizer non disponible sur cet appareil.", 16f)
            val reset = Button(this).apply {
                text = "Réinitialiser"
                setOnClickListener {
                    for (i in 0 until bands) {
                        eq?.setBandLevel(i.toShort(), 0)
                        prefs.edit().putInt("band_$i", 0).apply()
                    }
                    bass?.setStrength(0); virtualizer?.setStrength(0)
                    prefs.edit().putInt("bass", 0).putInt("surround", 0).apply()
                    recreate()
                }
            }
            content.addView(reset)
            text(content, "Note : le son 5.1 réel nécessite une source multicanal et une sortie compatible. Cette application n'ajoute pas de canaux 5.1.", 15f)
        } catch (e: Exception) {
            status.text = "Effets audio indisponibles sur cette TV : ${e.javaClass.simpleName}. Le moteur octoStream interne reste la solution fiable."
        }
    }

    private fun text(parent: LinearLayout, value: String, size: Float): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(-1)
            setPadding(4, 10, 4, 10)
            parent.addView(this)
        }

    private fun addSlider(parent: LinearLayout, label: String, min: Int, max: Int, start: Int, change: (Int) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 12) }
        val labelView = text(row, label, 17f)
        val slider = SeekBar(this).apply {
            this.max = max - min
            progress = (start - min).coerceIn(0, max - min)
            isFocusable = true
            isFocusableInTouchMode = false
        }
        row.addView(slider, LinearLayout.LayoutParams(-1, 54))
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) runCatching { change(progress + min) }
                labelView.text = "$label  •  ${(progress + min) / 100f} dB/force"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        // D-pad left/right adjusts SeekBar natively.
        parent.addView(row)
    }

    override fun onDestroy() {
        eq?.release()
        bass?.release()
        virtualizer?.release()
        super.onDestroy()
    }
}
