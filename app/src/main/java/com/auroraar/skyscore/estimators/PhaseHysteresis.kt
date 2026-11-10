package com.auroraar.skyscore.estimators

import com.auroraar.skyscore.model.Phase

/**
 * Addendum §5. A class only wins after leading by [lead] for a sustained time,
 * with a longer hold around day transitions where auto-exposure swings.
 */
class PhaseHysteresis(
    initial: Phase = Phase.DAYLIGHT,
    private val lead: Float = 0.15f,
    private val normalHoldMs: Long = 3_000L,
    private val slowHoldMs: Long = 5_000L,
) {
    var current: Phase = initial
        private set

    private var candidate: Phase? = null
    private var candidateSinceMs: Long = 0L

    fun update(prob: List<Float>, nowMs: Long): Phase {
        val topIndex = prob.indices.maxByOrNull { prob[it] } ?: return current
        val top = Phase.fromIndex(topIndex)
        val currentProb = prob.getOrElse(current.ordinal) { 0f }
        val topProb = prob.getOrElse(topIndex) { 0f }

        if (top == current || topProb - currentProb < lead) {
            candidate = null
            return current
        }
        if (candidate != top) {
            candidate = top
            candidateSinceMs = nowMs
            return current
        }
        val hold = if (top == Phase.DAYLIGHT || current == Phase.DAYLIGHT) slowHoldMs else normalHoldMs
        if (nowMs - candidateSinceMs >= hold) {
            current = top
            candidate = null
        }
        return current
    }

    fun reset(phase: Phase) {
        current = phase
        candidate = null
        candidateSinceMs = 0L
    }
}
