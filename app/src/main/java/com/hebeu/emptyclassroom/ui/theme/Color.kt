package com.hebeu.emptyclassroom.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌色：使用 ui-spec 约定的种子色。
 *  - Primary  : 教务蓝 #0B57D0
 *  - Secondary: 偏青  #00696E
 *  - Tertiary : 用于「空闲/成功」语义（浅绿）
 *
 * 未使用动态取色（Android 12 以下或用户关闭）时使用这里的方案；
 * 完整 Material 3 tonal 值，避免依赖基线调色板造成色彩不统一。
 */

// ---- 品牌种子色（供预览/截图说明用，也是本文件色板的来源） ----
val BrandSeedBlue = Color(0xFF0B57D0)
val BrandSeedTeal = Color(0xFF00696E)

// ================= 浅色 =================
private val LightPrimary = Color(0xFF0B57D0)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFD8E2FF)
private val LightOnPrimaryContainer = Color(0xFF001A41)

private val LightSecondary = Color(0xFF00696E)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFF9CF1F6)
private val LightOnSecondaryContainer = Color(0xFF002022)

private val LightTertiary = Color(0xFF3B6939)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFBCF0B4)
private val LightOnTertiaryContainer = Color(0xFF002204)

private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)

private val LightBackground = Color(0xFFF8F9FF)
private val LightOnBackground = Color(0xFF191C20)
private val LightSurface = Color(0xFFF8F9FF)
private val LightOnSurface = Color(0xFF191C20)
private val LightSurfaceVariant = Color(0xFFE1E2EC)
private val LightOnSurfaceVariant = Color(0xFF44474F)
private val LightOutline = Color(0xFF74777F)
private val LightOutlineVariant = Color(0xFFC4C6D0)

private val LightSurfaceDim = Color(0xFFD8DAE0)
private val LightSurfaceBright = Color(0xFFF8F9FF)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF2F3FA)
private val LightSurfaceContainer = Color(0xFFECEEF5)
private val LightSurfaceContainerHigh = Color(0xFFE6E8EF)
private val LightSurfaceContainerHighest = Color(0xFFE0E2E9)
private val LightInverseSurface = Color(0xFF2E3036)
private val LightInverseOnSurface = Color(0xFFF0F0F7)
private val LightInversePrimary = Color(0xFFADC6FF)

// ================= 深色 =================
private val DarkPrimary = Color(0xFFADC6FF)
private val DarkOnPrimary = Color(0xFF002E69)
private val DarkPrimaryContainer = Color(0xFF004494)
private val DarkOnPrimaryContainer = Color(0xFFD8E2FF)

private val DarkSecondary = Color(0xFF80D4DA)
private val DarkOnSecondary = Color(0xFF00363A)
private val DarkSecondaryContainer = Color(0xFF004F54)
private val DarkOnSecondaryContainer = Color(0xFF9CF1F6)

private val DarkTertiary = Color(0xFFA0D49B)
private val DarkOnTertiary = Color(0xFF00390B)
private val DarkTertiaryContainer = Color(0xFF235024)
private val DarkOnTertiaryContainer = Color(0xFFBCF0B4)

private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)

private val DarkBackground = Color(0xFF111318)
private val DarkOnBackground = Color(0xFFE2E2E9)
private val DarkSurface = Color(0xFF111318)
private val DarkOnSurface = Color(0xFFE2E2E9)
private val DarkSurfaceVariant = Color(0xFF44474F)
private val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
private val DarkOutline = Color(0xFF8E9099)
private val DarkOutlineVariant = Color(0xFF44474F)

private val DarkSurfaceDim = Color(0xFF111318)
private val DarkSurfaceBright = Color(0xFF37393E)
private val DarkSurfaceContainerLowest = Color(0xFF0C0E13)
private val DarkSurfaceContainerLow = Color(0xFF191C20)
private val DarkSurfaceContainer = Color(0xFF1D2024)
private val DarkSurfaceContainerHigh = Color(0xFF282A2F)
private val DarkSurfaceContainerHighest = Color(0xFF32353A)
private val DarkInverseSurface = Color(0xFFE2E2E9)
private val DarkInverseOnSurface = Color(0xFF2E3036)
private val DarkInversePrimary = Color(0xFF0B57D0)

internal val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    scrim = Color(0xFF000000),
)

internal val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = Color(0xFF000000),
)
