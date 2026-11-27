package com.driveguard.app.detection

/**
 * User-facing sensitivity presets. They adjust the confidence threshold and
 * the minimum continuous closure duration — never the polarity, the temporal
 * rules, or the face-tracking grace windows themselves.
 *
 * Blink-safety invariant: even the most responsive preset (1.5 s) keeps a 3×
 * margin over the longest voluntary blink (~0.5 s), so normal blinking can
 * never fire an alert at any sensitivity.
 *
 * Thresholds are expressed as P(closed) = 1 − mean(ML Kit eye-open probability),
 * so a preset's number is "closed once average openness falls below
 * 1 − threshold". MEDIUM (0.60 → openness < 0.40) is the rule that was tuned
 * on live frames in the reference prototype; HIGH and LOW move that cutoff by
 * ±0.10 to trade recall for fewer alerts.
 */
enum class Sensitivity(
    val confidenceThreshold: Float,
    val closedDurationMs: Long,
    val description: String,
) {
    /**
     * Fewer false alarms: closure must reach P = 0.70 (average openness below
     * 0.30) and last 3 s. Suited to bright daylight driving where squinting is
     * common.
     */
    LOW(0.70f, 3_000L, "Fewer alerts — 3 s of closed eyes required"),

    /**
     * The default: 2 s of continuous closure with the reference cutoff
     * (average openness < 0.40). Halves alert latency versus the 3 s rule
     * while retaining a ≥4× blink margin (a voluntary blink is 100–400 ms).
     * Closures of ~1–2 s are treated as microsleeps in the drowsiness
     * literature (PERCLOS lineage); 2 s is the conservative end.
     */
    MEDIUM(0.60f, DetectionTuning.DROWSY_START_THRESHOLD_MS, "Balanced — 2 s of closed eyes triggers an alert"),

    /**
     * Most responsive: closure at P = 0.50 (average openness below 0.50) for
     * 1.5 s. Useful for short city hops; may produce more alerts with
     * sunglasses or in low light. Still 3× the longest blink.
     */
    HIGH(0.50f, 1_500L, "Most responsive — 1.5 s of closed eyes triggers an alert"),
    ;

    fun toConfig(base: DrowsinessConfig = DrowsinessConfig()): DrowsinessConfig =
        base.copy(confidenceThreshold = confidenceThreshold, closedDurationMs = closedDurationMs)

    companion object {
        fun fromName(name: String?): Sensitivity =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}
