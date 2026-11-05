package com.cycitv.data.repository

import com.cycitv.data.api.CycaniApi
import com.cycitv.data.api.UpdateInfo
import com.cycitv.data.dto.RecommendRowDto
import com.cycitv.data.dto.VideoCardDto
import com.cycitv.data.dto.ZoneDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeRepository(
    private val api: CycaniApi,
    private val scope: CoroutineScope,
) {
    private val cacheMutex = Mutex()
    private var cachedRecommend: List<RecommendRowDto>? = null
    private var cachedZones: List<ZoneDto>? = null

    suspend fun recommend(force: Boolean = false): List<RecommendRowDto> = cacheMutex.withLock {
        if (!force && cachedRecommend != null) return cachedRecommend!!
        val data = api.recommend()
        cachedRecommend = data.list
        return data.list
    }

    /**
     * 首页「最近更新」: 并发拉 TV番组(zone 1) 与 剧场番组(zone 2) 的 update_time 列表,
     * 合并去重后交给调用方按发布日期排序(列表接口不含时间戳, 只能靠详情补)。
     */
    suspend fun latest(page: Int = 1, perZone: Int = 30): List<VideoCardDto> {
        val tv = runCatching { api.videos(1, page, perZone, "update_time") }.getOrNull()?.list ?: emptyList()
        val movie = runCatching { api.videos(2, page, perZone, "update_time") }.getOrNull()?.list ?: emptyList()
        return (tv + movie).distinctBy { it.videoId }
    }

    /** 转发: 单部番的更新进度(已更新集数 / 是否完结 / 发布日期) */
    suspend fun updateInfo(id: Long): UpdateInfo? = api.updateInfo(id)

    /** 转发: 并发补全一批番的更新进度(限流) */
    suspend fun fillUpdates(
        ids: List<Long>,
        concurrency: Int = 8,
        onEach: suspend (Long, UpdateInfo) -> Unit,
    ) = api.fillUpdates(ids, concurrency, onEach)

    /**
     * 给历史记录回填封面。
     *
     * 早期版本的进度上报没写 coverUrl 字段(列存在但恒为空), 所以老用户的
     * "继续观看"全是空封面。这里按 animeId 并发拉详情补回来, 拉到就落库,
     * 下次直接命中, 不会重复请求。限流 4: 这是首页首屏的次要任务,
     * 不能和主列表抢带宽。
     */
    suspend fun backfillHistoryCovers(
        items: List<com.cycitv.data.room.HistoryEntity>,
        dao: com.cycitv.data.room.HistoryDao,
    ) {
        val missing = items.filter { it.coverUrl.isBlank() }
        if (missing.isEmpty()) return
        // 先全部收齐再统一写库。若在回调里逐条 upsert, 每次写都会让 Room 的 Flow
        // 重发全表 -> 再次触发本函数 -> n 条记录产生 n 次全量重渲染。
        val covers = java.util.concurrent.ConcurrentHashMap<Long, String>()
        fillUpdates(missing.map { it.animeId }.distinct(), concurrency = 4) { id, info ->
            if (info.coverUrl.isNotBlank()) covers[id] = info.coverUrl
        }
        if (covers.isEmpty()) return
        // 同一部番可能有多条历史记录, 全部补上, 避免换集后又变空
        missing.filter { covers.containsKey(it.animeId) }.forEach { h ->
            runCatching { dao.upsert(h.copy(coverUrl = covers.getValue(h.animeId))) }
        }
    }

    suspend fun zones(force: Boolean = false): List<ZoneDto> = cacheMutex.withLock {
        if (!force && cachedZones != null) return cachedZones!!
        val zones = api.zones()
        cachedZones = zones
        return zones
    }
}
