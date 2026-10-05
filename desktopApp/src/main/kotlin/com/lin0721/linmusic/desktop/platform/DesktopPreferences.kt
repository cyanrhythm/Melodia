package com.lin0721.linmusic.desktop.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class CloseAction { TRAY, EXIT }

// 音乐库形态：收起为窄条 / 默认侧栏 / 展开铺满中间区域
enum class LibraryMode { RAIL, DEFAULT, EXPANDED }

// 音乐库条目的显示方式
enum class LibraryViewMode { COMPACT_LIST, LIST, SMALL_GRID, LARGE_GRID }

// 桌面端独有设置；快捷键以 “动作=修饰键:键码” 分号拼接存储，空值表示用户已清除该键
class DesktopPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        const val STORE_NAME = "desktop_prefs"

        private val KEY_HOTKEYS = stringPreferencesKey("hotkeys")
        private val KEY_MEDIA_KEYS_ENABLED = booleanPreferencesKey("media_keys_enabled")
        private val KEY_CLOSE_ACTION = stringPreferencesKey("close_action")
        private val KEY_NOW_PLAYING_OPEN = booleanPreferencesKey("now_playing_panel_open")
        private val KEY_LIBRARY_MODE = stringPreferencesKey("library_mode")
        private val KEY_LIBRARY_VIEW_MODE = stringPreferencesKey("library_view_mode")
        private val KEY_LIBRARY_WIDTH = floatPreferencesKey("library_width")
        private val KEY_NOW_PLAYING_WIDTH = floatPreferencesKey("now_playing_width")
    }

    val hotkeys: Flow<Map<HotkeyAction, HotkeyCombo?>> = dataStore.data.map { prefs ->
        val saved = parseHotkeys(prefs[KEY_HOTKEYS].orEmpty())
        HotkeyAction.entries.associateWith { action ->
            if (saved.containsKey(action)) saved[action] else HotkeyCombo.defaults[action]
        }
    }.distinctUntilChanged()

    val mediaKeysEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MEDIA_KEYS_ENABLED] ?: true }.distinctUntilChanged()

    val closeAction: Flow<CloseAction> = dataStore.data.map { prefs ->
        prefs[KEY_CLOSE_ACTION]?.let { name -> CloseAction.entries.firstOrNull { it.name == name } } ?: CloseAction.TRAY
    }.distinctUntilChanged()

    val nowPlayingPanelOpen: Flow<Boolean> = dataStore.data.map { it[KEY_NOW_PLAYING_OPEN] ?: true }.distinctUntilChanged()

    val libraryMode: Flow<LibraryMode> = dataStore.data.map { prefs ->
        prefs[KEY_LIBRARY_MODE]?.let { name -> LibraryMode.entries.firstOrNull { it.name == name } } ?: LibraryMode.DEFAULT
    }.distinctUntilChanged()

    val libraryViewMode: Flow<LibraryViewMode> = dataStore.data.map { prefs ->
        prefs[KEY_LIBRARY_VIEW_MODE]?.let { name -> LibraryViewMode.entries.firstOrNull { it.name == name } } ?: LibraryViewMode.LIST
    }.distinctUntilChanged()

    // 拖动调整过的栏宽（dp），未调整过为 null
    val libraryWidth: Flow<Float?> = dataStore.data.map { it[KEY_LIBRARY_WIDTH] }.distinctUntilChanged()

    val nowPlayingWidth: Flow<Float?> = dataStore.data.map { it[KEY_NOW_PLAYING_WIDTH] }.distinctUntilChanged()

    suspend fun saveHotkeys(hotkeys: Map<HotkeyAction, HotkeyCombo?>) {
        dataStore.edit { prefs ->
            prefs[KEY_HOTKEYS] = hotkeys.entries.joinToString(";") { (action, combo) -> "${action.name}=${combo?.encode().orEmpty()}" }
        }
    }

    suspend fun saveMediaKeysEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_MEDIA_KEYS_ENABLED] = enabled }
    }

    suspend fun saveCloseAction(action: CloseAction) {
        dataStore.edit { it[KEY_CLOSE_ACTION] = action.name }
    }

    suspend fun saveNowPlayingPanelOpen(open: Boolean) {
        dataStore.edit { it[KEY_NOW_PLAYING_OPEN] = open }
    }

    suspend fun saveLibraryMode(mode: LibraryMode) {
        dataStore.edit { it[KEY_LIBRARY_MODE] = mode.name }
    }

    suspend fun saveLibraryViewMode(mode: LibraryViewMode) {
        dataStore.edit { it[KEY_LIBRARY_VIEW_MODE] = mode.name }
    }

    suspend fun saveLibraryWidth(widthDp: Float) {
        dataStore.edit { it[KEY_LIBRARY_WIDTH] = widthDp }
    }

    suspend fun saveNowPlayingWidth(widthDp: Float) {
        dataStore.edit { it[KEY_NOW_PLAYING_WIDTH] = widthDp }
    }

    // 解析失败的条目忽略，回落到默认值
    private fun parseHotkeys(raw: String): Map<HotkeyAction, HotkeyCombo?> {
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<HotkeyAction, HotkeyCombo?>()
        raw.split(';').forEach { entry ->
            val (name, value) = entry.split('=', limit = 2).takeIf { it.size == 2 } ?: return@forEach
            val action = HotkeyAction.entries.firstOrNull { it.name == name } ?: return@forEach
            if (value.isEmpty()) {
                result[action] = null
            } else {
                HotkeyCombo.decode(value)?.let { result[action] = it }
            }
        }
        return result
    }
}
