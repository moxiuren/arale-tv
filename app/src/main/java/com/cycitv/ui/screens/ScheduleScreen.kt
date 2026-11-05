package com.cycitv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.cycitv.data.api.CycaniApi
import com.cycitv.data.dto.VideoCardDto
import com.cycitv.ui.components.LocalAppImageLoader
import com.cycitv.ui.components.tvFocus
import com.cycitv.ui.theme.BorderSubtle
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.SurfaceHi
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain
import kotlinx.coroutines.delay

private val WEEK = listOf(1, 2, 3, 4, 5, 6, 7)
private val WEEK_NAMES = mapOf(
    1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
    5 to "周五", 6 to "周六", 7 to "周日",
)

/**
 * 站方 remarks 的宽容解析。
 *
 * 2026-10-19 实测抓取 93 条样本, 发现同一字段有 8 种写法。原实现只认
 * `^(\d+)\|周[一二三四五六日](\d{1,2}):(\d{2})后$` 一种, 导致下面这些
 * 全部解析失败、被当成"无时间"排到列表末尾:
 *
 *   "02周三21:35后"    —— 缺竖线
 *   " 02|周六17:05后"  —— 前导空格
 *   " 04|周日1:35后"   —— 前导空格 + 单位数小时
 *   "03|周五更新"      —— 只有"更新", 无具体时刻
 *   "38|周日更新"      —— 长番, 集数是两位数
 *   "03|周六26:00后"   —— 超过 24 点(深夜档, 语义为次日 02:00)
 *
 * 现在的正则把"竖线/空格/后字"都设为可选, 时间部分整体可选。
 */
private val REMARKS_RE = Regex(
    """^\s*(\d+)\s*\|?\s*周([一二三四五六日])\s*(?:(\d{1,2})[:：](\d{2})\s*后?)?\s*(更新)?\s*$"""
)

private val WEEK_CHAR = mapOf(
    '一' to 1, '二' to 2, '三' to 3, '四' to 4,
    '五' to 5, '六' to 6, '日' to 7,
)

/** remarks 解析结果 */
private data class Parsed(
    /** 当天更新的分钟数; 25:35 这类深夜档按 25*60+35 保留原样 */
    val minute: Int?,
    /** 最新集数字符串(保留前导 0 与否都行, 展示时去 0) */
    val episode: String?,
    /** remarks 里声明的星期(用于和分组星期交叉校验) */
    val weekday: Int?,
    /** 原文里只有"更新"没有时刻 */
    val hasUpdateWord: Boolean,
)

private fun parseRemarks(remarks: String): Parsed {
    val m = REMARKS_RE.find(remarks.trim()) ?: return Parsed(null, null, null, false)
    val ep = m.groupValues[1].trimStart('0').ifBlank { "0" }
    val wd = WEEK_CHAR[m.groupValues[2].firstOrNull() ?: ' ']
    val hh = m.groupValues[3].toIntOrNull()
    val mm = m.groupValues[4].toIntOrNull()
    val minute = if (hh != null && mm != null) hh * 60 + mm else null
    return Parsed(minute, ep, wd, m.groupValues[5].isNotBlank())
}

private fun nowMinuteOfDay(): Int {
    val c = java.util.Calendar.getInstance()
    return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
}

private fun todayWeekday(): Int {
    val c = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
    return if (c == 1) 7 else c - 1
}

private fun fmtMinute(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

private data class Entry(
    val v: VideoCardDto,
    val p: Parsed,
)

/** 时间轴的组成单元 —— "现在"必须作为一项插进时间轴, 而不是钉在列表顶部 */
private sealed interface RowItem {
    data class Now(val minute: Int) : RowItem
    data class Anime(val e: Entry) : RowItem
    /** "未排期"分组标题, 让无时间的番有明确归属而不是散落在列表尾部 */
    data class Section(val title: String, val count: Int) : RowItem
}

@Composable
fun ScheduleScreen(
    api: CycaniApi,
    onOpen: (Long) -> Unit,
) {
    val rows by produceState<Map<Int, List<VideoCardDto>>>(emptyMap(), api) {
        value = try {
            api.weekday().list.associate { it.weekday to it.videos }
        } catch (e: Exception) { emptyMap() }
    }
    val today = remember { todayWeekday() }
    var selected by remember { mutableIntStateOf(today) }
    var nowMinute by remember { mutableIntStateOf(nowMinuteOfDay()) }
    val listState = rememberLazyListState()
    val isToday = selected == today

    // 每分钟刷新当前时间
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinute = nowMinuteOfDay()
        }
    }

    // 拆分: 有时刻的按时间升序; 无时刻的单独成组(原实现把两者混排,
    // 39%(36/93) 的无时刻番被 Int.MAX_VALUE 压到列表最底部, 用户以为"没出现")
    val allEntries = (rows[selected] ?: emptyList()).map { Entry(it, parseRemarks(it.remarks)) }
    val timed = allEntries.filter { it.p.minute != null }
        .sortedWith(compareBy<Entry> { it.p.minute!! }.thenBy { it.v.title })
    val untimed = allEntries.filter { it.p.minute == null }
        .sortedWith(compareBy<Entry> { if (it.p.hasUpdateWord) 0 else 1 }.thenBy { it.v.title })

    // 组装时间轴: 已更新的(时间已过) -> [现在] -> 待更新的 -> [未排期分组]
    val items = buildList {
        if (isToday) {
            val (past, future) = timed.partition { it.p.minute!! <= nowMinute }
            past.forEach { add(RowItem.Anime(it)) }
            add(RowItem.Now(nowMinute))
            future.forEach { add(RowItem.Anime(it)) }
        } else {
            timed.forEach { add(RowItem.Anime(it)) }
        }
        if (untimed.isNotEmpty()) {
            add(RowItem.Section("未排期", untimed.size))
            untimed.forEach { add(RowItem.Anime(it)) }
        }
    }

    // 进入页面时滚动到"现在"附近(只对今天生效)
    LaunchedEffect(selected, rows.isNotEmpty()) {
        if (isToday && items.isNotEmpty()) {
            val idx = items.indexOfFirst { it is RowItem.Now }
            if (idx > 0) listState.scrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    Row(Modifier.fillMaxSize().padding(start = 36.dp, top = 20.dp, end = 36.dp)) {
        // 左侧星期切换
        Column(
            Modifier.width(140.dp).padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            WEEK.forEach { w ->
                val isTodayTab = w == today
                var tabFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .onFocusChanged { tabFocused = it.isFocused }
                        .clip(CycShape.chip)
                        .background(
                            when {
                                tabFocused -> Primary.copy(alpha = 0.18f)
                                w == selected -> SurfaceHi
                                else -> Color.Transparent
                            }
                        )
                        .then(
                            if (tabFocused) Modifier.border(2.dp, Primary, CycShape.chip) else Modifier
                        )
                        .clickable { selected = w }
                        .padding(horizontal = 14.dp)
                ) {
                    Text(
                        WEEK_NAMES[w] ?: "",
                        fontSize = 18.sp,
                        fontWeight = if (w == selected) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            w == selected || tabFocused -> TextMain
                            isTodayTab -> Primary
                            else -> TextDim
                        },
                    )
                    if (isTodayTab) {
                        Box(
                            Modifier
                                .padding(start = 8.dp)
                                .size(6.dp)
                                .background(Primary, CircleShape)
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Primary)
            } else if (items.isEmpty()) {
                Text(
                    "当日暂无更新",
                    color = TextDim,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items.size, key = { i ->
                        when (val it = items[i]) {
                            is RowItem.Now -> "now"
                            is RowItem.Section -> "sec-${it.title}"
                            is RowItem.Anime -> it.e.v.videoId
                        }
                    }) { i ->
                        when (val it = items[i]) {
                            is RowItem.Now -> NowDivider(it.minute, isToday)
                            is RowItem.Section -> SectionHeader(it.title, it.count)
                            is RowItem.Anime -> ScheduleRow(it.e, isToday, nowMinute, onOpen)
                        }
                    }
                }
            }
        }
    }
}

/** 时间轴上的"现在"分隔线 —— 把已更新与待更新切开 */
@Composable
private fun NowDivider(minute: Int, isToday: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(Primary, CircleShape))
        Text(
            "现在 ${fmtMinute(minute)}",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Primary,
            modifier = Modifier.padding(start = 12.dp),
        )
        Box(
            Modifier
                .padding(start = 14.dp)
                .weight(1f)
                .height(1.dp)
                .background(Primary.copy(alpha = 0.35f))
        )
        Text(
            if (isToday) "以上已更新" else "当日更新计划",
            fontSize = 13.sp,
            color = TextDim,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/** "未排期"分组标题: 让无时刻的番有明确归属 */
@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDim)
        Text(
            " · $count 部 · 站方未标注具体时刻",
            fontSize = 13.sp,
            color = TextDim.copy(alpha = 0.7f),
        )
        Box(
            Modifier
                .padding(start = 12.dp)
                .weight(1f)
                .height(1.dp)
                .background(BorderSubtle)
        )
    }
}

@Composable
private fun ScheduleRow(
    e: Entry,
    isToday: Boolean,
    nowMinute: Int,
    onOpen: (Long) -> Unit,
) {
    val loader = LocalAppImageLoader.current
    val minute = e.p.minute
    // 已更新 = 有时间 且 (不是今天 或 时间已过)
    val updated = minute != null && (!isToday || minute <= nowMinute)
    val upcoming = minute != null && isToday && minute > nowMinute
    val epText = e.p.episode?.takeIf { it != "0" }?.let { "第 ${it} 集" }

    val statusText = when {
        updated -> epText?.let { "已更新至 $it" } ?: "已更新"
        upcoming -> "今日 ${fmtMinute(minute!!)} 更新" + (epText?.let { " · 已更至 $it" } ?: "")
        minute == null && e.p.hasUpdateWord -> epText?.let { "已更至 $it · 时刻未标注" } ?: "时刻未标注"
        minute == null -> epText?.let { "已更至 $it" } ?: "未标注更新信息"
        else -> "待更新"
    }

    val accent = if (updated || (minute == null && e.p.hasUpdateWord)) Primary else TextDim

    Row(
        Modifier
            .fillMaxWidth()
            .tvFocus(scale = 1.02f, cornerRadius = 12.dp)
            .clip(CycShape.card)
            .clickable { onOpen(e.v.videoId) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 时间列
        Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (minute != null) fmtMinute(minute) else "—",
                fontSize = 17.sp,
                fontWeight = if (minute != null) FontWeight.Bold else FontWeight.Normal,
                color = if (upcoming) TextDim else TextMain,
            )
            Text(
                when {
                    updated -> "已更新"
                    upcoming -> "待更新"
                    minute == null && e.p.hasUpdateWord -> "已更"
                    else -> ""
                },
                fontSize = 11.sp,
                color = accent,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 状态点
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .size(7.dp)
                .background(
                    when {
                        updated -> Primary
                        upcoming -> TextDim.copy(alpha = 0.4f)
                        else -> TextDim.copy(alpha = 0.25f)
                    },
                    CircleShape,
                )
        )
        // 封面
        if (loader != null && e.v.coverUrl.isNotBlank()) {
            AsyncImage(
                model = e.v.coverUrl,
                imageLoader = loader,
                contentDescription = e.v.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(52.dp)
                    .height(72.dp)
                    .clip(CycShape.badge)
                    .background(Surface),
            )
        }
        // 标题 + 状态
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                e.v.title,
                fontSize = 18.sp,
                fontWeight = if (upcoming) FontWeight.Normal else FontWeight.Medium,
                color = if (upcoming) TextDim else TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                statusText,
                fontSize = 13.sp,
                color = accent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
