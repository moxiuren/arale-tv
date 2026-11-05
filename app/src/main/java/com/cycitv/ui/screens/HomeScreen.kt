package com.cycitv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cycitv.data.api.UpdateInfo
import com.cycitv.data.dto.VideoCardDto
import com.cycitv.data.repository.HomeRepository
import com.cycitv.ui.components.PosterCard
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页 = "最近更新"统一网格。
 *
 * 数据来源: TV番组(zone 1) + 剧场番组(zone 2) 各自的 update_time 列表并发拉取后合并,
 * 再用详情接口的 publish_date 从新到旧排序 —— 列表接口不带时间戳, 只能这样补。
 * 剧场版与 TV 番混排, 不再分行。
 */
@Composable
fun HomeScreen(
    repo: HomeRepository,
    onOpen: (Long) -> Unit,
    onResume: (com.cycitv.data.room.HistoryEntity) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.cycitv.App
    // "继续观看": 订阅 Room 的 history_video。db() 是 suspend, 所以先拿 Flow 再 collect,
    // 期间若 DB 还在后台构建, 会一直等到它 ready(而不是抛异常或静默空列表)
    var recent by remember { mutableStateOf<List<com.cycitv.data.room.HistoryEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        runCatching {
            val dao = app.db().historyDao()
            dao.recent().collect { list ->
                recent = list
                // 老记录没有封面(早期版本没写该字段) -> 并发补一次并落库。
                // 必须另起协程: backfill 里的 upsert 会再次触发这个 Flow,
                // 在 collect 内顺序执行会阻塞收集器, 也会让"补完再触发"变成串行。
                launch { repo.backfillHistoryCovers(list, dao) }
            }
        }
    }
    var page by remember { mutableIntStateOf(1) }
    var raw by remember { mutableStateOf<List<VideoCardDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var end by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val grid = rememberLazyGridState()

    // 更新进度 + 发布日期: 列表接口都不返回, 加载后并发补齐(限流 8), 拉到一条回填一条
    val meta = remember { MutableStateFlow<Map<Long, UpdateInfo>>(emptyMap()) }
    val metaMap by meta.collectAsState()

    fun load(reset: Boolean) {
        scope.launch {
            loading = true
            err = null
            try {
                val p = if (reset) 1 else page + 1
                val list = repo.latest(p)
                raw = if (reset) list else (raw + list).distinctBy { it.videoId }
                page = p
                end = list.isEmpty()
                val missing = raw.map { it.videoId }.distinct().filter { meta.value[it] == null }
                if (missing.isNotEmpty()) {
                    repo.fillUpdates(missing) { id, info -> meta.update { it + (id to info) } }
                }
            } catch (e: Exception) {
                err = e.message ?: "加载失败"
                end = true
                android.util.Log.e("CYC", "home load fail", e)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load(true) }

    // 从新到旧: 主键是发布日期; 还没补到日期的先用年份兜底, 补到后自动重排
    val sorted = remember(raw, metaMap) {
        raw.sortedWith(
            compareByDescending<VideoCardDto> { metaMap[it.videoId]?.publishDate ?: "" }
                .thenByDescending { it.year ?: 0 }
                .thenBy { it.videoId }
        )
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= grid.layoutInfo.totalItemsCount - 8
        }
    }
    LaunchedEffect(shouldLoadMore, loading, end) {
        if (shouldLoadMore && !loading && !end) load(false)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 32.dp, top = 14.dp, end = 32.dp)) {
            Text("最近更新", fontSize = 22.sp, color = TextMain)
            Text(
                "TV 番组 + 剧场版 · 按更新时间从新到旧",
                fontSize = 13.sp,
                color = TextDim,
                modifier = Modifier.padding(top = 2.dp)
            )

            // 有观看记录时才占位; 空列表时组件自己 return, 不留空白
            com.cycitv.ui.components.ContinueWatchingRow(
                items = recent,
                onResume = onResume,
            )

            Box(Modifier.weight(1f).padding(top = 8.dp)) {
                when {
                    raw.isEmpty() && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    raw.isEmpty() && err != null -> Column(Modifier.align(Alignment.Center)) {
                        Text("加载失败", color = TextDim, fontSize = 20.sp)
                        Text(err!!, color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                        Text("按菜单键刷新", color = Primary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    raw.isEmpty() -> Text("暂无数据", color = TextDim, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 126.dp),
                        state = grid,
                        contentPadding = PaddingValues(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(sorted, key = { it.videoId }) { v ->
                            PosterCard(
                                v = v,
                                onClick = { onOpen(v.videoId) },
                                update = metaMap[v.videoId],
                            )
                        }
                        if (!end) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (loading) "加载中…" else "继续下滑加载更多",
                                        fontSize = 15.sp,
                                        color = TextDim,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
