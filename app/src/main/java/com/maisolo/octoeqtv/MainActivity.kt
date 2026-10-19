package com.maisolo.octoeqtv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Build
import android.graphics.*
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.view.*
import android.widget.*
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Android TV studio. Uses the number of EQ bands actually exposed by the device.
 * Session 0 is deprecated: effects may not work outside this app.
 */
@Suppress("DEPRECATION")
class MainActivity : Activity() {
    private val bg = Color.rgb(7, 13, 27)
    private val panel = Color.rgb(14, 27, 47)
    private val cyan = Color.rgb(29, 171, 255)
    private val white = Color.WHITE
    private val muted = Color.rgb(168, 190, 218)
    private val palette = intArrayOf(0xff37ed6f.toInt(), 0xff12ddc9.toInt(), 0xff16b6ff.toInt(), 0xff8e65ff.toInt(), 0xffef48c1.toInt(), 0xffffa827.toInt())
    private val prefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var enabled = true
    private var bandMin = -1200
    private var bandMax = 1200
    private var count = 0
    private val values = mutableListOf<Int>()
    private lateinit var eqStrip: LinearLayout
    private lateinit var state: TextView
    private lateinit var power: Button
    private lateinit var presetBox: LinearLayout
    private lateinit var mainScroll: ScrollView
    private lateinit var presetScroll: HorizontalScrollView
    private var activePreset = "Personnalisé"

    private var bound = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val engine = (binder as AudioService.LocalBinder).service
            eq = engine.eq; bass = engine.bass; virtualizer = engine.virtualizer
            enabled = prefs.getBoolean("enabled", true)
            initEffects(); buildScreen(); applyAll(); power.requestFocus()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            eq = null; bass = null; virtualizer = null
            initEffects(); buildScreen()
        }
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).roundToInt()
    private fun text(s: String, size: Float, color: Int = white): TextView = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(5), dp(5), dp(5), dp(5))
    }
    private fun section(s: String) = text(s, 21f).apply { setTypeface(null, Typeface.BOLD) }
    private fun column(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun background(color: Int, radius: Int = 16): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun button(s: String, action: () -> Unit): Button = Button(this).apply {
        text = s; isAllCaps = false; textSize = 14f; setTextColor(white)
        background = background(0xff173352.toInt(), 11)
        isFocusable = true
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { action() }
        setOnFocusChangeListener { _, focused ->
            background = background(if (focused) 0xff176ac0.toInt() else 0xff173352.toInt(), 11)
            scaleX = if (focused) 1.04f else 1f
            scaleY = if (focused) 1.04f else 1f
        }
    }
    private fun addButton(parent: LinearLayout, title: String, action: () -> Unit): Button =
        button(title, action).also {
            parent.addView(it, LinearLayout.LayoutParams(-2, dp(46)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        enabled = prefs.getBoolean("enabled", true)
        buildScreen()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("notification_asked", false)) {
            prefs.edit().putBoolean("notification_asked", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val service = Intent(this, AudioService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            bound = bindService(service, connection, Context.BIND_AUTO_CREATE)
        } catch (e: RuntimeException) {
            state.text = "Service audio indisponible : ${e.javaClass.simpleName}"
        }
    }

    private fun initEffects() {
        count = runCatching { eq?.numberOfBands?.toInt() ?: 0 }.getOrDefault(0)
        runCatching { eq?.bandLevelRange?.let { bandMin = it[0].toInt(); bandMax = it[1].toInt() } }
        values.clear()
        for (i in 0 until count) values.add(prefs.getInt("band_$i", 0).coerceIn(bandMin, bandMax))
    }

    private fun buildScreen() {
        val outer = column().apply { setBackgroundColor(bg); setPadding(dp(18), dp(12), dp(18), dp(12)) }
        setContentView(outer)
        val header = row()
        outer.addView(header)
        val brand = column()
        brand.addView(text("◉  OctoEQ TV", 26f, cyan).apply { setTypeface(null, Typeface.BOLD) })
        brand.addView(text("POWER YOUR SOUND", 11f, muted))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        power = addButton(header, if (enabled) "●  EQ ACTIVÉ" else "○  EQ DÉSACTIVÉ") {
            enabled = !enabled
            prefs.edit().putBoolean("enabled", enabled).apply()
            applyEnabled()
            power.text = if (enabled) "●  EQ ACTIVÉ" else "○  EQ DÉSACTIVÉ"
            power.contentDescription = power.text
        }
        power.id = View.generateViewId()
        power.contentDescription = power.text
        state = text("", 12f, muted).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        outer.addView(state)
        state.text = if (count == 0) "Égaliseur matériel indisponible : commandes EQ désactivées." else
            "$count bandes Android • contrôle : ${if (runCatching { eq?.hasControl() == true }.getOrDefault(false)) "acquis" else "indisponible"} • global expérimental, efficacité externe non vérifiée"
        mainScroll = ScrollView(this).apply { isFillViewport = true }
        outer.addView(mainScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val body = column()
        mainScroll.addView(body)
        val spectrum = SpectrumView(this)
        body.addView(spectrum, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(12) })
        val eqPanel = column().apply { background = background(panel); setPadding(dp(14), dp(10), dp(14), dp(10)) }
        body.addView(eqPanel, LinearLayout.LayoutParams(-1, -2))
        val eqHeader = row()
        eqPanel.addView(eqHeader)
        eqHeader.addView(section("Égaliseur graphique"), LinearLayout.LayoutParams(0, -2, 1f))
        eqHeader.addView(text("$count bandes disponibles", 13f, muted))
        eqPanel.addView(text("OK : régler / valider  •  En navigation : ↑ alimentation, ↓ profils  •  Courbe décorative", 12f, muted))
        val horizontal = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = true; isFillViewport = true }
        eqPanel.addView(horizontal, LinearLayout.LayoutParams(-1, dp(224)))
        eqStrip = row()
        horizontal.addView(eqStrip, android.widget.FrameLayout.LayoutParams(-1, -1))
        renderBands()
        body.addView(text("PRÉRÉGLAGES", 15f, muted))
        presetScroll = HorizontalScrollView(this)
        body.addView(presetScroll)
        presetBox = row()
        presetScroll.addView(presetBox)
        val presets = listOf("Plat", "Basses +", "Aigus +", "Vocal", "Pop", "Rock", "Jazz", "Cinéma", "Concert")
        presets.forEach { name -> addButton(presetBox, name) { applyPreset(name) }.id = View.generateViewId() }
        // Explicit focus exits: UP from EQ returns to power; DOWN enters presets.
        for (i in 0 until eqStrip.childCount) {
            val band = eqStrip.getChildAt(i)
            band.nextFocusUpId = power.id
            band.nextFocusDownId = presetBox.getChildAt(i.coerceAtMost(presetBox.childCount - 1)).id
        }
        body.addView(text("EFFETS SONORES", 15f, muted))
        val effects = row()
        body.addView(effects)
        effects.addView(effectPanel("BASSES", "Bass Boost", "bass", 0xff8d6bff.toInt(), runCatching { bass?.strengthSupported == true }.getOrDefault(false)) { v ->
            runCatching { bass?.setStrength(v.toShort()) }
        }, LinearLayout.LayoutParams(0, dp(135), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        effects.addView(effectPanel("SURROUND", "Spatialisation", "surround", 0xffef48c1.toInt(), runCatching { virtualizer?.strengthSupported == true }.getOrDefault(false)) { v ->
            runCatching { virtualizer?.setStrength(v.toShort()) }
        }, LinearLayout.LayoutParams(0, dp(135), 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        val footer = row()
        outer.addView(footer)
        addButton(footer, "↺  Réinitialiser") { applyPreset("Plat"); prefs.edit().putInt("bass", 0).putInt("surround", 0).apply(); buildScreen(); applyAll() }
        addButton(footer, "■  Arrêter le service") { prefs.edit().putBoolean("enabled", false).apply(); stopService(Intent(this, AudioService::class.java)); finish() }
        footer.addView(text("Sauvegarde automatique • Retour : écran fermé, service maintenu", 12f, muted))
    }

    private fun renderBands() {
        eqStrip.removeAllViews()
        if (count == 0) {
            eqStrip.addView(text("Aucune bande disponible sur cet appareil", 16f))
            return
        }
        for (i in 0 until count) {
            val freq = runCatching { eq?.getCenterFreq(i.toShort())?.div(1000) ?: 0 }.getOrDefault(0)
            val caption = if (freq >= 1000) String.format(Locale.US, "%.1fk", freq / 1000f) else "$freq"
            val band = i
            val view = BandSlider(this, caption, values[i], bandMin, bandMax, palette[i * palette.size / count]) { new ->
                values[band] = new
                prefs.edit().putInt("band_$band", new).apply()
                activePreset = "Personnalisé"
                prefs.edit().putString("preset", activePreset).apply()
                if (enabled) runCatching { eq?.setBandLevel(band.toShort(), new.toShort()) }
            }
            view.id = View.generateViewId()
            view.nextFocusUpId = power.id
            if (::presetBox.isInitialized && presetBox.childCount > 0)
                view.nextFocusDownId = presetBox.getChildAt(0).id
            eqStrip.addView(view, LinearLayout.LayoutParams(0, dp(214), 1f))
        }
    }

    private fun applyPreset(name: String) {
        activePreset = name
        prefs.edit().putString("preset", name).apply()
        val gains = when (name) {
            "Basses +" -> intArrayOf(700, 650, 500, 300, 100, 0, 0, 0, 0, 0)
            "Aigus +" -> intArrayOf(0, 0, 0, 0, 0, 0, 100, 350, 550, 650)
            "Vocal" -> intArrayOf(-200, -100, 0, 100, 250, 450, 450, 250, 0, -100)
            "Pop" -> intArrayOf(350, 250, 0, -100, 0, 200, 250, 300, 250, 200)
            "Rock" -> intArrayOf(450, 300, 100, -100, -200, 100, 300, 400, 450, 350)
            "Jazz" -> intArrayOf(250, 150, 0, 100, 200, 150, 200, 300, 250, 200)
            "Cinéma" -> intArrayOf(500, 450, 250, 0, 0, 100, 200, 300, 350, 300)
            "Concert" -> intArrayOf(300, 250, 150, 50, 0, 0, 100, 250, 350, 350)
            else -> IntArray(10)
        }
        for (i in 0 until count) {
            val frequency = runCatching { eq?.getCenterFreq(i.toShort())?.div(1000f) ?: 1000f }.getOrDefault(1000f)
            val value = EqMath.gainAt(frequency, gains).coerceIn(bandMin, bandMax)
            values[i] = value
            prefs.edit().putInt("band_$i", value).apply()
        }
        applyAll()
        renderBands()
        Toast.makeText(this, "Profil : $name", Toast.LENGTH_SHORT).show()
    }

    private fun effectPanel(title: String, subtitle: String, key: String, color: Int, available: Boolean, apply: (Int) -> Unit): LinearLayout {
        val p = column().apply { background = background(panel); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        p.addView(text(title, 17f, color).apply { setTypeface(null, Typeface.BOLD) })
        p.addView(text(if (available) subtitle else "Non pris en charge", 12f, muted))
        val controls = row()
        p.addView(controls)
        val display = text("", 18f)
        var value = prefs.getInt(key, 0).coerceIn(0, 1000)
        fun update() { display.text = "${value / 10} %" }
        update()
        addButton(controls, "−") {
            value = (value - 100).coerceAtLeast(0)
            prefs.edit().putInt(key, value).apply()
            if (enabled) apply(value)
            update()
        }.isEnabled = available
        controls.addView(display, LinearLayout.LayoutParams(0, -2, 1f))
        addButton(controls, "+") {
            value = (value + 100).coerceAtMost(1000)
            prefs.edit().putInt(key, value).apply()
            if (enabled) apply(value)
            update()
        }.isEnabled = available
        return p
    }

    private fun applyEnabled() {
        runCatching { eq?.enabled = enabled }
        runCatching { bass?.enabled = enabled && prefs.getInt("bass", 0) > 0 && bass?.strengthSupported == true }
        runCatching { virtualizer?.enabled = enabled && prefs.getInt("surround", 0) > 0 && virtualizer?.strengthSupported == true }
    }
    private fun applyAll() {
        for (i in 0 until count) runCatching { eq?.setBandLevel(i.toShort(), values[i].toShort()) }
        runCatching { bass?.setStrength(prefs.getInt("bass", 0).coerceIn(0, 1000).toShort()) }
        runCatching { virtualizer?.setStrength(prefs.getInt("surround", 0).coerceIn(0, 1000).toShort()) }
        applyEnabled()
    }
    override fun onDestroy() {
        if (bound) { unbindService(connection); bound = false }
        super.onDestroy()
    }

    private inner class SpectrumView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(panel)
            val bars = 62
            for (i in 0 until bars) {
                val x = width * (i + 0.5f) / bars
                val fraction = (0.15 + 0.65 * kotlin.math.abs(kotlin.math.sin(i * 0.23) * kotlin.math.cos(i * 0.13))).toFloat()
                val h = height * fraction
                paint.color = palette[i * palette.size / bars]
                paint.strokeWidth = (width.toFloat() / bars * 0.56f).coerceAtLeast(2f)
                paint.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(x, height * 0.86f, x, height * 0.86f - h * 0.74f, paint)
            }
        }
    }

    private inner class BandSlider(context: Context, private val caption: String, private var level: Int,
        private val min: Int, private val max: Int, private val color: Int, private val changed: (Int) -> Unit) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val navigation = BandNavigation()
        private val editing get() = navigation.editing
        private var consumeBackUp = false
        init {
            isFocusable = true
            isClickable = true
            contentDescription = "Fréquence $caption. OK pour régler."
        }
        private fun setEditing(value: Boolean) {
            if (value != navigation.editing) navigation.confirm()
            contentDescription = "Fréquence $caption. " +
                if (editing) "Réglage actif, haut et bas pour modifier, OK pour valider."
                else "Navigation, haut pour EQ activé, bas pour préréglages, OK pour régler."
            invalidate()
        }
        override fun performClick(): Boolean {
            super.performClick()
            setEditing(!editing)
            return true
        }
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    return true // Toggle on key UP only: long presses must not toggle twice.
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (editing) { consumeBackUp = true; setEditing(false); return true }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (!editing) {
                        setEditing(false)
                        power.requestFocus()
                        return true
                    }
                    level = EqMath.step(level, 100, min, max)
                    changed(level); invalidate(); return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (!editing) {
                        setEditing(false)
                        presetBox.getChildAt(0)?.requestFocus()
                        return true
                    }
                    level = EqMath.step(level, -100, min, max)
                    changed(level); invalidate(); return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Horizontal navigation always remains available, including in edit mode.
                    setEditing(false)
                }
            }
            return super.onKeyDown(keyCode, event)
        }
        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            if (keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                if (!event.isCanceled) performClick()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK && consumeBackUp) { consumeBackUp = false; return true }
            return super.onKeyUp(keyCode, event)
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                requestFocus()
                val top = dp(50).toFloat()
                val bottom = height - dp(52).toFloat()
                val fraction = ((bottom - event.y) / (bottom - top)).coerceIn(0f, 1f)
                level = (min + fraction * (max - min)).roundToInt().coerceIn(min, max)
                changed(level); invalidate(); return true
            }
            return super.onTouchEvent(event)
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val mid = width / 2f
            val top = dp(50).toFloat()
            val bottom = height - dp(52).toFloat()
            val frac = if (max == min) 0.5f else (level - min).toFloat() / (max - min)
            val y = bottom - frac * (bottom - top)
            p.color = if (editing) 0xff185c49.toInt() else if (isFocused) 0xff1b5284.toInt() else panel
            canvas.drawRoundRect(2f, 3f, width - 2f, height - 3f, dp(9).toFloat(), dp(9).toFloat(), p)
            p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.textAlign = Paint.Align.CENTER
            p.textSize = dp(13).toFloat()
            p.color = white
            canvas.drawText(String.format(Locale.US, "%+.0f", level / 100f), mid, dp(27).toFloat(), p)
            if (editing) { p.color = 0xff37ed6f.toInt(); p.textSize = dp(10).toFloat(); canvas.drawText("RÉGLAGE", mid, dp(43).toFloat(), p) }
            p.color = 0xff34435a.toInt(); p.strokeWidth = dp(9).toFloat(); p.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(mid, top, mid, bottom, p)
            p.color = color
            canvas.drawLine(mid, y, mid, bottom, p)
            p.color = white
            canvas.drawCircle(mid, y, dp(if (isFocused) 12 else 9).toFloat(), p)
            p.color = if (isFocused) cyan else muted
            p.textSize = dp(12).toFloat()
            canvas.drawText(caption, mid, height - dp(19).toFloat(), p)
        }
        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            if (!gainFocus) setEditing(false)
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect); invalidate()
        }
    }
}
