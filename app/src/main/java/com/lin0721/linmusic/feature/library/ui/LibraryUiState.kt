package com.lin0721.linmusic.feature.library.ui

// 音乐库展示分区（与网易云网页端「创建的歌单 / 收藏的歌单」结构对齐）
data class LibrarySection(
    val title: String,
    val items: List<LibraryItem>
)

// 音乐库 UI 状态
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    // 加载成功，携带全量条目、过滤后条目、分区结果与各分类计数
    data class Success(
        val allItems: List<LibraryItem>,
        val filteredItems: List<LibraryItem>,
        val sections: List<LibrarySection> = emptyList(),
        val artistCount: Int,
        val playlistCount: Int,
        val albumCount: Int
    ) : LibraryUiState

    data class Error(val message: String) : LibraryUiState
}
