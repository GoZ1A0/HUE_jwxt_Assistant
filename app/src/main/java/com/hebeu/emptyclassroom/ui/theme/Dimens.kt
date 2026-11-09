package com.hebeu.emptyclassroom.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 间距标尺（ui-spec §2.3）：4 / 8 / 12 / 16 / 20 / 24 / 32 dp。
 * 统一从这里取，避免各处魔法数字漂移。
 */
object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** 页面水平 padding */
    val screenHorizontal = md

    /** 区块间距 */
    val section = md

    /** 卡片内 padding */
    val cardInner = md

    /** 列表项之间的间距 */
    val listGap = sm

    /** 主操作按钮高度（ui-spec §2.4） */
    val primaryButtonHeight = 52.dp

    /** 内容最大宽度：宽屏时居中，避免行长过长 */
    val contentMaxWidth = 720.dp

    /** 登录表单最大宽度 */
    val loginFormMaxWidth = 480.dp

    /** 最小触控目标 */
    val minTouchTarget = 48.dp
}
