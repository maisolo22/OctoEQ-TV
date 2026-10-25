package com.maisolo.octoeqtv

import android.app.*
import android.content.*
import android.media.audiofx.*
import android.os.*

/** Foreground owner of effects; listens only to sessions explicitly announced by players. */
@Suppress("DEPRECATION")
class AudioService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    inner class LocalBinder : Binder() { val service get() = this@AudioService }
    private val prefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val binder = LocalBinder()
    private val owners = linkedMapOf<Int, MutableSet<String>>()
    private val engines = linkedMapOf<Int, SessionEffects>()
    private var registered = false
    var bypass = false; private set
    internal val attachedSessions get() = engines.keys.toSet()
    internal val announcedSessions get() = owners.keys.toSet()
    internal val primary get() = engines.values.lastOrNull()
    // Compatibility accessors for existing integrations and diagnostics.
    val eq: Equalizer? get() = primary?.eq
    val bass: BassBoost? get() = primary?.bass
    val virtualizer: Virtualizer? get() = primary?.virtualizer
    internal val diagnostic: String get() {
        val mode = prefs.getString("mode", "auto")
        val active = if (engines.containsKey(0)) "Sortie globale expérimentale" else
            if (engines.isEmpty()) "Aucune session annoncée : relancez la lecture dans l’autre application" else
                owners.filterKeys { engines.containsKey(it) }.values.flatten().distinct().joinToString().take(180)
        val detail = primary?.let { "${it.engineName} · contrôle ${if (it.controlled) "acquis" else "absent"}" } ?: "Aucun moteur attaché"
        val error = engines.values.mapNotNull { it.lastError }.distinct().joinToString()
        return "$active\n$detail\n" + (if (bypass) "Comparaison : original" else "Son externe à vérifier à l’écoute · mode $mode") +
            if (error.isBlank()) "" else "\n$error"
    }
    private val update = Runnable { reconcile(); migrateLegacyGains(); applySettings() }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME).orEmpty()
            if (id <= 0 || pkg.isBlank() || pkg.length > 255) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> announce(id, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    owners[id]?.remove(pkg)
                    if (owners[id]?.isEmpty() == true) owners.remove(id)
                    handler.post(update)
                }
            }
        }
    }
    internal fun announce(id: Int, pkg: String) {
        if (id <= 0 || pkg.isBlank() || pkg.length > 255) return
        if (!owners.containsKey(id) && owners.size >= 8) owners.remove(owners.keys.first())
        val packages = owners.getOrPut(id) { linkedSetOf() }
        if (packages.size < 8) packages.add(pkg)
        handler.removeCallbacks(update); handler.post(update)
    }
    // API 33+ explicitly exports player announcements; pre-33 has no export flag contract.
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("audio", "Traitement audio", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AudioService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "audio") else Notification.Builder(this)
        startForeground(1, builder.setSmallIcon(R.drawable.ic_octoeq).setContentTitle("OctoEQ TV · Studio audio")
            .setContentText("Sessions compatibles et sortie globale expérimentale")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Arrêter", stop).build()).build())
        val filter = IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
        registered = true
        prefs.registerOnSharedPreferenceChangeListener(this)
        reconcile(); migrateLegacyGains(); applySettings()
    }
    private fun migrateLegacyGains() {
        if (prefs.getBoolean("canonical_gains", false)) return
        val oldEq = eq
        if (oldEq == null && prefs.all.keys.any { it.startsWith("band_") }) return
        val editor = prefs.edit()
        for (i in StudioTone.frequencies.indices) {
            val oldBand = runCatching { oldEq?.getBand((StudioTone.frequencies[i] * 1000).toInt())?.toInt() }.getOrNull()
            editor.putInt("gain_$i", if (oldBand != null) prefs.getInt("band_$oldBand", 0) else 0)
        }
        editor.putBoolean("canonical_gains", true).apply()
    }
    private fun reconcile() {
        val mode = prefs.getString("mode", "auto")
        val desired = when (mode) {
            "global" -> setOf(0)
            "sessions" -> owners.keys.toSet()
            else -> owners.keys.toSet().ifEmpty { setOf(0) }
        }
        // Never combine global and session effects: avoid processing the same audio twice.
        for (id in engines.keys.toList()) if (id !in desired) engines.remove(id)?.release()
        for (id in desired) if (id !in engines) {
            val engine = SessionEffects(id, prefs)
            engines[id] = engine
            engine.effects().forEach { effect -> effect.setControlStatusListener { _, granted ->
                if (granted) { handler.removeCallbacks(update); handler.postDelayed(update, 50) }
            } }
        }
    }
    internal fun toggleBypass() { bypass = !bypass; applySettings() }
    internal fun endComparison() { bypass = false; applySettings() }
    internal fun retry() {
        engines.values.forEach { it.release() }; engines.clear()
        reconcile(); applySettings()
    }
    private fun applySettings() { engines.values.forEach { it.apply(bypass) } }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            prefs.edit().putBoolean("enabled", false).apply()
            endComparison(); stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent) = binder
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        handler.removeCallbacks(update); handler.post(update)
    }
    override fun onDestroy() {
        if (registered) unregisterReceiver(receiver)
        prefs.unregisterOnSharedPreferenceChangeListener(this); handler.removeCallbacksAndMessages(null)
        engines.values.forEach { it.release() }; engines.clear(); owners.clear()
        super.onDestroy()
    }
}
