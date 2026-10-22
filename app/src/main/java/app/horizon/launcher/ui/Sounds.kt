package app.horizon.launcher.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import app.horizon.launcher.R

/** Soft UI clicks (own generated sounds). Toggle in the Control Center. */
object Sounds {
    private var pool: SoundPool? = null
    private var tickId = 0
    private var tockId = 0
    private var lastTick = 0L

    @Volatile
    var enabled = true

    fun init(context: Context) {
        if (pool != null) return
        pool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
            .also {
                tickId = it.load(context, R.raw.focus_tick, 1)
                tockId = it.load(context, R.raw.select_tock, 1)
            }
    }

    /** Focus moved. Only plays right after a D-pad press, never for programmatic focus. */
    fun tick() {
        if (!enabled) return
        val now = SystemClock.uptimeMillis()
        if (now - Motion.lastMoveAt > 250 || now - lastTick < 40) return
        lastTick = now
        pool?.play(tickId, 0.45f, 0.45f, 1, 0, 1f)
    }

    fun tock() {
        if (enabled) pool?.play(tockId, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun release() {
        pool?.release()
        pool = null
    }
}
