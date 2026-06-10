package com.example.photocomparetool.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.photocomparetool.CompareMode
import com.example.photocomparetool.R
import com.example.photocomparetool.repositories.SettingsRepository
import com.example.photocomparetool.ui.theme.PhotoCompareToolTheme
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val settingsRepo = remember { SettingsRepository.getInstance(context) }
            val coroutineScope = rememberCoroutineScope()

            // 收集当前设置
            val currentMode by settingsRepo.defaultCompareMode.collectAsState(initial = "SINGLE")
            val isLocked by settingsRepo.isLocked.collectAsState(initial = false)
            val zoomLimit by settingsRepo.zoomLimit.collectAsState(initial = 5.0f)
            val showExif by settingsRepo.showExif.collectAsState(initial = true)
            val showZoomRatio by settingsRepo.showZoomRatio.collectAsState(initial = true)

            PhotoCompareToolTheme {
                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(stringResource(R.string.setting_title)) }
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier.padding(innerPadding),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        // 分组标题：常规
                        item {
                            Text(
                                "常规",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                            )
                        }

                        // 1. 默认比对模式
                        item {
                            SettingsDropdownItem(
                                title = "默认比对模式",
                                options = CompareMode.entries.map { it.label },
                                selectedOption = CompareMode.valueOf(currentMode).label,
                                onOptionSelected = { newLabel ->
                                    val mode = CompareMode.entries.find { it.label == newLabel }
                                        ?: CompareMode.SINGLE
                                    coroutineScope.launch {
                                        settingsRepo.setDefaultCompareMode(mode.name)
                                    }
                                }
                            )
                        }

                        // 2. 默认锁定状态
                        item {
                            SettingsSwitchItem(
                                title = "默认锁定缩放同步",
                                subtitle = "打开时，双指缩放会影响所有照片",
                                checked = isLocked,
                                onCheckedChange = { checked ->
                                    coroutineScope.launch {
                                        settingsRepo.setLocked(checked)
                                    }
                                }
                            )
                        }

                        // 分组标题：显示
                        item {
                            Text(
                                "显示",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                            )
                        }

                        // 3. 显示 EXIF 信息
                        item {
                            SettingsSwitchItem(
                                title = "显示 EXIF 信息",
                                subtitle = "在照片左上角显示拍摄参数",
                                checked = showExif,
                                onCheckedChange = { checked ->
                                    coroutineScope.launch {
                                        settingsRepo.setShowExif(checked)
                                    }
                                }
                            )
                        }

                        // 4. 显示缩放比
                        item {
                            SettingsSwitchItem(
                                title = "显示放大比率",
                                subtitle = "（目前不可用）在照片左下角显示相对于屏幕宽度的百分比",
                                checked = showZoomRatio,
                                onCheckedChange = { checked ->
                                    coroutineScope.launch {
                                        settingsRepo.setShowZoomRatio(checked)
                                    }
                                }
                            )
                        }

                        // 分组标题：缩放
                        item {
                            Text(
                                "缩放",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
                            )
                        }

                        // 5. 最大缩放倍数
                        item {
                            SettingsSliderItem(
                                title = "最大缩放倍数",
                                value = zoomLimit,
                                valueRange = 2.0f..20.0f,
                                steps = 17,                     // 2,3,4,5,6,7,8,9,10
                                onValueChange = { newLimit ->
                                    coroutineScope.launch {
                                        settingsRepo.setZoomLimit(newLimit)
                                    }
                                },
                                valueDisplay = { "${it.toInt()}x" }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- 可复用设置项组件（优化后）---------------------

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) } // 整行点击可切换
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Switch 保留，其自身的 onCheckedChange 也会触发，但由于整行已有点击，不再重复调用
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsDropdownItem(
    title: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true } // 点击整行展开菜单
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Box {
            TextButton(onClick = { expanded = true }) { // 仍然保留按钮，但点击整行也会展开
                Text(selectedOption)
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "展开",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSliderItem(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String = { "%.1f".format(it) }
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = valueDisplay(value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}