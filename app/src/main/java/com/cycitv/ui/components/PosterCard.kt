package com.cycitv.ui.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import coil3.compose.AsyncImage
import com.cycitv.data.dto.VideoCardDto
import com.cycitv.ui.theme.BorderSubtle
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.SurfaceHi
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain

/**
 * 番剧卡片: 四方扁平(0 圆角)的网格单元。
 *
 * 设计要点:
 *  - 直角矩形, 无圆角、无缩放、无辉光 —— 只用 2dp 描边 + 实心底色划边界, 不做"悬浮卡片"
 *  - 聚焦态仅换描边(2dp 细线 -> 3dp 高对比粗线), 不位移不缩放
 *  - 标题/年份行用 heightIn(min) 而非 height(): 系统字体放大时不再裁切文字
 *    (此前 height(17.dp) 硬限高, 电视 fontScale > 1 时年份标签被切掉一截)
 */
@Composable
fun PosterCard(
    v: VideoCardDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverWidth: Int? = null,
    coverRatio: Float = 1.25f,
    /** 该番的更新进度(由列表页后台并发补全); null = 还没查到, 先退回显示总集数 */
    update: com.cycitv.data.api.UpdateInfo? = null,
) {
    val loader = LocalAppImageLoader.current
    val shape = CycShape.card
    // 焦点描边平滑过渡(110ms): 快速左右切换时描边不再硬跳变
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // 未聚焦用中性细线, 聚焦切品牌色 —— 原实现两个状态共用同一个 borderColor,
    // 导致"聚焦只变粗细不变色", 焦点信号很弱
    val borderColor by animateColorAsState(
        targetValue = if (focused) Primary else BorderSubtle,
        animationSpec = tween(durationMillis = 110),
        label = "cardBorder",
    )
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth(),
        shape = CardDefaults.shape(
            shape = shape,
            focusedShape = shape,
            pressedShape = shape,
        ),
        colors = CardDefaults.colors(
            containerColor = Surface,
            contentColor = TextMain,
            focusedContainerColor = Surface,
            pressedContainerColor = Surface,
        ),
        // 轻微放大与全站焦点语言保持一致(原为 1f 不缩放, 与 ScheduleScreen 的 1.03f 冲突)
        scale = CardDefaults.scale(focusedScale = 1.03f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.5f.dp, borderColor), shape = shape),
            focusedBorder = Border(BorderStroke(2.5f.dp, borderColor), shape = shape),
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(4.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f / coverRatio) // 宽:高 = 4:5
                    .clip(CycShape.chip)
                    .background(SurfaceHi)
            ) {
                if (loader != null) {
                    AsyncImage(
                        model = v.coverUrl,
                        imageLoader = loader,
                        contentDescription = v.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight()
                    )
                }
                // 左上: 评分(黑底白字角标, 去掉原来的黄色)
                v.score?.takeIf { it > 0 }?.let {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .background(Color(0xE60F1012), CycShape.badge)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            it.toString(),
                            color = TextMain,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                // 右下: 更新进度角标
                //   已查到详情 -> 更新至 N / 全 N 话 / 待更新
                //   还没查到   -> 先退回显示总集数(暂态)
                val badge: String? = when {
                    update == null -> if (v.total > 0) v.total.toString() + "话" else null
                    update.updated <= 0 -> "待开播"
                    update.completed -> "全 " + update.updated + " 话"
                    else -> "更新至 " + update.updated
                }
                // 角标背景不可控(海报可能是纯白/纯黑/高饱和), 因此不能靠"和页面底色协调":
                // 统一用半透明深底 + 白字保证文字可读, 再加 1dp 品牌色描边,
                // 让角标边界在深色海报上不至于消失, 同时粉色只作为轮廓而非填充。
                if (badge != null) {
                    val badgeBg = if (update != null && update.updated <= 0) {
                        Color(0xCC5A6472)   // 待开播: 中性灰, 与"有更新"区分
                    } else {
                        Color(0xE60F1012)
                    }
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .background(badgeBg, CycShape.badge)
                            .border(1.dp, Primary.copy(alpha = 0.55f), CycShape.badge)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            badge,
                            color = TextMain,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            // 标题: 最小高度保证卡片等高, 但允许字体放大时自然撑开(不再裁切)
            Text(
                v.title,
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, start = 2.dp, end = 2.dp)
                    .heightIn(min = 32.dp)
            )
            // 年份: 最小高度占位(无年份也占位, 保证每张卡等高)
            Row(
                Modifier
                    .padding(start = 2.dp, top = 4.dp)
                    .heightIn(min = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                v.year?.takeIf { it > 0 }?.let {
                    Box(
                        Modifier
                            .background(SurfaceHi, CycShape.chip)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            it.toString(),
                            color = TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
