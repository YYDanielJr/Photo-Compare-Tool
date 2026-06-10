package com.example.photocomparetool

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.photocomparetool.ui.theme.PhotoCompareToolTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoCompareToolTheme {
                HomeScreen()
            }
        }
    }
}

// 比对模式枚举
enum class CompareMode(val icon: ImageVector, val label: String) {
    SINGLE(Icons.Filled.Photo, "单张"),
    TWO(Icons.Filled.ViewAgenda, "两张"),
    FOUR(Icons.Filled.GridView, "四张")
}

/**
 * 照片的缩放/平移状态，每张照片独立拥有
 */
class PhotoTransformState(
    initialScale: Float = 1f,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f
) {
    var scale by mutableFloatStateOf(initialScale)
    var offsetX by mutableFloatStateOf(initialOffsetX)
    var offsetY by mutableFloatStateOf(initialOffsetY)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var selectedMode by remember { mutableStateOf(CompareMode.SINGLE) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }

    // 存储各位置选中的图片 Uri
    var selectedUris by remember {
        mutableStateOf(List<Uri?>(4) { null })
    }

    // 每张照片的独立变换状态（最多4个）
    val photoStates = remember { mutableStateListOf<PhotoTransformState>() }
    // 初始化4个状态对象（按需使用）
    if (photoStates.isEmpty()) {
        repeat(4) { photoStates.add(PhotoTransformState()) }
    }

    // 记录当前点击的是第几个容器（0~3）
    var currentPickerIndex by remember { mutableIntStateOf(0) }

    // 图库选择器
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedUris = selectedUris.toMutableList().also { it[currentPickerIndex] = uri }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // 锁定/解锁按钮
                    IconButton(onClick = { isLocked = !isLocked }) {
                        Icon(
                            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (isLocked) "解锁" else "锁定"
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 比对模式下拉菜单
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = selectedMode.icon,
                                contentDescription = "切换比对模式"
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            CompareMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        selectedMode = mode
                                        menuExpanded = false
                                        // 重置所有状态和图片
                                        selectedUris = List(4) { null }
                                        photoStates.forEach { state ->
                                            state.scale = 1f
                                            state.offsetX = 0f
                                            state.offsetY = 0f
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(imageVector = mode.icon, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (mode == selectedMode) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "已选中",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // 设置按钮
                    IconButton(onClick = { /* 打开设置 */ }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        CompareContent(
            mode = selectedMode,
            selectedUris = selectedUris,
            photoStates = photoStates,
            isLocked = isLocked,
            onCardClick = { index ->
                currentPickerIndex = index
                imagePicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun CompareContent(
    mode: CompareMode,
    selectedUris: List<Uri?>,
    photoStates: List<PhotoTransformState>,
    isLocked: Boolean,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 创建手势增量回调，根据锁定状态决定更新范围
    val createGestureHandler: (Int) -> (Float, Float, Float) -> Unit = { index ->
        { zoomDelta, panX, panY ->
            if (isLocked) {
                // 锁定模式：对所有照片状态应用相同增量
                photoStates.forEach { state ->
                    state.scale = (state.scale * zoomDelta).coerceIn(1f, 3f)
                    state.offsetX += panX
                    state.offsetY += panY
                }
            } else {
                // 解锁模式：仅更新当前照片
                val state = photoStates[index]
                state.scale = (state.scale * zoomDelta).coerceIn(1f, 3f)
                state.offsetX += panX
                state.offsetY += panY
            }
        }
    }

    when (mode) {
        CompareMode.SINGLE -> {
            Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
                ZoomablePhotoContainer(
                    uri = selectedUris[0],
                    onClick = { onCardClick(0) },
                    transformState = photoStates[0],
                    onGestureDelta = createGestureHandler(0),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        CompareMode.TWO -> {
            Column(
                modifier = modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoomablePhotoContainer(
                    uri = selectedUris[0],
                    onClick = { onCardClick(0) },
                    transformState = photoStates[0],
                    onGestureDelta = createGestureHandler(0),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                ZoomablePhotoContainer(
                    uri = selectedUris[1],
                    onClick = { onCardClick(1) },
                    transformState = photoStates[1],
                    onGestureDelta = createGestureHandler(1),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }

        CompareMode.FOUR -> {
            Column(
                modifier = modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomablePhotoContainer(
                        uri = selectedUris[0],
                        onClick = { onCardClick(0) },
                        transformState = photoStates[0],
                        onGestureDelta = createGestureHandler(0),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ZoomablePhotoContainer(
                        uri = selectedUris[1],
                        onClick = { onCardClick(1) },
                        transformState = photoStates[1],
                        onGestureDelta = createGestureHandler(1),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomablePhotoContainer(
                        uri = selectedUris[2],
                        onClick = { onCardClick(2) },
                        transformState = photoStates[2],
                        onGestureDelta = createGestureHandler(2),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ZoomablePhotoContainer(
                        uri = selectedUris[3],
                        onClick = { onCardClick(3) },
                        transformState = photoStates[3],
                        onGestureDelta = createGestureHandler(3),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * 可双指缩放/平移的照片容器（状态由外部传入，完全无内部状态）
 */
@Composable
fun ZoomablePhotoContainer(
    uri: Uri?,
    onClick: () -> Unit,
    transformState: PhotoTransformState,
    onGestureDelta: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // 核心修复：使用 rememberUpdatedState 保证在 pointerInput 内部始终调用最新的 lambda
    val currentOnGestureDelta by rememberUpdatedState(onGestureDelta)
    val currentOnClick by rememberUpdatedState(onClick)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (uri == null) Color.Gray.copy(alpha = 0.15f) else Color.Transparent
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                // 使用最新的点击回调
                .clickable { currentOnClick() }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // 无论外部 isLocked 怎么变，这里始终会执行携带最新状态的回调
                        currentOnGestureDelta(zoom, pan.x, pan.y)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transformState.scale
                        scaleY = transformState.scale
                        translationX = transformState.offsetX
                        translationY = transformState.offsetY
                    },
                contentAlignment = Alignment.Center
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = "选中的照片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("点击选择", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}