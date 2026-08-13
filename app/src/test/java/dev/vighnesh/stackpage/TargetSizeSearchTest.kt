package dev.vighnesh.stackpage

import dev.vighnesh.stackpage.image.Attempt
import dev.vighnesh.stackpage.image.MAX_QUALITY
import dev.vighnesh.stackpage.image.MIN_QUALITY
import dev.vighnesh.stackpage.image.searchPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The search contract: the returned plan's bytes are a real measurement of
 * the returned attempt, a reachable target is always hit, and an unreachable
 * one reports the best achievable instead of looping.
 */
class TargetSizeSearchTest {

    /** Monotonic in both axes, roughly how a JPEG encoder behaves. */
    private fun monotonicCurve(attempt: Attempt): Long {
        val scale = attempt.scalePercent / 100.0
        return (4_000_000 * scale * scale * (0.05 + 0.95 * (attempt.quality / 100.0))).toLong()
    }

    @Test
    fun reachableTargetIsHitAtTheHighestFittingQuality() {
        val target = 2_000_000L
        val plan = searchPlan(4_000_000, target, ::monotonicCurve)

        assertTrue(plan.hitTarget)
        assertTrue(plan.expectedBytes <= target)
        assertEquals(monotonicCurve(plan.attempt), plan.expectedBytes)
        assertEquals(100, plan.attempt.scalePercent)
        // On a monotonic curve, quality one step higher must overshoot.
        val nextUp = Attempt(plan.attempt.quality + 1, 100)
        assertTrue(monotonicCurve(nextUp) > target)
    }

    @Test
    fun generousTargetKeepsMaximumQuality() {
        val plan = searchPlan(4_000_000, 100_000_000, ::monotonicCurve)
        assertTrue(plan.hitTarget)
        assertEquals(Attempt(MAX_QUALITY, 100), plan.attempt)
    }

    @Test
    fun tightTargetFallsBackToScaling() {
        val target = 100_000L
        val plan = searchPlan(4_000_000, target, ::monotonicCurve)

        assertTrue(plan.hitTarget)
        assertTrue(plan.expectedBytes <= target)
        assertEquals(MIN_QUALITY, plan.attempt.quality)
        assertTrue(plan.attempt.scalePercent < 100)
    }

    @Test
    fun nonMonotonicCurveStillReturnsARealMeasurementUnderTarget() {
        // A curve with a bump: quality 60-70 encodes *larger* than 71-80,
        // which a naive binary search can be fooled by.
        fun bumpy(attempt: Attempt): Long {
            val base = monotonicCurve(attempt)
            return if (attempt.quality in 60..70) base + 1_500_000 else base
        }

        val target = 2_500_000L
        val plan = searchPlan(4_000_000, target, ::bumpy)

        assertTrue(plan.hitTarget)
        // The promise that matters: the reported bytes are the probe's own
        // answer for the returned attempt, and they fit the target.
        assertEquals(bumpy(plan.attempt), plan.expectedBytes)
        assertTrue(plan.expectedBytes <= target)
    }

    @Test
    fun unreachableTargetReportsBestAchievableWithoutLooping() {
        val plan = searchPlan(4_000_000, 1_000, ::monotonicCurve)

        assertFalse(plan.hitTarget)
        // Best achievable is the smallest encode the search saw: the floor
        // quality at the floor scale step.
        assertEquals(Attempt(MIN_QUALITY, 10), plan.attempt)
        assertEquals(monotonicCurve(plan.attempt), plan.expectedBytes)
        assertTrue(plan.probeCount <= 20, "probeCount ${plan.probeCount} should stay bounded")
    }

    @Test
    fun probeBudgetStaysSmallOnTheHappyPath() {
        val plan = searchPlan(4_000_000, 2_000_000, ::monotonicCurve)
        assertTrue(plan.probeCount <= 7, "quality-only search took ${plan.probeCount} probes")
    }

    @Test
    fun invalidInputsThrow() {
        assertFailsWith<IllegalArgumentException> { searchPlan(0, 1, ::monotonicCurve) }
        assertFailsWith<IllegalArgumentException> { searchPlan(1, 0, ::monotonicCurve) }
        assertFailsWith<IllegalArgumentException> { Attempt(0, 100) }
        assertFailsWith<IllegalArgumentException> { Attempt(50, 0) }
    }
}
