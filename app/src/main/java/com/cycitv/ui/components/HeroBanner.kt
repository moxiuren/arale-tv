package com.cycitv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cycitv.data.dto.VideoCardDto
import com.cycitv.ui.theme.LineStrong
import com.cycitv.ui.theme.OnMintText
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.SurfaceHi
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.cycButtonShape
import com.cycitv.ui.theme.BorderSubtle
import com.cycitv.ui.theme.BrandGlowColor
import com.cycitv.ui.theme.BrandGradient
import com.cycitv.ui.theme.MintPrimary
import com.cycitv.ui.theme.SakuraPink
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain

/**
 * 首页首屏顶部 Hero Billboard 宽幅推荐横幅
 */
@Composable
fun HeroBanner(
    featured: VideoCardDto?,
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (featured == null) return

    val loader = LocalAppImageLoader.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp)
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .clip(CycShape.panel)
            .background(Surface)
            .border(1.dp, BorderSubtle, CycShape.panel)
    ) {
        // 右侧/背景封面大图
        if (loader != null) {
            AsyncImage(
                model = featured.coverUrl,
                imageLoader = loader,
                contentDescription = featured.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.65f)
                    .align(Alignment.CenterEnd)
            )
        }

        // 从左到右的浅白渐变遮罩，确保文字在任何封面上都超高对比度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Surface,
                            Surface.copy(alpha = 0.98f),
                            Surface.copy(alpha = 0.88f),
                            Surface.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 左侧内容信息
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.65f)
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // 标签行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(LineStrong, CycShape.badge)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "主推新番",
                            color = OnMintText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    featured.score?.takeIf { it > 0 }?.let {
                        Box(
                            modifier = Modifier
                                .background(SakuraPink.copy(alpha = 0.15f), CycShape.badge)
                                .border(1.dp, SakuraPink.copy(alpha = 0.35f), CycShape.badge)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "★ $it 分",
                                color = TextMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (featured.total > 0) {
                        Box(
                            modifier = Modifier
                                .background(Color(0x33000000), CycShape.badge)
                                .border(1.dp, BorderSubtle, CycShape.badge)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "全 ${featured.total} 话",
                                color = TextMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 大标题
                Text(
                    text = featured.title,
                    color = TextMain,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                // 简介
                Text(
                    text = "高清蓝光画质 · 纯净播放 · 次元城同步排期",
                    color = TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 操作按键区 (官方 TV Button: 遥控聚焦自带 1.1x 缩放 + 品牌双色渐变描边 + 辉光)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 立即播放主按钮 (薄荷绿)
                androidx.tv.material3.Button(
                    shape = cycButtonShape(),
                    onClick = { onPlay(featured.videoId) },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Primary,
                        contentColor = Color.White,
                        focusedContainerColor = Primary,
                        focusedContentColor = Color.White,
                    ),
                    border = androidx.tv.material3.ButtonDefaults.border(
                        focusedBorder = androidx.tv.material3.Border(
                            BorderStroke(3.dp, LineStrong),
                            shape = CycShape.button,
                        ),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "播放",
                        tint = OnMintText,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "立即播放",
                        color = OnMintText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 追番次级按钮 (樱花粉幽灵按钮)
                androidx.tv.material3.Button(
                    shape = cycButtonShape(),
                    onClick = { onPlay(featured.videoId) },
                    colors = androidx.tv.material3.ButtonDefaults.colors(
                        containerColor = Surface,
                        contentColor = TextMain,
                        focusedContainerColor = SurfaceHi,
                        focusedContentColor = TextMain,
                    ),
                    border = androidx.tv.material3.ButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            BorderStroke(2.dp, BorderSubtle),
                            shape = CycShape.button,
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            BorderStroke(3.dp, LineStrong),
                            shape = CycShape.button,
                        ),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkBorder,
                        contentDescription = "追番",
                        tint = TextMain,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text(
                        text = "加入追番",
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
