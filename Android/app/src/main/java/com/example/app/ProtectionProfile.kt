package com.eyeguardian.app

/**
 * Profiles make the distance assistant fit the user's context. The face-size
 * ratio is a relative signal, not a centimetre measurement; users can choose
 * how sensitive and how persistent reminders should feel.
 */
internal enum class ProtectionMode(
    val label: String,
    val description: String,
    val tooCloseRatio: Float,
    val gettingCloseRatio: Float,
    val warningDelayMillis: Long,
    val warningCooldownMillis: Long
) {
    READING(
        label = "Reading",
        description = "Balanced distance guidance for everyday reading.",
        tooCloseRatio = 0.40f,
        gettingCloseRatio = 0.28f,
        warningDelayMillis = 5_000L,
        warningCooldownMillis = 90_000L
    ),
    STUDY(
        label = "Study",
        description = "More attentive reminders for focused study sessions.",
        tooCloseRatio = 0.38f,
        gettingCloseRatio = 0.27f,
        warningDelayMillis = 6_000L,
        warningCooldownMillis = 120_000L
    ),
    WORK(
        label = "Work",
        description = "A calmer profile for long work and engineering sessions.",
        tooCloseRatio = 0.44f,
        gettingCloseRatio = 0.31f,
        warningDelayMillis = 12_000L,
        warningCooldownMillis = 240_000L
    ),
    GAMING(
        label = "Gaming",
        description = "Least interruptive reminders while still allowing recovery time.",
        tooCloseRatio = 0.46f,
        gettingCloseRatio = 0.33f,
        warningDelayMillis = 20_000L,
        warningCooldownMillis = 300_000L
    );

    companion object {
        fun fromId(id: String?): ProtectionMode = entries.firstOrNull { it.name == id } ?: READING
    }
}

internal data class DistanceProfile(
    val id: String,
    val label: String,
    val description: String,
    val tooCloseRatio: Float,
    val gettingCloseRatio: Float,
    val warningDelayMillis: Long,
    val warningCooldownMillis: Long,
    val targetDistanceCm: Int? = null,
    val isCustom: Boolean = false
)

internal data class CustomDistanceProfile(
    val id: String,
    val name: String,
    val targetDistanceCm: Int,
    val calibratedFaceWidthRatio: Float?,
    val warningDelayMillis: Long,
    val warningCooldownMillis: Long
) {
    fun asDistanceProfile(): DistanceProfile {
        // A camera frame gives us a relative face-size signal. Calibration is
        // preferred; the fallback is only a starting estimate based on the
        // built-in Reading profile and must be presented as approximate.
        val ratio = calibratedFaceWidthRatio
            ?: (ProtectionMode.READING.tooCloseRatio * 45f / targetDistanceCm)
                .coerceIn(0.20f, 0.60f)
        return DistanceProfile(
            id = "custom:$id",
            label = name,
                description = "Custom mode calibrated from your chosen viewing position",
            tooCloseRatio = ratio,
            gettingCloseRatio = (ratio * 0.70f).coerceAtLeast(0.12f),
            warningDelayMillis = warningDelayMillis,
            warningCooldownMillis = warningCooldownMillis,
            targetDistanceCm = targetDistanceCm,
            isCustom = true
        )
    }
}

internal fun ProtectionMode.asDistanceProfile(): DistanceProfile = DistanceProfile(
    id = "mode:$name",
    label = label,
    description = description,
    tooCloseRatio = tooCloseRatio,
    gettingCloseRatio = gettingCloseRatio,
    warningDelayMillis = warningDelayMillis,
    warningCooldownMillis = warningCooldownMillis
)

internal enum class AmbientLightLevel(val label: String) {
    DIM("Dim scene"),
    COMFORTABLE("Comfortable scene light"),
    BRIGHT("Bright scene"),
    UNKNOWN("Checking scene light")
}

internal fun ambientLightLevelFromLuma(luma: Float): AmbientLightLevel = when {
    luma < 32f -> AmbientLightLevel.DIM
    luma > 225f -> AmbientLightLevel.BRIGHT
    else -> AmbientLightLevel.COMFORTABLE
}

internal fun shouldIssueDistanceWarning(
    status: DistanceStatus,
    nowMillis: Long,
    tooCloseSinceMillis: Long,
    lastWarningMillis: Long,
    warningSentForBlock: Boolean,
    profile: DistanceProfile
): Boolean {
    if (status != DistanceStatus.TOO_CLOSE || tooCloseSinceMillis <= 0L) {
        return false
    }
    // warningSentForBlock is retained for compatibility with stored/service
    // state. The cooldown deliberately allows a calm repeat while the user
    // remains too close; it prevents per-frame notification spam.
    val heldTooCloseLongEnough = nowMillis - tooCloseSinceMillis >= profile.warningDelayMillis
    val cooldownComplete = lastWarningMillis <= 0L ||
        nowMillis - lastWarningMillis >= profile.warningCooldownMillis
    return heldTooCloseLongEnough && cooldownComplete
}
