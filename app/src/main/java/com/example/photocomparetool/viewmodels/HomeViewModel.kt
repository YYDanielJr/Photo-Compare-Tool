package com.example.photocomparetool.viewmodels

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.photocomparetool.CompareMode
import com.example.photocomparetool.PhotoTransformState

class HomeViewModel : ViewModel() {
    var selectedMode by mutableStateOf(CompareMode.SINGLE)
    var isLocked by mutableStateOf(false)

    // 四张图片的 Uri，null 表示未选择
    val selectedUris = mutableStateListOf<Uri?>(null, null, null, null)

    // 四张图片的缩放/平移状态
    val photoStates = mutableStateListOf(
        PhotoTransformState(),
        PhotoTransformState(),
        PhotoTransformState(),
        PhotoTransformState()
    )
}