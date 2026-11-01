package com.maisolo.octoeqtv

import android.app.*
import android.content.*
import android.media.audiofx.*
import android.os.*
import androidx.core.content.ContextCompat

/** Persistent user-started engine. Global and player-session routes are mutually exclusive. */
@Suppress("DEPRECATION")
class AudioService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    inner class LocalBinder : Binder() { val service get() = this@AudioService }
    private data class Effects(val eq: EqEngine, val bass: BassBoost?, val surround: Virtualizer?)
    private val engines = linkedMapOf<Int, Effects>()
    private val sessions = SessionRegistry()
    var eq: EqEngine? = null; private set
    val bass get() = engines.values.firstOrNull()?.bass
    val virtualizer get() = engines.values.firstOrNull()?.surround
    var observer: (() -> Unit)? = null
    private val prefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val update = Runnable { applySettings() }
    private val binder = LocalBinder()
    private var error: String? = null
    private var receiverRegistered = false
    private var stoppedByUser = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (stoppedByUser || !prefs.getBoolean("player_sessions", false)) return
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val owner = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: return
            if (!AudioPolicy.validSession(id, owner) || owner.length > 255) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> if (sessions.open(id, owner)) {
                    if (!createEngine(id)) sessions.forget(id)
                    updatePrimary(); applySettings(); observer?.invoke()
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> if (sessions.close(id, owner)) {
                    release(engines.remove(id)); updatePrimary(); observer?.invoke()
                }
            }
        }
    }
    fun status(): String {
        val route = if (prefs.getBoolean("player_sessions", false)) "Sessions compatibles : ${engines.size}" else "Global expérimental"
        val engine = eq
        val control = engine?.let { runCatching { if (it.hasControl()) "contrôle acquis" else "contrôle indisponible" }.getOrDefault("contrôle perdu") }
        return listOfNotNull(route, engine?.label, control, error,
            if (engine == null && prefs.getBoolean("player_sessions", false)) "Relancer la lecture dans un lecteur compatible" else null).joinToString(" • ")
    }
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("audio", "Traitement audio", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AudioService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "audio") else Notification.Builder(this)
        startForeground(1, builder.setSmallIcon(R.drawable.ic_octoeq).setContentTitle("OctoEQ TV • moteur audio")
            .setContentText("Compatibilité selon le lecteur et la sortie PCM • ouvrir pour régler")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Arrêter", stop).build()).build())
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
        prefs.registerOnSharedPreferenceChangeListener(this)
        rebuild()
    }
    private fun createEngine(id: Int): Boolean {
        val faithful = prefs.getBoolean("faithful", true)
        val (engine, reason) = EqEngine.create(id, faithful)
        error = reason
        if (engine == null) return false
        // Faithful mode does not instantiate legacy BassBoost/Virtualizer, even after EQ fallback.
        val bass = if (!faithful) runCatching { BassBoost(0, id) }.getOrNull() else null
        val surround = if (!faithful) runCatching { Virtualizer(0, id) }.getOrNull() else null
        engines[id] = Effects(engine, bass, surround)
        listOfNotNull(engine.nativeEffect, bass, surround).forEach { effect ->
            effect.setControlStatusListener { _, granted ->
                handler.post {
                    if (granted) { engine.invalidate(); applySettings() }
                    observer?.invoke()
                }
            }
        }
        return true
    }
    private fun updatePrimary() { eq = engines.values.firstOrNull()?.eq }
    private fun release(effects: Effects?) {
        effects ?: return
        runCatching { effects.eq.nativeEffect.setControlStatusListener(null) }
        listOfNotNull(effects.eq.nativeEffect, effects.bass, effects.surround).forEach { runCatching { it.release() } }
    }
    private fun rebuild() {
        engines.values.forEach { release(it) }; engines.clear(); eq = null; error = null
        // Recreate only known live sessions; never layer session processing over global EQ.
        if (prefs.getBoolean("player_sessions", false)) sessions.ids().forEach { createEngine(it) }
        else { sessions.clear(); createEngine(0) }
        updatePrimary(); applySettings(); observer?.invoke()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stoppedByUser = true
            prefs.edit().putBoolean("enabled", false).apply()
            applySettings(); stopSelf(); return START_NOT_STICKY
        }
        stoppedByUser = false
        applySettings()
        return START_STICKY
    }
    override fun onBind(intent: Intent) = binder
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == "faithful" || key == "player_sessions") { rebuild(); return }
        handler.removeCallbacks(update); handler.postDelayed(update, 16)
        if (key == "enabled") observer?.invoke()
    }
    private fun applySettings() {
        val enabled = prefs.getBoolean("enabled", true) && !stoppedByUser
        val reference = eq ?: return
        val requested = IntArray(reference.frequencies.size) { prefs.getInt(reference.gainKeys[it], 0) }
        for (effects in engines.values) {
            val engine = effects.eq
            try {
                val range = engine.bandLevelRange
                val levels = AudioPolicy.mapCurve(reference.frequencies, requested, engine.frequencies, range[0].toInt(), range[1].toInt())
                engine.apply(levels, enabled)
                effects.bass?.let {
                    val strength = prefs.getInt("bass", 0).coerceIn(0, 1000)
                    if (it.strengthSupported) it.setStrength(strength.toShort())
                    val wanted = enabled && strength > 0 && it.strengthSupported
                    if (it.enabled != wanted) check(it.setEnabled(wanted) == AudioEffect.SUCCESS)
                }
                effects.surround?.let {
                    val strength = prefs.getInt("surround", 0).coerceIn(0, 1000)
                    if (it.strengthSupported) it.setStrength(strength.toShort())
                    val wanted = enabled && strength > 0 && it.strengthSupported
                    if (it.enabled != wanted) check(it.setEnabled(wanted) == AudioEffect.SUCCESS)
                }
            } catch (e: RuntimeException) {
                val message = "Session ${engine.session} : réglage refusé (${e.javaClass.simpleName})"
                if (error != message) { error = message; observer?.invoke() }
            }
        }
    }
    override fun onDestroy() {
        observer = null
        prefs.unregisterOnSharedPreferenceChangeListener(this); handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(receiver)
        engines.values.forEach { release(it) }; engines.clear(); sessions.clear(); eq = null
        super.onDestroy()
    }
}
