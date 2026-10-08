package com.lin0721.linmusic.desktop.ui.nowplaying

import com.lin0721.linmusic.core.preferences.FullPlayerCard
import com.lin0721.linmusic.core.preferences.FullPlayerCardSetting

// 设置页只编辑桌面端已实现的卡片，其余卡片（如评论）保持原位与原显隐。
// ordered 是已实现卡片调序后的结果，按它依次填回完整配置里属于已实现卡片的位置
fun mergeSupportedOrder(
    full: List<FullPlayerCardSetting>,
    ordered: List<FullPlayerCardSetting>,
    supported: Set<FullPlayerCard> = SupportedInfoCards
): List<FullPlayerCardSetting> {
    if (ordered.size != full.count { it.card in supported }) return full
    val iterator = ordered.iterator()
    return full.map { if (it.card in supported) iterator.next() else it }
}

fun setCardVisible(full: List<FullPlayerCardSetting>, card: FullPlayerCard, visible: Boolean): List<FullPlayerCardSetting> =
    full.map { if (it.card == card) it.copy(visible = visible) else it }
