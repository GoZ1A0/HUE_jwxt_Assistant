package com.hebeu.emptyclassroom.ui.query.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.hebeu.emptyclassroom.domain.model.BuildingOption
import com.hebeu.emptyclassroom.domain.model.CampusOption
import com.hebeu.emptyclassroom.ui.theme.AppSpacing

/** 通用只读下拉选择器（ExposedDropdownMenuBox），label + 当前值 + 菜单项。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownSelector(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "全部",
    leadingIcon: ImageVector? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val open = expanded && enabled
    ExposedDropdownMenuBox(
        expanded = open,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: placeholder,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = leadingIcon?.let { icon ->
                { Icon(imageVector = icon, contentDescription = null) }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = open,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** 校区选择（ui-spec 要求 SingleChoiceSegmentedButtonRow）；长名称截短避免大字体溢出。 */
@Composable
fun CampusSegmentedSelector(
    campuses: List<CampusOption>,
    selectedCampusId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (campuses.isEmpty()) return
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        campuses.forEachIndexed { index, campus ->
            val selected = campus.id == selectedCampusId
            SegmentedButton(
                selected = selected,
                onClick = { onSelect(campus.id) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = campuses.size),
            ) {
                Text(
                    text = campusShortName(campus.name),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 「丛台校区(临床医学院)」→「丛台」；「新校区」保持不变；空名兜底。 */
fun campusShortName(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return "未知校区"
    val beforeParenthesis = cleaned.substringBefore('(').substringBefore('（').trim()
    return beforeParenthesis.ifEmpty { cleaned }
}

/** 教学楼下拉：选项来自 options.buildingsByCampus / CampusDetail，已含 id="" 的「全部」项。 */
@Composable
fun BuildingDropdown(
    buildings: List<BuildingOption>,
    selectedBuildingId: String?,
    isLoading: Boolean,
    errorText: String?,
    onSelect: (BuildingOption) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        DropdownSelector(
            label = "教学楼",
            options = buildings,
            selected = buildings.firstOrNull { it.id == (selectedBuildingId ?: "") },
            optionLabel = { it.name.ifBlank { "全部" } },
            onSelect = onSelect,
            enabled = !isLoading && buildings.isNotEmpty(),
            placeholder = if (isLoading) "正在加载教学楼…" else "全部教学楼",
        )
        if (errorText != null) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) {
                    Text(text = "重试", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
