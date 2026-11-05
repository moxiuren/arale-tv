package com.cycitv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cycitv.data.api.CycaniApi
import com.cycitv.data.api.UpdateInfo
import com.cycitv.data.dto.VideoCardDto
import com.cycitv.data.dto.ZoneDto
import com.cycitv.ui.components.PosterCard
import com.cycitv.ui.components.tvFocus
import com.cycitv.ui.theme.BorderSubtle
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.PrimaryDim
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 排序: 默认「更新」(用户要求, 不要一上来按热度) */
private val SORTS = listOf(
    "update_time" to "更新",
    "hits" to "热度",
    "score" to "评分",
)

/** 番剧季度: 新番只在这 4 个月开播(1月/4月/7月/10月), 不再罗列 12 个月 */
private val SEASONS = listOf(1, 4, 7, 10)

private const val PAGE_SIZE = 48

/** 网格卡片最小宽度: 收窄到 126dp 后 1080p 下能同时看到两排 */
private val CARD_MIN_WIDTH = 126.dp

@Composable
fun CategoryScreen(
    api: CycaniApi,
    onOpen: (Long) -> Unit,
) {
    val zones by produceState<List<ZoneDto>>(emptyList(), api) {
        value = try { api.zones() } catch (e: Exception) { emptyList() }
    }
    var zoneId by remember { mutableIntStateOf(1) }
    var orderBy by remember { mutableStateOf("update_time") }
    // 季度筛选: year=null 表示不限年份; month=0 表示不限季度
    var year by remember { mutableStateOf<String?>(null) }
    var month by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var videos by remember { mutableStateOf<List<VideoCardDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(false) }
    var errMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()

    // 更新进度: 列表接口不返回, 加载后并发补全(限流 8), 逐条回填刷新
    val updates = remember { MutableStateFlow<Map<Long, UpdateInfo>>(emptyMap()) }
    val updatesMap by updates.collectAsState()

    val zoneYears: List<String> = zones.firstOrNull { it.id == zoneId }?.filters?.years ?: emptyList()
    // 年份+季度都有 -> tag(形如 "2026年10月"); 只选年份 -> year 参数; 都不选 -> 不筛
    val tagArg: String? = if (year != null && month > 0) year + "年" + month + "月" else null
    val yearArg: String? = if (year != null && month == 0) year else null

    fun load(reset: Boolean) {
        scope.launch {
            loading = true
            errMsg = null
            try {
                val p = if (reset) 1 else page + 1
                val data = api.videos(zoneId, p, PAGE_SIZE, orderBy, tagArg, yearArg)
                val merged = if (reset) data.list else videos + data.list
                videos = merged
                page = p
                end = data.list.size < PAGE_SIZE
                val missing = merged.map { it.videoId }.distinct().filter { updates.value[it] == null }
                if (missing.isNotEmpty()) {
                    api.fillUpdates(missing) { id, info ->
                        updates.update { it + (id to info) }
                    }
                }
            } catch (e: Exception) {
                errMsg = "加载失败: " + (e.message ?: e.javaClass.simpleName) + "，按菜单键重试"
                end = true
                android.util.Log.e("CYC", "cat load fail", e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(zoneId, orderBy, tagArg, yearArg) {
        videos = emptyList()
        page = 1
        end = false
        load(true)
    }

    LaunchedEffect(zones) {
        if (zones.isNotEmpty() && zones.none { it.id == zoneId }) {
            zoneId = zones.first().id
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= grid.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(shouldLoadMore, loading, end) {
        if (shouldLoadMore && !loading && !end) load(false)
    }

    Box(Modifier.fillMaxSize()) {
        // 顶部整体收紧: 标题 22sp + 两行筛选, 给下方网格让出至少两排的空间
        Column(Modifier.fillMaxSize().padding(start = 32.dp, top = 12.dp, end = 32.dp)) {
            Text("分类浏览", fontSize = 22.sp, color = TextMain)

            // 第一行: 分区 + 排序
            FilterRow {
                zones.forEach { z ->
                    item(key = "z-" + z.id) { Chip(z.name, z.id == zoneId) { zoneId = z.id } }
                }
                item(key = "sep1") { Sep() }
                SORTS.forEach { (k, label) ->
                    item(key = "s-" + k) { Chip(label, k == orderBy) { orderBy = k } }
                }
            }

            // 第二行: 季度 + 年份(年份可横向滚动)
            FilterRow {
                item(key = "m-all") { Chip("全部季度", month == 0) { month = 0 } }
                SEASONS.forEach { m ->
                    item(key = "m-" + m) { Chip(m.toString() + "月", m == month) { month = m } }
                }
                item(key = "sep2") { Sep() }
                item(key = "y-all") { Chip("不限年份", year == null) { year = null } }
                zoneYears.forEach { y ->
                    item(key = "y-" + y) { Chip(y, y == year) { year = y } }
                }
            }

            Box(Modifier.weight(1f).padding(top = 6.dp)) {
                if (videos.isEmpty() && loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (videos.isEmpty() && errMsg != null) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(errMsg!!, color = TextDim, fontSize = 18.sp)
                        Text("按菜单键重试", color = Primary, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                } else if (videos.isEmpty()) {
                    Text("该筛选下暂无内容", color = TextDim, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
                        state = grid,
                        contentPadding = PaddingValues(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        videos.forEach { v ->
                            item(key = v.videoId) {
                                PosterCard(
                                    v = v,
                                    onClick = { onOpen(v.videoId) },
                                    update = updatesMap[v.videoId],
                                )
                            }
                        }
                        if (!end) {
                            item {
                                LoadMoreChip(loading, Modifier.tvFocus().clickable { load(false) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一行筛选项(无标签, 横向可滚动) */
@Composable
private fun FilterRow(items: LazyListScope.() -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { items() }
}

/** 组间竖线分隔 */
@Composable
private fun Sep() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(BorderSubtle)
    )
}

/**
 * 筛选 chip: 选中 = 低透明粉底 + 粉描边 + 粉字, 未选 = 细描边 + 灰字。
 *
 * 2026-10-19: 原实现选中态是"实心粉底 + 深色字"。品牌色改成樱花粉后,
 * 一屏里有 5 个选中 chip 加每张卡片的进度角标, 粉色变成大面积色块,
 * 失去"信号"属性, 整体观感接近廉价模板。现在把粉色压回描边与文字,
 * 选中态依然一眼可辨, 但视觉重量回到该有的档位。
 */
@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .tvFocus()
            .clip(CycShape.chip)
            .background(if (selected) PrimaryDim else Color.Transparent)
            .border(2.dp, if (selected) Primary else BorderSubtle, CycShape.chip)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = if (selected) Primary else TextDim,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LoadMoreChip(loading: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (loading) "加载中…" else "加载更多 (OK)", fontSize = 16.sp, color = TextDim)
    }
}
