package com.maisolo.octoeqtv

import android.app.*
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.*
import android.os.*

/** Owns native effects independently of the Activity. Never captures audio. */
@Suppress("DEPRECATION")
class AudioService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    inner class LocalBinder : Binder() { val service get() = this@AudioService }
    var eq: Equalizer? = null; private set
    var bass: BassBoost? = null; private set
    var virtualizer: Virtualizer? = null; private set
    private val prefs by lazy { getSharedPreferences("studio", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private val update = Runnable { applySettings() }
    private val binder = LocalBinder()
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel("audio", "Traitement audio", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, AudioService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "audio") else Notification.Builder(this)
        val notification = builder.setSmallIcon(R.drawable.ic_octoeq).setContentTitle("OctoEQ TV • moteur audio")
            .setContentText("Effets globaux expérimentaux • ouvrir pour régler")
            .setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null, "Arrêter", stop).build()).build()
        startForeground(1, notification)
        eq = runCatching { Equalizer(0, 0) }.getOrNull()
        bass = runCatching { BassBoost(0, 0) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, 0) }.getOrNull()
        // Reapply when an OEM gives control back to this client.
        listOfNotNull(eq, bass, virtualizer).forEach { effect ->
            effect.setControlStatusListener { _, granted -> if (granted) handler.post(update) }
        }
        prefs.registerOnSharedPreferenceChangeListener(this)
        applySettings()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            prefs.edit().putBoolean("enabled", false).apply()
            applySettings(); stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent) = binder
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        handler.removeCallbacks(update); handler.post(update)
    }
    private fun applySettings() {
        val enabled = prefs.getBoolean("enabled", true)
        eq?.let { effect -> runCatching {
            val range = effect.bandLevelRange
            for (i in 0 until effect.numberOfBands.toInt()) effect.setBandLevel(i.toShort(),
                prefs.getInt("band_$i", 0).coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            effect.enabled = enabled
        } }
        bass?.let { effect -> runCatching {
            val strength = prefs.getInt("bass", 0).coerceIn(0, 1000)
            if (effect.strengthSupported) effect.setStrength(strength.toShort())
            effect.enabled = enabled && strength > 0 && effect.strengthSupported
        } }
        virtualizer?.let { effect -> runCatching {
            val strength = prefs.getInt("surround", 0).coerceIn(0, 1000)
            if (effect.strengthSupported) effect.setStrength(strength.toShort())
            effect.enabled = enabled && strength > 0 && effect.strengthSupported
        } }
    }
    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(this); handler.removeCallbacksAndMessages(null)
        listOfNotNull(eq, bass, virtualizer).forEach { runCatching { it.release() } }
        eq = null; bass = null; virtualizer = null
        super.onDestroy()
    }
}
