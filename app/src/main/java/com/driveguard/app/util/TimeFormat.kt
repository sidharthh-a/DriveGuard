package com.driveguard.app.util

import java.util.concurrent.TimeUnit

/** Duration/time formatting helpers (no locale-dependent surprises in tests). */
object TimeFormat {

    /** 3_725_000 → "05:32" (mm:ss) or "01:02:05" (h:mm:ss) for >= 1 h. */
    fun duration(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** "3 min" / "1 h 12 min" compact human string. */
    fun durationHuman(ms: Long): String {
        val totalMin = TimeUnit.MILLISECONDS.toMinutes(ms)
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "%dh %dmin".format(h, m)
            h > 0 -> "%dh".format(h)
            m > 0 -> "%dmin".format(m)
            else -> "<1min"
        }
    }
}
