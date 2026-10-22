package com.eyeguardian.app

import org.junit.Assert.assertEquals
import org.junit.Test

class BreakRewardPolicyTest {
    @Test
    fun stoppingBeforeTwentyMinutesDoesNotCreditXp() {
        val credited = BreakRewardPolicy.creditedBlocks(
            elapsedMillis = BreakRewardPolicy.BLOCK_MILLIS - 1,
            alreadyCreditedBlocks = 0,
            continuing = false
        )
        assertEquals(0, credited)
    }

    @Test
    fun stoppingAfterFirstBlockCreditsExactlyOneBlock() {
        val credited = BreakRewardPolicy.creditedBlocks(
            elapsedMillis = BreakRewardPolicy.BLOCK_MILLIS * 2,
            alreadyCreditedBlocks = 0,
            continuing = false
        )
        assertEquals(1, credited)
        assertEquals(20, BreakRewardPolicy.xpForNewBlocks(0, credited))
    }

    @Test
    fun continuingAfterFortyMinutesCreditsBothBlocks() {
        val credited = BreakRewardPolicy.creditedBlocks(
            elapsedMillis = BreakRewardPolicy.BLOCK_MILLIS * 2,
            alreadyCreditedBlocks = 0,
            continuing = true
        )
        assertEquals(2, credited)
        assertEquals(40, BreakRewardPolicy.xpForNewBlocks(0, credited))
    }

    @Test
    fun repeatedNotificationDoesNotDuplicateCredits() {
        val credited = BreakRewardPolicy.creditedBlocks(
            elapsedMillis = BreakRewardPolicy.BLOCK_MILLIS * 2,
            alreadyCreditedBlocks = 2,
            continuing = true
        )
        assertEquals(2, credited)
        assertEquals(0, BreakRewardPolicy.xpForNewBlocks(2, credited))
    }
}
