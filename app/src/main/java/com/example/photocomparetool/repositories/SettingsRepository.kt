package com.example.photocomparetool.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 扩展属性，在 Context 上创建 DataStore 单例
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository private constructor(private val dataStore: DataStore<Preferences>) {

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.dataStore).also { INSTANCE = it }
            }
        }
    }

    // 定义所有设置项的 Key
    private object PreferencesKeys {
        val DEFAULT_COMPARE_MODE = stringPreferencesKey("default_compare_mode") // SINGLE, TWO, FOUR
        val IS_LOCKED = booleanPreferencesKey("is_locked")
        val ZOOM_LIMIT = floatPreferencesKey("zoom_limit")           // 默认 3.0f 或 5.0f
        val SHOW_EXIF = booleanPreferencesKey("show_exif")           // 是否显示 EXIF 信息
        val SHOW_ZOOM_RATIO = booleanPreferencesKey("show_zoom_ratio")
    }

    // 公开只读 Flow 给 Compose 收集
    val defaultCompareMode: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_COMPARE_MODE] ?: "SINGLE"
    }

    val isLocked: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.IS_LOCKED] ?: false
    }

    val zoomLimit: Flow<Float> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ZOOM_LIMIT] ?: 10.0f
    }

    val showExif: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_EXIF] ?: true
    }
    val showZoomRatio: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_ZOOM_RATIO] ?: true
    }

    // 更新方法（挂起函数）
    suspend fun setDefaultCompareMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_COMPARE_MODE] = mode
        }
    }

    suspend fun setLocked(locked: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_LOCKED] = locked
        }
    }

    suspend fun setZoomLimit(limit: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ZOOM_LIMIT] = limit
        }
    }

    suspend fun setShowExif(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_EXIF] = show
        }
    }
    suspend fun setShowZoomRatio(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_ZOOM_RATIO] = show
        }
    }
}