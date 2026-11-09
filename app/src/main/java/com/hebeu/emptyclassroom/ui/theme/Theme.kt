package com.hebeu.emptyclassroom.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hebeu.emptyclassroom.ui.motion.LocalMotionScaleEnabled
import com.hebeu.emptyclassroom.ui.motion.MotionSpec

/**
 * 应用主题。
 *
 * - 跟随系统浅色/深色（[isSystemInDarkTheme]）；
 * - Android 12+ 默认使用动态取色（[dynamicLightColorScheme]/[dynamicDarkColorScheme]），
 *   关闭动态取色或低版本时使用品牌色板（[LightColorScheme]/[DarkColorScheme]）；
 * - 统一圆角/字号层级（[AppShapes]/[AppTypography]）；
 * - 通过 [LocalMotionScaleEnabled] 向 UI 广播系统「动画时长缩放」状态：
 *   Settings.Global.ANIMATOR_DURATION_SCALE == 0（开发者选项关闭动画）时全局关闭动画，
 *   但加载指示仍保留（见 [com.hebeu.emptyclassroom.ui.motion.MotionSpec]）。
 *
 * QA 截图/Preview 可通过 `dynamicColor = false` + `darkTheme = true/false` 得到确定颜色。
 */
@Composable
fun EmptyClassroomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 读取系统动画缩放；在 ON_RESUME 时重新读取，用户在设置里改完返回能立即生效。
    var motionEnabled by remember(context) {
        mutableStateOf(MotionSpec.isSystemAnimationEnabled(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                motionEnabled = MotionSpec.isSystemAnimationEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalMotionScaleEnabled provides motionEnabled) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** 供 @Preview 使用的最小主题包装（固定浅色 + 品牌色，避免动态取色让截图不稳定）。 */
@Composable
internal fun PreviewTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    EmptyClassroomTheme(darkTheme = darkTheme, dynamicColor = false, content = content)
}
