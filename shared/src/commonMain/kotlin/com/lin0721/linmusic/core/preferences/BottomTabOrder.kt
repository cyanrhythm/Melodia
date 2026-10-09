package com.lin0721.linmusic.core.preferences

// 底栏三个可排序 tab（推荐/搜索/我的）的展示顺序。
// 只存 id 列表，文案与图标由各端自行映射；创建入口不参与排序，恒排最后
object BottomTabOrder {
    const val LIBRARY = "library"
    const val HOME = "home"
    const val SEARCH = "search"

    // 全部合法 id；normalize 里也用它作为缺失项的补齐顺序
    val IDS: List<String> = listOf(HOME, SEARCH, LIBRARY)

    // 默认顺序：推荐 / 搜索 / 我的（冷启动落地页取顺序第一位）
    val DEFAULT: List<String> = IDS

    // 去重、丢弃未知 id，再把缺失的按默认顺序补到末尾（兼容旧版本或损坏数据）
    fun normalize(order: List<String>): List<String> {
        val known = order.filter { it in IDS }.distinct()
        return known + IDS.filterNot { it in known }
    }

    fun encode(order: List<String>): String = normalize(order).joinToString(",")

    fun decode(raw: String?): List<String> =
        if (raw.isNullOrBlank()) DEFAULT else normalize(raw.split(",").map { it.trim() })
}
