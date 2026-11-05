package com.eyeguardian.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionProfileTest {
    @Test
    fun modesHaveDifferentSensitivityAndReminderRules() {
        assertTrue(ProtectionMode.STUDY.tooCloseRatio < ProtectionMode.WORK.tooCloseRatio)
        assertTrue(ProtectionMode.GAMING.warningDelayMillis > ProtectionMode.READING.warningDelayMillis)
        assertTrue(ProtectionMode.GAMING.warningCooldownMillis > ProtectionMode.STUDY.warningCooldownMillis)
    }

    @Test
    fun ambientLightBucketsUseOnDeviceLumaOnly() {
        assertEquals(AmbientLightLevel.DIM, ambientLightLevelFromLuma(10f))
        assertEquals(AmbientLightLevel.COMFORTABLE, ambientLightLevelFromLuma(120f))
        assertEquals(AmbientLightLevel.BRIGHT, ambientLightLevelFromLuma(240f))
    }

    @Test
    fun distanceWarningGatePreventsImmediateRepeatedAlerts() {
        val profile = ProtectionMode.READING.asDistanceProfile()
        assertTrue(
            shouldIssueDistanceWarning(
                DistanceStatus.TOO_CLOSE,
                nowMillis = 6_000L,
                tooCloseSinceMillis = 0L,
                lastWarningMillis = 0L,
                warningSentForBlock = false,
                profile = profile
            ).not()
        )
        assertTrue(
            shouldIssueDistanceWarning(
                DistanceStatus.TOO_CLOSE,
                nowMillis = 6_000L,
                tooCloseSinceMillis = 1_000L,
                lastWarningMillis = 0L,
                warningSentForBlock = false,
                profile = profile
            )
        )
        assertFalse(
            shouldIssueDistanceWarning(
                DistanceStatus.TOO_CLOSE,
                nowMillis = 7_000L,
                tooCloseSinceMillis = 1_000L,
                lastWarningMillis = 6_000L,
                warningSentForBlock = false,
                profile = profile
            )
        )
    }

    @Test
    fun distanceWarningCooldownAllowsCalmRepeatAfterTheInterval() {
        val profile = ProtectionMode.READING.asDistanceProfile()
        assertTrue(
            shouldIssueDistanceWarning(
                DistanceStatus.TOO_CLOSE,
                nowMillis = 100_000L,
                tooCloseSinceMillis = 1_000L,
                lastWarningMillis = 1_000L,
                warningSentForBlock = true,
                profile = profile
            )
        )
    }
}
