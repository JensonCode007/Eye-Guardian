package com.eyeguardian.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Keeps the existing light design intact while giving the same screens a
 * calm, glass-inspired dark palette. UI code can continue to describe its
 * light color and this adapter provides the corresponding dark surface/text.
 */
val LocalEyeGuardDark = compositionLocalOf { false }

@Composable
fun glassBorder(): BorderStroke {
    return BorderStroke(
        width = 1.dp,
        color = if (LocalEyeGuardDark.current) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color(0xFF7F9A8B).copy(alpha = 0.22f)
        }
    )
}

@Composable
fun appColor(lightValue: Long): Color {
    val light = Color(lightValue)
    if (!LocalEyeGuardDark.current) return light

    return when (lightValue) {
        0xFFFFFFFF -> Color(0xCC22302E)
        0xFFF1F6F3 -> Color(0xFF0E1714)
        0xFFF4F0FF -> Color(0xFF171522)
        0xFFE9F5FF -> Color(0xFF111E27)
        0xFFFFF1F5 -> Color(0xFF241820)
        0xFF1F6045 -> Color(0xC8335A4B)
        0xFFE1F2E8 -> Color(0xC825483C)
        0xFFFFF9ED -> Color(0xC82E271B)
        0xFFE9E4FF -> Color(0xFF25233A)
        0xFFE0F2FF -> Color(0xC8243A4A)
        0xFFE7ECE9 -> Color(0xFF27332E)
        0xFFE8F0FF -> Color(0xC81F3545)
        0xFFE4EFFF -> Color(0xFF1D2B3A)
        0xFFFFE4EC -> Color(0xFF3A1F2B)
        0xFFEAF4EE -> Color(0xFF18342A)
        0xFFEDE8FF -> Color(0xFF29213A)
        0xFFEAF1FF -> Color(0xFF172A38)
        0xFFEAF8F3 -> Color(0xC8214437)
        0xFFE4F2E9 -> Color(0xB82A4439)
        0xFFFFF0D8 -> Color(0xFF332713)
        0xFFFFEEF5 -> Color(0xFF351D2B)
        0xFFFFEEF7 -> Color(0xFF351D2F)
        0xFFFFE7B8 -> Color(0xFF3A2C16)
        0xFFFFF1EA -> Color(0xFF35251D)
        0xFF123B2A -> Color(0xFFE4F5E9)
        0xFF113A2A -> Color(0xFFE1F4E7)
        0xFF163B2A -> Color(0xFFE3F4E9)
        0xFF17372B -> Color(0xFFE2F1E8)
        0xFFD8F0E1 -> Color(0xFFE6FFF0)
        0xFF342B67 -> Color(0xFFE4DEFF)
        0xFF164A70 -> Color(0xFFD4ECFF)
        0xFF493719 -> Color(0xFFFFE2A8)
        0xFF654719 -> Color(0xFFFFD98A)
        0xFF5D4300 -> Color(0xFFFFD778)
        0xFF782B4B -> Color(0xFFFFB0CA)
        0xFF2E7D55 -> Color(0xFF6AD6A2)
        0xFF377D5B -> Color(0xFF72DFA8)
        0xFF087A58 -> Color(0xFF72E0B0)
        0xFF3178C6 -> Color(0xFF82BFFF)
        0xFF6654C7 -> Color(0xFFB4A8FF)
        0xFFE9769F -> Color(0xFFFF9CBD)
        0xFFB33E68 -> Color(0xFFFF8EB5)
        0xFF8A5200 -> Color(0xFFFFC86B)
        0xFF8A6500 -> Color(0xFFFFCE78)
        0xFF5F6D65 -> Color(0xFFAABCB2)
        0xFF64736B -> Color(0xFFA9BBB1)
        0xFF6B7971 -> Color(0xFFA7B9AE)
        0xFF6E604B -> Color(0xFFC4B598)
        0xFF718078 -> Color(0xFF95A99E)
        0xFF806D4B -> Color(0xFFC9B995)
        0xFF806D3A -> Color(0xFFD6BF8B)
        0xFF80546A -> Color(0xFFD4A9BB)
        0xFF8A7656 -> Color(0xFFC8B995)
        0xFF4A6C82 -> Color(0xFFA6C8DF)
        0xFF587066 -> Color(0xFFA7BDB2)
        0xFF62777F -> Color(0xFFA9C1C9)
        0xFF665E87 -> Color(0xFFBEB4DF)
        0xFFB26A00 -> Color(0xFFFFC76F)
        0xFF9B5B00 -> Color(0xFFFFB96A)
        0xFFC43D3D -> Color(0xFFFF9B9B)
        else -> darkFallback(light)
    }
}

private fun darkFallback(color: Color): Color {
    val luminance = (color.red * 0.2126f) + (color.green * 0.7152f) + (color.blue * 0.0722f)
    return when {
        luminance > 0.78f -> Color(
            red = 0.08f + color.red * 0.12f,
            green = 0.10f + color.green * 0.13f,
            blue = 0.13f + color.blue * 0.14f,
            alpha = color.alpha
        )
        luminance < 0.22f -> Color(0xFFE2F0E8)
        else -> Color(
            red = color.red + (1f - color.red) * 0.45f,
            green = color.green + (1f - color.green) * 0.45f,
            blue = color.blue + (1f - color.blue) * 0.45f,
            alpha = color.alpha
        )
    }
}
