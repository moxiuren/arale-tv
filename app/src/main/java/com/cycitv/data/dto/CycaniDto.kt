package com.cycitv.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiEnvelope<T>(
    val code: Int,
    val msg: String = "",
    val data: T? = null,
)

@Serializable
data class Pager(
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val total: Int,
)

@Serializable
data class ZoneDto(
    val id: Int,
    val name: String,
    val sort: Int = 0,
    /** 站方提供的筛选项(年份/分类), 用于分类页的季度选择 */
    val filters: ZoneFiltersDto? = null,
)

@Serializable
data class ZoneFiltersDto(
    val categories: List<String> = emptyList(),
    /** 形如 ["2026","2025",...], 站方为字符串数组 */
    val years: List<String> = emptyList(),
)

@Serializable
data class VideoCardDto(
    @SerialName("video_id") val videoId: Long,
    @SerialName("zone_id") val zoneId: Int,
    val title: String,
    val description: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    val remarks: String = "",
    /** 站方发布日期(形如 2026-10-02), 用于首页"最近更新"排序 */
    @SerialName("publish_date") val publishDate: String = "",
    val area: String = "",
    val year: Int? = null,
    val version: String = "",
    val score: Double? = null,
    val hits: Long = 0,
    val total: Int = 0,
    val tags: List<String> = emptyList(),
)

@Serializable
data class RecommendRowDto(
    val id: Int,
    val name: String = "",
    @SerialName("link_type") val linkType: String = "",
    @SerialName("zone_id") val zoneId: Int? = null,
    val videos: List<VideoCardDto> = emptyList(),
)

@Serializable
data class RecommendData(
    val list: List<RecommendRowDto> = emptyList(),
)

@Serializable
data class VideoListData(
    val list: List<VideoCardDto> = emptyList(),
    val pager: Pager? = null,
)

@Serializable
data class HotWordsData(
    val keywords: List<String> = emptyList(),
)

@Serializable
data class VideoDetailDto(
    val id: Long,
    val title: String,
    val description: String = "",
    @SerialName("cover_url") val coverUrl: String = "",
    val area: String = "",
    val language: String = "",
    val year: Int? = null,
    val version: String = "",
    val score: Double? = null,
    val hits: Long = 0,
    val total: Int = 0,
    /** 是否完结(站方 completed 字段) */
    val completed: Boolean = false,
    val director: List<String> = emptyList(),
    val actor: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val state: String = "",
    /** 形如 "1|周五23:05后": 第 1 集将于该时刻更新(此时 play_from 里那条多半还是先导PV) */
    val remarks: String = "",
    /** 站方发布日期(形如 2026-10-02), 用于首页"最近更新"排序 */
    @SerialName("publish_date") val publishDate: String = "",
    @SerialName("play_from") val playFrom: List<PlayFromDto> = emptyList(),
    @SerialName("watch_progress") val watchProgress: WatchProgressDto? = null,
)

@Serializable
data class PlayFromDto(
    val code: String,
    val title: String = "",
    val count: Int = 0,
)

@Serializable
data class WatchProgressDto(
    @SerialName("section_id") val sectionId: Long,
    @SerialName("section_title") val sectionTitle: String = "",
    val progress: Long = 0,
    val duration: Long = 0,
)

@Serializable
data class SectionDto(
    val id: Long,
    val title: String = "",
)

@Serializable
data class SectionListData(
    val list: List<SectionDto> = emptyList(),
    val pager: Pager? = null,
)

@Serializable
data class PlayUrlData(
    val name: String = "",
    val url: String = "",
)

@Serializable
data class WeekdayData(
    val list: List<WeekdayRowDto> = emptyList(),
)

@Serializable
data class WeekdayRowDto(
    val weekday: Int,
    val videos: List<VideoCardDto> = emptyList(),
)

@Serializable
data class RankData(
    val list: List<RankDto> = emptyList(),
)

@Serializable
data class RankDto(
    val id: Long,
    val name: String = "",
    val type: String = "",
    @SerialName("video_count") val videoCount: Int = 0,
)

@Serializable
data class RankVideosData(
    val list: List<VideoCardDto> = emptyList(),
)
