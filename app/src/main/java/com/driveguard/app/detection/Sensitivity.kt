package com.driveguard.app.detection

/**
 * User-facing sensitivity presets. They adjust the confidence threshold and
 * the minimum continuous closure duration — never the polarity, the temporal
 * rules, or the face-tracking grace windows themselves.
 *
 * Blink-safety invariant: even the most responsive preset (1.5 s) keeps a 3×
 * margin over the longest voluntary blink (~0.5 s), so normal blinking can
 * never fire an alert at any sensitivity.
 */
enum class Sensitivity(
    val confidenceThreshold: Float,
    val closedDurationMs: Long,
    val description: String,
) {
    /**
     * Fewer false alarms: higher P threshold, longer closure required.
     * The classic 3-second rule — suited to bright daylight driving where
     * squinting is common.
     */
    LOW(0.80f, 3_000L, "Fewer alerts — 3 s of closed eyes required"),

    /**
     * The default: 2 s of continuous closure. Halves alert latency versus the
     * 3 s rule while retaining a ≥4× blink margin (a voluntary blink is
     * 100–400 ms). Closures of ~1–2 s are treated as microsleeps in the
     * drowsiness literature (PERCLOS lineage); 2 s is the conservative end.
     */
    MEDIUM(0.70f, DetectionTuning.DROWSY_START_THRESHOLD_MS, "Balanced — 2 s of closed eyes triggers an alert"),

    /**
     * Most responsive: 1.5 s closure. Useful for short city hops; may produce
     * more alerts with sunglasses or in low light. Still 3× the longest blink.
     */
    HIGH(0.60f, 1_500L, "Most responsive — 1.5 s of closed eyes triggers an alert"),
    ;

    fun toConfig(base: DrowsinessConfig = DrowsinessConfig()): DrowsinessConfig =
        base.copy(confidenceThreshold = confidenceThreshold, closedDurationMs = closedDurationMs)

    companion object {
        fun fromName(name: String?): Sensitivity =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}
