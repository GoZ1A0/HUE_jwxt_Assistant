package com.hebeu.emptyclassroom.ui.motion

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

/**
 * 统一动效时长与「尊重系统动画缩放」的实现（ui-spec §4）。
 *
 * 时长：短 150ms / 标准 220ms / 长 320ms，缓动 FastOutSlowInEasing。
 *
 * 系统动画关闭（Settings.Global.ANIMATOR_DURATION_SCALE == 0，或 ValueAnimator 被禁用）时：
 * - [MotionSpec.enabled] == false；
 * - 所有 tween 时长为 0、spring 用瞬时收敛参数、列表 animateItem 传 null，
 *   即「直接切换」而不是把页面卡住；
 * - 加载指示器（ProgressIndicator）本身不是补间动画，仍会显示。
 *
 * 组合方式：[com.hebeu.emptyclassroom.ui.theme.EmptyClassroomTheme] 读取系统设置后
 * 通过 [LocalMotionScaleEnabled] 下发；业务 Composable 用 [rememberMotionSpec] 取当前值。
 */
data class MotionSpec(
    val enabled: Boolean,
    val shortMs: Int = SHORT_MS,
    val standardMs: Int = STANDARD_MS,
    val longMs: Int = LONG_MS,
) {
    /** 按系统动画开关折算后的实际时长（关闭时 0ms）。 */
    fun durationMs(baseMs: Int): Int = if (enabled) baseMs else 0

    fun <T> tween(baseMs: Int, delayMs: Int = 0): TweenSpec<T> = tween(
        durationMillis = durationMs(baseMs),
        delayMillis = durationMs(delayMs),
        easing = FastOutSlowInEasing,
    )

    fun <T> springSmooth(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 结果列表 item 的位移动画（关闭动画时为 null，Compose 约定 null = 不做该动画）。 */
    fun placementSpec(): FiniteAnimationSpec<IntOffset>? =
        if (enabled) spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) else null

    /** 结果列表 item 的淡入淡出（关闭动画时为 null）。 */
    fun fadeSpec(): FiniteAnimationSpec<Float>? =
        if (enabled) tween(standardMs) else null

    companion object {
        const val SHORT_MS = 150
        const val STANDARD_MS = 220
        const val LONG_MS = 320

        val Enabled = MotionSpec(enabled = true)
        val Disabled = MotionSpec(enabled = false)

        /**
         * 读取系统「动画时长缩放」。优先读 Settings.Global（ui-spec 要求），
         * 读取失败时退回 ValueAnimator.areAnimatorsEnabled()，再失败默认开启动画。
         * 读取发生在 ON_RESUME（见 Theme），因此用户改完设置返回 App 会立刻生效。
         */
        fun isSystemAnimationEnabled(context: Context): Boolean {
            val durationScale = runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrNull()
            if (durationScale != null) return durationScale > 0f
            return runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        }
    }
}

/** 主题下发：系统动画是否启用。CompositionLocal 保证测试/Preview 可强制关闭动画。 */
val LocalMotionScaleEnabled = staticCompositionLocalOf { true }

/** 业务 Composable 的统一入口。 */
@Composable
fun rememberMotionSpec(): MotionSpec {
    val enabled = LocalMotionScaleEnabled.current
    return remember(enabled) { if (enabled) MotionSpec.Enabled else MotionSpec.Disabled }
}
