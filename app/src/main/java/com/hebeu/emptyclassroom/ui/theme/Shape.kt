package com.hebeu.emptyclassroom.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角规范（ui-spec §2.3）：
 *  - 卡片 large = 20dp
 *  - 输入框 medium = 12dp
 *  - Chip small = 8dp
 *  - 底部面板 extraLarge = 28dp
 */
internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
