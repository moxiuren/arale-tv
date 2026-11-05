package com.cycitv.data.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_video ORDER BY updateTime DESC")
    fun all(): Flow<List<HistoryEntity>>

    /**
     * "继续观看"列表: 每部番只取最近观看的那一集。
     *
     * 三个过滤条件都不是可选的:
     *  - `progressMs > 30000` —— 刚点开几秒就退出的不算"看过", 否则误触会污染列表
     *  - `progressMs < durationMs - 30000` —— 已经看到片尾的算看完, 不该再催"继续"
     *  - 相关子查询取每部番的最新一条 —— 主键是 sectionId 而非 animeId,
     *    同一部番看过多集会重复列出; 子查询里重复过滤条件, 是为了让
     *    "最新一集已看完"时能回退到这部番更早的未看完记录, 而不是整部消失
     */
    @Query(
        """
        SELECT * FROM history_video h
        WHERE h.progressMs > 30000
          AND h.durationMs > 0
          AND h.progressMs < h.durationMs - 30000
          AND h.updateTime = (
              SELECT MAX(h2.updateTime) FROM history_video h2
              WHERE h2.animeId = h.animeId
                AND h2.progressMs > 30000
                AND h2.durationMs > 0
                AND h2.progressMs < h2.durationMs - 30000
          )
        ORDER BY h.updateTime DESC
        LIMIT :limit
        """
    )
    fun recent(limit: Int = 20): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_video WHERE sectionId = :id LIMIT 1")
    suspend fun bySection(id: Long): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(h: HistoryEntity)

    @Query("DELETE FROM history_video WHERE sectionId = :id")
    suspend fun remove(id: Long)
}

@Dao
interface SearchDao {
    @Query("SELECT * FROM search_history ORDER BY updateTime DESC LIMIT 20")
    fun all(): Flow<List<SearchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SearchEntity)

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
