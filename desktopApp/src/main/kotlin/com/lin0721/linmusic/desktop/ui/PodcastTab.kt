package com.lin0721.linmusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.desktop.ui.theme.DesktopColors
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.formatListenerCount
import com.lin0721.linmusic.feature.podcast.domain.formatProgramDuration
import com.lin0721.linmusic.feature.podcast.domain.formatSubCount
import com.lin0721.linmusic.feature.podcast.ui.PodcastUiState

private val EdgePadding = 24.dp

// 「播客」页：节目在上（双击播放），电台货架在下；桌面端暂无电台详情页，货架只展示
@Composable
fun PodcastTab(
    uiState: PodcastUiState,
    listState: LazyListState,
    onCategorySelect: (Long?) -> Unit,
    onProgramPlay: (Int) -> Unit,
    onRetry: () -> Unit
) {
    when (uiState) {
        PodcastUiState.Loading -> HomeTabLoading()
        is PodcastUiState.Error -> HomeTabError(uiState.message, onRetry)
        is PodcastUiState.Success -> {
            val data = uiState.data
            HoverScrollbarBox(listState) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (data.categories.isNotEmpty()) {
                        item(key = "categories") {
                            CategoryChips(data.categories, data.selectedCategoryId, onCategorySelect)
                        }
                    }
                    item(key = "programs") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionTitle("最新节目")
                            if (data.isProgramLoading) {
                                HomeTabLoading(Modifier.fillMaxWidth().height(160.dp))
                            } else {
                                Column(Modifier.padding(horizontal = EdgePadding - 12.dp)) {
                                    data.programs.forEachIndexed { index, program ->
                                        ProgramRow(program) { onProgramPlay(index) }
                                    }
                                }
                            }
                        }
                    }
                    // 未登录时该段为空，整块不出现
                    if (data.personalizedRadios.isNotEmpty()) {
                        item(key = "personalized") { RadioShelf("猜你喜欢", data.personalizedRadios) }
                    }
                    if (data.recommendRadios.isNotEmpty()) {
                        item(key = "recommend") { RadioShelf("精选电台", data.recommendRadios) }
                    }
                    if (data.toplistRadios.isNotEmpty()) {
                        item(key = "toplist") { RadioShelf("热门电台榜", data.toplistRadios, showRank = true) }
                    }
                }
            }
        }
    }
}

// 分类胶囊：「推荐」是虚拟项，对应不限分类
@Composable
private fun CategoryChips(categories: List<PodcastCategory>, selectedId: Long?, onSelect: (Long?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = EdgePadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "all") { OutlineChip("推荐", selectedId == null) { onSelect(null) } }
        itemsIndexed(categories, key = { _, item -> item.id }) { _, category ->
            OutlineChip(category.name, selectedId == category.id) { onSelect(category.id) }
        }
    }
}

// 节目行：封面 + 标题 + 电台·主播 + 时长收听数，双击播放
@Composable
private fun ProgramRow(program: PodcastProgram, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(if (hovered) DesktopColors.PaneHover else Color.Transparent)
            .hoverable(interaction).onDoubleClick(onPlay).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(program.coverUrl, 64.dp)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                program.name,
                color = DesktopColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val source = listOfNotNull(
                program.radioName.takeIf { it.isNotBlank() },
                program.djName.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (source.isNotBlank()) {
                Text(
                    source,
                    color = DesktopColors.TextGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            val meta = listOfNotNull(
                formatProgramDuration(program.durationMs).takeIf { it.isNotBlank() },
                formatListenerCount(program.listenerCount).takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = DesktopColors.TextGray.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RadioShelf(title: String, radios: List<PodcastRadio>, showRank: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = EdgePadding),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(radios, key = { index, item -> "${item.id}_$index" }) { index, radio ->
                RadioCard(radio, rank = if (showRank) index + 1 else null)
            }
        }
    }
}

// 电台卡片；桌面端没有电台详情页，故不可点。rank 非空时在封面左上角标名次
@Composable
private fun RadioCard(radio: PodcastRadio, rank: Int?) {
    Column(Modifier.width(CardWidth)) {
        Box {
            Cover(radio.picUrl, CardWidth, shape = RoundedCornerShape(6.dp))
            if (rank != null) {
                Text(
                    "$rank",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            radio.name,
            color = DesktopColors.TextPrimary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        val meta = listOfNotNull(
            radio.programCount.takeIf { it > 0 }?.let { "$it 期" },
            formatSubCount(radio.subCount).takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, color = DesktopColors.TextGray, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
