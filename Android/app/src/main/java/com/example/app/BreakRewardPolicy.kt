package com.eyeguardian.app

/**
 * Pure reward rules for a manual visual break. Keeping this outside the UI and
 * receiver makes the anti-abuse behavior easy to verify with local tests.
 */
internal object BreakRewardPolicy {
    const val BLOCK_MILLIS = 20L * 60L * 1_000L
    const val XP_PER_BLOCK = 20

    /**
     * Returns the total number of 20-minute blocks that should be credited.
     * Stopping without continuing only confirms the first completed block;
     * pressing Continue confirms any additional full blocks elapsed.
     */
    fun creditedBlocks(
        elapsedMillis: Long,
        alreadyCreditedBlocks: Int,
        continuing: Boolean
    ): Int {
        val safeExisting = alreadyCreditedBlocks.coerceAtLeast(0)
        if (elapsedMillis < BLOCK_MILLIS) return safeExisting
        val completedBlocks = (elapsedMillis / BLOCK_MILLIS).toInt().coerceAtLeast(1)
        val eligibleBlocks = if (continuing) completedBlocks else 1
        return maxOf(safeExisting, eligibleBlocks)
    }

    fun xpForNewBlocks(previouslyCreditedBlocks: Int, creditedBlocks: Int): Int {
        return (creditedBlocks - previouslyCreditedBlocks).coerceAtLeast(0) * XP_PER_BLOCK
    }
}
