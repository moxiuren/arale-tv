package com.cycitv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cycitv.data.room.HistoryEntity
import com.cycitv.ui.theme.BorderSubtle
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.SurfaceHi
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain

/**
 * 首页"继续观看"横排。
 *
 * 数据来自 Room 的 `history_video`, 由 PlayScreen 每 10 秒写入一次进度。
 * 卡片点击后直接进入播放页并从上次位置续播 —— 续播动作本身由 PlayScreen 的
 * `loadSection()` 完成(它内部按 sectionId 查历史并 seekTo), 这里只负责"把用户
 * 送到那一集", 不重复实现 seek 逻辑。
 *
 * 卡片用横版而非竖版海报: 竖版 4:5 在横向滚动条里一屏只能放 4 张, 且进度条
 * 没有稳定的横向空间。横版把海报压到左侧、右侧留给标题与进度, 一屏能看 5 张。
 */
@Composable
fun ContinueWatchingRow(
    items: List<HistoryEntity>,
    onResume: (HistoryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Column(modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 10.dp)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .background(Primary, CycShape.bar)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "继续观看",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "共 ${items.size} 部",
                fontSize = 13.sp,
                color = TextDim,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // 上下留 8dp: 焦点放大 1.03 倍时不会被 LazyRow 边界裁掉
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            items(items, key = { it.sectionId }) { h ->
                ContinueCard(h, onClick = { onResume(h) })
            }
        }
    }
}

@Composable
private fun ContinueCard(h: HistoryEntity, onClick: () -> Unit) {
    val loader = LocalAppImageLoader.current
    val frac = (h.progressMs.toFloat() / h.durationMs.toFloat()).coerceIn(0f, 1f)
    val leftMin = ((h.durationMs - h.progressMs) / 60_000L).coerceAtLeast(0L)

    Row(
        Modifier
            // 210dp: 1080p 下连侧栏可用宽度约 1380dp, 一屏能放下 5 张(留出焦点放大的余量)。
            // 之前 286dp 只能放 2.5 张, 横向滚动的意义被浪费掉了。
            .width(210.dp)
            .tvFocus(scale = 1.03f, cornerRadius = 12.dp)
            .clip(CycShape.card)
            .background(Surface)
            .border(1.dp, BorderSubtle, CycShape.card)
            .clickable { onClick() }
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(52.dp)
                .height(72.dp)
                .clip(CycShape.chip)
                .background(SurfaceHi),
        ) {
            if (loader != null && h.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = h.coverUrl,
                    imageLoader = loader,
                    contentDescription = h.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                )
            }
        }
        Column(
            Modifier
                .padding(start = 10.dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                h.title.ifBlank { "未命名番剧" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                h.sectionTitle.ifBlank { "上次观看" },
                fontSize = 11.sp,
                color = TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            // 进度条: 轨道用 SurfaceHi, 已看部分用品牌色
            Box(
                Modifier
                    .padding(top = 7.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(SurfaceHi),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(Primary),
                )
            }
            Text(
                "已看 ${(frac * 100).toInt()}% · 剩 ${leftMin} 分",
                fontSize = 10.sp,
                color = TextDim,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
            )
        }
    }
}
