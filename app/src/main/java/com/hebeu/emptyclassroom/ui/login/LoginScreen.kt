package com.hebeu.emptyclassroom.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hebeu.emptyclassroom.ui.common.components.InlineErrorCard
import com.hebeu.emptyclassroom.ui.motion.rememberMotionSpec
import com.hebeu.emptyclassroom.ui.theme.AppSpacing

/**
 * 登录页（ui-spec §3.1）：
 * 图标 + 校名 + 副标题 → 表单卡片（学号/密码）→ 错误提示 → 登录按钮 → 隐私说明 + 数据来源。
 *
 * 完全由 [LoginUiState] 驱动（无内部业务状态），便于 Preview / Roborazzi / Compose UI 测试
 * 传入任意状态渲染；登录成功后的页面切换由 AppRoot 观察 session 完成。
 *
 * @param noticeText 外部提示（例如会话失效后回到登录页：「登录已过期，请重新登录」）。
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisibility: () -> Unit,
    onSubmit: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    noticeText: String? = null,
    onAutoLoginChange: (Boolean) -> Unit = {},
    onOpenPrivacy: () -> Unit = onOpenAbout,
) {
    val motion = rememberMotionSpec()
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSubmit()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(AppSpacing.xxl))
            Column(
                modifier = Modifier
                    .widthIn(max = AppSpacing.loginFormMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ---- 品牌区 ----
                com.hebeu.emptyclassroom.ui.common.components.AppBrandIcon()
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    text = "河北工程大学",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "HUE教务助手",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(AppSpacing.xl))

                // ---- 表单卡片 ----
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    ) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = onUsernameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("学号") },
                            placeholder = { Text("请输入教务系统学号") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Person, contentDescription = null)
                            },
                            isError = state.usernameError != null,
                            supportingText = state.usernameError?.let { error -> { Text(error) } },
                            singleLine = true,
                            enabled = !state.isSubmitting,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next,
                            ),
                            shape = MaterialTheme.shapes.medium,
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = onPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("密码") },
                            placeholder = { Text("请输入教务系统密码") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Outlined.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = onTogglePasswordVisibility,
                                    enabled = !state.isSubmitting,
                                ) {
                                    Icon(
                                        imageVector = if (state.isPasswordVisible) {
                                            Icons.Outlined.VisibilityOff
                                        } else {
                                            Icons.Outlined.Visibility
                                        },
                                        contentDescription = if (state.isPasswordVisible) "隐藏密码" else "显示密码",
                                    )
                                }
                            },
                            visualTransformation = if (state.isPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            isError = state.passwordError != null,
                            supportingText = state.passwordError?.let { error -> { Text(error) } },
                            singleLine = true,
                            enabled = !state.isSubmitting,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            shape = MaterialTheme.shapes.medium,
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = state.autoLogin,
                        onCheckedChange = onAutoLoginChange, enabled = !state.isSubmitting)
                    Text("自动登录", style = MaterialTheme.typography.bodyMedium)
                }
                // ---- 会话失效提示（回到登录页后仍知道为什么被退出） ----
                AnimatedVisibility(
                    visible = noticeText != null,
                    enter = fadeIn(motion.tween(motion.standardMs)) +
                        expandVertically(motion.tween(motion.standardMs)),
                    exit = fadeOut(motion.tween(motion.shortMs)) +
                        shrinkVertically(motion.tween(motion.shortMs)),
                ) {
                    Column {
                        Spacer(Modifier.height(AppSpacing.md))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = noticeText.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(
                                    horizontal = AppSpacing.md,
                                    vertical = AppSpacing.sm,
                                ),
                            )
                        }
                    }
                }

                // ---- 错误提示 ----
                AnimatedVisibility(
                    visible = state.errorText != null,
                    enter = fadeIn(motion.tween(motion.standardMs)) +
                        expandVertically(motion.tween(motion.standardMs)),
                    exit = fadeOut(motion.tween(motion.shortMs)) +
                        shrinkVertically(motion.tween(motion.shortMs)),
                ) {
                    Column {
                        Spacer(Modifier.height(AppSpacing.md))
                        InlineErrorCard(message = state.errorText.orEmpty())
                    }
                }

                Spacer(Modifier.height(AppSpacing.lg))

                // ---- 登录按钮 ----
                Button(
                    onClick = submit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppSpacing.primaryButtonHeight),
                    enabled = !state.isSubmitting,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Crossfade(
                        targetState = state.isSubmitting,
                        animationSpec = motion.tween(motion.shortMs),
                        label = "loginButton",
                    ) { submitting ->
                        if (submitting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(AppSpacing.xs))
                                Text(text = "登录中…", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Text(text = "登录", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(AppSpacing.md))

                // ---- 隐私与数据来源 ----
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = onOpenPrivacy) { Text("隐私") }
                    TextButton(onClick = onOpenAbout) { Text("关于本应用") }
                }
            }
            Spacer(Modifier.height(AppSpacing.xxl))
        }
    }
}
