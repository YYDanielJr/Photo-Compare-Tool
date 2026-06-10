package com.example.photocomparetool

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.photocomparetool.activities.SettingsActivity
import com.example.photocomparetool.repositories.SettingsRepository
import com.example.photocomparetool.ui.theme.PhotoCompareToolTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    TWO(Icons.Filled.Splitscreen, "两张"),
    FOUR(Icons.Filled.GridView, "四张")
}

// 照片缩放/平移状态
class PhotoTransformState(
    initialScale: Float = 1f,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f
) {
    var scale by mutableFloatStateOf(initialScale)
    var offsetX by mutableFloatStateOf(initialOffsetX)
    var offsetY by mutableFloatStateOf(initialOffsetY)
}

// EXIF 显示信息
data class ExifDisplayInfo(
    val fileName: String = "未知",
    val resolution: String = "",
    val device: String = "未知",
    val aperture: String = "未知",
    val shutter: String = "未知",
    val iso: String = "未知",
    val focalLength: String = "未知",
    val focalLength35mm: String? = null
)

/**
 * 从 Uri 读取 EXIF 信息（在 IO 线程调用）
 */
suspend fun readExifInfo(context: Context, uri: Uri): ExifDisplayInfo = withContext(Dispatchers.IO) {
    try {
        // 统一使用 InputStream 构造 ExifInterface，兼容所有 Android 版本
        val inputStream = context.contentResolver.openInputStream(uri)
        val exif = inputStream?.use { ExifInterface(it) } ?: return@withContext ExifDisplayInfo()

        // 文件名
        val fileName: String = run {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else "未知"
                } else "未知"
            } ?: uri.lastPathSegment ?: "未知"
        }

        // 设备型号
        val make = exif.getAttribute(ExifInterface.TAG_MAKE) ?: ""
        val model = exif.getAttribute(ExifInterface.TAG_MODEL) ?: ""
        val device = if (make.isNotBlank() && model.isNotBlank()) "$make $model"
        else if (model.isNotBlank()) model
        else if (make.isNotBlank()) make
        else "未知"

        // 光圈
        val apertureValue = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, -1.0)
        val aperture = if (apertureValue > 0) "f/${String.format("%.1f", apertureValue)}" else "未知"

        // 快门
        val exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0)
        val shutter = if (exposureTime > 0) {
            if (exposureTime < 0.5) "1/${(1.0 / exposureTime).toInt()}s"
            else "${String.format("%.1f", exposureTime)}s"
        } else "未知"

        // ISO
        val iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
            ?: "未知"

        // 原始焦段
        val focalLengthValue = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0)
        val focalLength = if (focalLengthValue > 0) "${String.format("%.1f", focalLengthValue)}mm" else "未知"

        // 35mm 等效焦段（可能不存在）
        val focalLength35mmValue = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, -1)
        val focalLength35mm = if (focalLength35mmValue > 0) "${focalLength35mmValue}mm" else null

        val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, -1)
        val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, -1)
        val resolution = if (width > 0 && height > 0) "${width}x${height}" else ""

        ExifDisplayInfo(
            fileName, resolution, device, aperture, shutter, iso,
            focalLength, focalLength35mm
        )
    } catch (e: Exception) {
        e.printStackTrace()
        ExifDisplayInfo()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    // 从 DataStore 实时读取设置
    val defaultModeName by settingsRepo.defaultCompareMode.collectAsState(initial = "SINGLE")
    val isLocked by settingsRepo.isLocked.collectAsState(initial = false)
    val zoomLimit by settingsRepo.zoomLimit.collectAsState(initial = 5.0f)
    val showExif by settingsRepo.showExif.collectAsState(initial = true)
    val showZoomRatio by settingsRepo.showZoomRatio.collectAsState(initial = true)

    // 将读取到的模式转换为枚举
    val selectedMode = try {
        CompareMode.valueOf(defaultModeName)
    } catch (_: Exception) {
        CompareMode.SINGLE
    }

    var menuExpanded by remember { mutableStateOf(false) }

    // 照片 Uri 列表
    var selectedUris by remember { mutableStateOf(List<Uri?>(4) { null }) }
    val photoStates = remember { mutableStateListOf<PhotoTransformState>() }
    if (photoStates.isEmpty()) repeat(4) { photoStates.add(PhotoTransformState()) }

    var currentPickerIndex by remember { mutableIntStateOf(0) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            selectedUris = selectedUris.toMutableList().also { it[currentPickerIndex] = uri }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    // 锁定按钮：点击时更新 Compose 已收集的值（isLocked 自动刷新）
                    IconButton(onClick = {
                        coroutineScope.launch {
                            settingsRepo.setLocked(!isLocked)
                        }
                    }) {
                        Icon(
                            imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (isLocked) "解锁" else "锁定"
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(selectedMode.icon, contentDescription = "切换比对模式")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            CompareMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(mode.label) },
                                    onClick = {
                                        menuExpanded = false
                                        // 持久化选择的模式
                                        coroutineScope.launch {
                                            settingsRepo.setDefaultCompareMode(mode.name)
                                        }
                                        // 清空当前图片和缩放状态
                                        selectedUris = List(4) { null }
                                        photoStates.forEach { state ->
                                            state.scale = 1f
                                            state.offsetX = 0f
                                            state.offsetY = 0f
                                        }
                                    },
                                    leadingIcon = { Icon(mode.icon, null) },
                                    trailingIcon = {
                                        if (mode == selectedMode) {
                                            Icon(
                                                Icons.Filled.Check,
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
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
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
            zoomLimit = zoomLimit,         // 传递缩放上限
            showExif = showExif,           // 传递 EXIF 显示开关
            showZoomRatio = showZoomRatio, // 传递缩放 显示开关
            onCardClick = { index ->
                currentPickerIndex = index
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
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
    zoomLimit: Float,
    showExif: Boolean,
    showZoomRatio: Boolean,    // 传递缩放 显示开关
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val createGestureHandler: (Int) -> (Float, Float, Float) -> Unit = { index ->
        { zoomDelta, panX, panY ->
            if (isLocked) {
                photoStates.forEach { state ->
                    state.scale = (state.scale * zoomDelta).coerceIn(1f, zoomLimit) // 使用传入的 zoomLimit
                    state.offsetX += panX
                    state.offsetY += panY
                }
            } else {
                val state = photoStates[index]
                state.scale = (state.scale * zoomDelta).coerceIn(1f, zoomLimit)     // 使用传入的 zoomLimit
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
                    modifier = Modifier.fillMaxSize(),
                    showExif = showExif,
                    showZoomRatio = showZoomRatio, // 传递缩放 显示开关
                )
            }
        }
//        CompareMode.TWO -> {
//            Column(
//                modifier = modifier.fillMaxSize().padding(16.dp),
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                ZoomablePhotoContainer(
//                    uri = selectedUris[0],
//                    onClick = { onCardClick(0) },
//                    transformState = photoStates[0],
//                    onGestureDelta = createGestureHandler(0),
//                    modifier = Modifier.weight(1f).fillMaxWidth(),
//                    showExif = showExif,
//                    showZoomRatio = showZoomRatio, // 传递缩放 显示开关
//                )
//                ZoomablePhotoContainer(
//                    uri = selectedUris[1],
//                    onClick = { onCardClick(1) },
//                    transformState = photoStates[1],
//                    onGestureDelta = createGestureHandler(1),
//                    modifier = Modifier.weight(1f).fillMaxWidth(),
//                    showExif = showExif,
//                    showZoomRatio = showZoomRatio, // 传递缩放 显示开关
//                )
//            }
//        }
        CompareMode.TWO -> {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            if (isLandscape) {
                // 横屏：左右布局
                Row(
                    modifier = modifier.fillMaxSize().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomablePhotoContainer(
                        uri = selectedUris[0],
                        onClick = { onCardClick(0) },
                        transformState = photoStates[0],
                        onGestureDelta = createGestureHandler(0),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ZoomablePhotoContainer(
                        uri = selectedUris[1],
                        onClick = { onCardClick(1) },
                        transformState = photoStates[1],
                        onGestureDelta = createGestureHandler(1),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                // 竖屏：上下布局（保持原样）
                Column(
                    modifier = modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZoomablePhotoContainer(
                        uri = selectedUris[0],
                        onClick = { onCardClick(0) },
                        transformState = photoStates[0],
                        onGestureDelta = createGestureHandler(0),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    ZoomablePhotoContainer(
                        uri = selectedUris[1],
                        onClick = { onCardClick(1) },
                        transformState = photoStates[1],
                        onGestureDelta = createGestureHandler(1),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
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
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio, // 传递缩放 显示开关
                    )
                    ZoomablePhotoContainer(
                        uri = selectedUris[1],
                        onClick = { onCardClick(1) },
                        transformState = photoStates[1],
                        onGestureDelta = createGestureHandler(1),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio, // 传递缩放 显示开关
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
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio, // 传递缩放 显示开关
                    )
                    ZoomablePhotoContainer(
                        uri = selectedUris[3],
                        onClick = { onCardClick(3) },
                        transformState = photoStates[3],
                        onGestureDelta = createGestureHandler(3),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        showExif = showExif,
                        showZoomRatio = showZoomRatio, // 传递缩放 显示开关
                    )
                }
            }
        }
    }
}

@Composable
fun ZoomablePhotoContainer(
    uri: Uri?,
    onClick: () -> Unit,
    transformState: PhotoTransformState,
    onGestureDelta: (Float, Float, Float) -> Unit,
    showExif: Boolean = true,
    showZoomRatio: Boolean = true, // 传递缩放 显示开关
    modifier: Modifier = Modifier
) {
    val currentOnGestureDelta by rememberUpdatedState(onGestureDelta)
    val currentOnClick by rememberUpdatedState(onClick)

    // 读取 EXIF 信息（异步）
    val context = LocalContext.current
    var exifInfo by remember { mutableStateOf<ExifDisplayInfo?>(null) }
    LaunchedEffect(uri) {
        exifInfo = if (uri != null) readExifInfo(context, uri) else null
    }

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
                .clickable { currentOnClick() }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        currentOnGestureDelta(zoom, pan.x, pan.y)
                    }
                }
        ) {
            // 图片层（可缩放/平移）
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
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .size(Size.ORIGINAL)   // 强制加载原始分辨率
                            .build(),
                        contentDescription = "选中的照片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("点击选择", style = MaterialTheme.typography.bodySmall)
                }
            }

            val exifTextStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 14.sp,      // 减小行高，可根据需要调整
                color = Color.White,
                fontWeight = FontWeight.Medium
            )

            // EXIF 信息层（固定在左上角，不受缩放影响）
            if (showExif && uri != null && exifInfo != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = buildString {
                                append(exifInfo!!.fileName)
                                if (exifInfo!!.resolution.isNotEmpty()) {
                                    append(" (${exifInfo!!.resolution})")
                                }
                            },
                            style = exifTextStyle
                        )
                        Text(
                            text = exifInfo!!.device,
                            style = exifTextStyle
                        )
                        Text(
                            text = "${exifInfo!!.aperture} | ${exifInfo!!.shutter} | ISO ${exifInfo!!.iso}",
                            style = exifTextStyle
                        )
                        Text(
                            text = buildString {
                                append(exifInfo!!.focalLength)
                                exifInfo!!.focalLength35mm?.let { append(" (35mm等效: $it)") }
                            },
                            style = exifTextStyle
                        )
                    }
                }
            }
//            // 左下角放大比率显示（在 EXIF 层之后，同一父 Box 内）
//            if (showZoomRatio) {
//                val zoomPercent = (transformState.scale * 100).toInt()
//                Box(
//                    modifier = Modifier
//                        .align(Alignment.BottomStart)
//                        .padding(8.dp)
//                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
//                        .padding(horizontal = 8.dp, vertical = 4.dp)
//                ) {
//                    Text(
//                        text = "${zoomPercent}%",
//                        color = Color.White,
//                        fontSize = 12.sp,
//                        fontWeight = FontWeight.Medium
//                    )
//                }
//            }
        }
    }
}