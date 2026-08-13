package dev.vighnesh.stackpage.image

/**
 * Finding the (quality, scale) pair that lands an image under a byte target.
 *
 * Pure Kotlin on purpose, mirroring PageLayout: the search logic is where the
 * bugs live, so it takes an injected [probe] and knows nothing about bitmaps.
 * In production the probe is a real Bitmap.compress into a counting stream; in
 * tests it is a fake curve, including deliberately non-monotonic ones, because
 * real JPEG encoders are only *mostly* monotonic in quality.
 */

/** One candidate encode: JPEG quality and a uniform scale percentage. */
data class Attempt(val quality: Int, val scalePercent: Int) {
    init {
        require(quality in 1..100) { "quality $quality out of range" }
        require(scalePercent in 1..100) { "scalePercent $scalePercent out of range" }
    }
}

/**
 * The chosen encode. [expectedBytes] is the probed size of exactly this
 * attempt, never an estimate carried over from a different attempt.
 * [hitTarget] is false when even the smallest probed encode missed, in which
 * case the plan is the best achievable rather than a promise.
 */
data class Plan(
    val attempt: Attempt,
    val expectedBytes: Long,
    val hitTarget: Boolean,
    val probeCount: Int,
)

const val MIN_QUALITY = 40
const val MAX_QUALITY = 95

/**
 * Searches quality [MIN_QUALITY]..[MAX_QUALITY] at full scale first, then
 * steps scale down 90..10 in tens at [MIN_QUALITY] if quality alone cannot
 * reach the target. Converges in at most ~15 probes.
 *
 * Among attempts that fit the target the largest one wins, because closest
 * under the target is least destroyed. If nothing fits, the smallest probed
 * attempt is returned with [Plan.hitTarget] false.
 */
fun searchPlan(originalBytes: Long, targetBytes: Long, probe: (Attempt) -> Long): Plan {
    require(originalBytes > 0) { "originalBytes must be positive" }
    require(targetBytes > 0) { "targetBytes must be positive" }

    val probed = LinkedHashMap<Attempt, Long>()
    fun measure(attempt: Attempt): Long = probed.getOrPut(attempt) { probe(attempt) }

    // Phase 1: binary search over quality at full scale for the highest
    // quality that fits. Non-monotonic curves cannot break correctness here
    // because the winner is chosen from actual measurements at the end.
    var low = MIN_QUALITY
    var high = MAX_QUALITY
    while (low <= high) {
        val mid = (low + high) / 2
        val size = measure(Attempt(mid, 100))
        if (size <= targetBytes) low = mid + 1 else high = mid - 1
    }

    // Phase 2: quality has a floor; if the floor still misses, shrink.
    if (probed.none { it.value <= targetBytes }) {
        measure(Attempt(MIN_QUALITY, 100))
        for (scale in 90 downTo 10 step 10) {
            if (measure(Attempt(MIN_QUALITY, scale)) <= targetBytes) break
        }
    }

    val fitting = probed.filter { it.value <= targetBytes }
    return if (fitting.isNotEmpty()) {
        val best = fitting.maxBy { it.value }
        Plan(best.key, best.value, hitTarget = true, probeCount = probed.size)
    } else {
        val best = probed.minBy { it.value }
        Plan(best.key, best.value, hitTarget = false, probeCount = probed.size)
    }
}
