package com.cycitv.ui.components

import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cycitv.data.dto.VideoCardDto
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.LineStrong
import com.cycitv.ui.theme.MintPrimary
import com.cycitv.ui.theme.TextMain

@Composable
fun VideoRow(
    title: String,
    videos: List<VideoCardDto>,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** 各番的更新进度(由首页后台并发补全), 用于卡片右下角"更新至第几集" */
    updates: Map<Long, com.cycitv.data.api.UpdateInfo> = emptyMap(),
) {
    // 焦点左右移动时让列表平滑滚动(而不是瞬时跳跃), 焦点项尽量停在第 2 个位置
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 12.dp)
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .background(LineStrong, CycShape.bar)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextMain,
            )
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            videos.forEachIndexed { index, v ->
                item(key = v.videoId) {
                    Box(
                        Modifier
                            .width(148.dp)
                            .onFocusChanged { st ->
                                if (st.isFocused) {
                                    scope.launch {
                                        // 滚到前一项, 让焦点卡片停在第 2 位而不是贴左边缘
                                        listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                                    }
                                }
                            }
                    ) {
                        // 焦点效果由官方 TV Card 自带(缩放+渐变描边+辉光), 调用方不再包 tvFocus
                        PosterCard(
                            v = v,
                            onClick = { onOpen(v.videoId) },
                            update = updates[v.videoId],
                        )
                    }
                }
            }
        }
    }
}

