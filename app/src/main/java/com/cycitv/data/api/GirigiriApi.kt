package com.cycitv.data.api

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 备用源: girigirilove (MacCMS 站点, 魔改路由)。
 * 已实测: 首页/列表/详情/播放全通, 播放页无验证可直接取流。
 * 路由格式:
 *  列表:  /show/{type}-----------/  或 /show/{type}-----------/{page}/
 *  详情:  /GV{id}/
 *  播放:  /playGV{id}-{sid}-{nid}/  → player_aaaa JSON → url 三重解码 → m3u8
 * 封面/分片: https://giri.girigirilove.top/...
 */
class GirigiriApi(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 15_000,
) {
    companion object {
        const val SITE = "girigirilove"
        const val SITE_LABEL = "giri"
        private const val BASE = "https://ani.girigirilove.com"
        private const val IMG_BASE = "https://giri.girigirilove.top"
        private val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    /** 首页/周表列表页视频条目 */
    data class Item(val gvId: Long, val title: String, val cover: String?, val desc: String = "")

    /** 详情页解析结果 */
    data class Detail(
        val title: String,
        val cover: String?,
        val desc: String,
        val episodes: List<String>,   // /playGV{id}-{sid}-{nid}/ 路径
        val sid: Int = 1,
    )

    private fun open(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        conn.setRequestProperty("Referer", "$BASE/")
        conn.instanceFollowRedirects = true
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val code = conn.responseCode
        if (code >= 400) throw IOException("girigirilove HTTP $code")
        return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private suspend fun get(urlStr: String): String = withContext(Dispatchers.IO) {
        val conn = open(urlStr)
        try {
            readBody(conn)
        } finally {
            conn.disconnect()
        }
    }

    /** 列表页: /show/{type}-----------/{page}/ (type=2 日番, 33 剧场版等; 0=全部首页) */
    suspend fun list(type: Int = 0, page: Int = 1): List<Item> {
        val url = if (type == 0) {
            "$BASE/"  // 首页 243 条
        } else {
            "$BASE/show/$type-----------/$page/"
        }
        val html = get(url)
        return parseItems(html)
    }

    /** 解析页面里的 /GV{id}/ 条目(首页/列表/搜索页通用) */
    fun parseItems(html: String): List<Item> {
        val items = ArrayList<Item>()
        // 首页/列表条目: <a href="/GV{id}/" title="{片名}">...<img ... data-src="{封面}">
        val re = Regex("href=\"/GV(\\d+)/\"[^>]*title=\"([^\"]+)\"")
        val reImg = Regex("data-src=\"([^\"]+)\"")
        val sorted = re.findAll(html).map { it.range.first }.toList()
        val titles = re.findAll(html).map { Pair(it.range.first, it.groupValues) }.toList()
        // 简单方案: 标题直接从 a 标签 title 取; 封面尝试从同区域抓
        for (m in re.findAll(html)) {
            val id = m.groupValues[1].toLongOrNull() ?: continue
            val title = m.groupValues[2]
            // 找 a 标签结束前的 data-src 封面
            val startIdx = m.range.last
            val endIdx = html.indexOf("</a>", startIdx).let { if (it < 0) html.length else it }
            val seg = html.substring(startIdx, minOf(endIdx, startIdx + 800))
            val cover = reImg.find(seg)?.groupValues?.get(1)
            items.add(Item(id, title, normUrl(cover ?: ""), ""))
        }
        return items.distinctBy { it.gvId }
    }

    private fun normUrl(u: String): String? {
        if (u.isBlank() || u.startsWith("data:")) return null
        return if (u.startsWith("//")) "https:$u"
        else if (u.startsWith("/")) "$IMG_BASE$u"
        else u
    }

    /** 详情: /GV{id}/ → 标题/封面/简介/集数 */
    suspend fun detail(gvId: Long): Detail {
        val html = get("$BASE/GV$gvId/")
        val title = Regex("<title>([^_<]+)").find(html)?.groupValues?.get(1)?.trim()
            ?: Regex("alt=\"([^\"]+)\"").find(html)?.groupValues?.get(1) ?: "GV$gvId"
        val cover = Regex("data-src=\"(/upload/vod/[^\"]+)\"").find(html)?.groupValues?.get(1)?.let { "$IMG_BASE$it" }
        val desc = Regex("content=\"([^\"]{10,300})\"").find(html)?.groupValues?.get(1) ?: ""
        // 集数链接 /playGV{id}-{sid}-{nid}/
        val eps = Regex("href=\"(/playGV$gvId-\\d+-\\d+/)\"").findAll(html)
            .map { it.groupValues[1] }.distinct().toList()
        val sid = Regex("/playGV$gvId-(\\d+)-").find(html)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        if (eps.isEmpty()) throw IOException("girigirilove 未找到播放集: GV$gvId")
        return Detail(title, cover, desc, eps, sid)
    }

    /** 播放: 解析播放页 player_aaaa 的 url → 三重解码 → m3u8 */
    suspend fun playUrl(playPath: String): String = withContext(Dispatchers.IO) {
        val url = if (playPath.startsWith("http")) playPath else "$BASE$playPath"
        val html = get(url)
        val start = html.indexOf("player_aaaa={")
        if (start < 0) throw IOException("girigirilove 播放页无 player_aaaa")
        var i = start + "player_aaaa=".length
        var depth = 0
        while (i < html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) break }
            }
            i++
        }
        val cfgRaw = html.substring(start + "player_aaaa=".length, i + 1)
        val urlField = Regex("\"url\":\"([^\"]+)\"").find(cfgRaw)?.groupValues?.get(1)
            ?: throw IOException("girigirilove play url 字段缺失")
        // 三重: base64 → url-decode → url-decode
        val b64 = try { Base64.getDecoder().decode(urlField).toString(Charsets.UTF_8) }
        catch (_: Exception) { urlField }
        var out = b64
        repeat(3) { out = java.net.URLDecoder.decode(out, "UTF-8") }
        if (!out.startsWith("http")) throw IOException("girigirilove 解码异常: ${out.take(40)}")
        out
    }

    /**
     * 周表: 解析首页 HTML 里 #week-module-{1..7} 模块(每日节目块)。
     * 数据直接嵌入首页, 无需额外接口; 返回 day(1=周一..7=周日) → 视频列表。
     */
    suspend fun weekday(): Map<Int, List<Item>> = withContext(Dispatchers.IO) {
        val html = get("$BASE/")
        val out = HashMap<Int, List<Item>>()
        val re = Regex("href=\"/GV(\\d+)/\"[^>]*title=\"([^\"]+)\"")
        val reImg = Regex("data-src=\"([^\"]+)\"")
        for (d in 1..7) {
            val start = html.indexOf("id=\"week-module-$d\"")
            if (start < 0) continue
            val end = html.indexOf("id=\"week-module-${d + 1}\"", start + 20)
            val seg = if (end > 0) html.substring(start, end) else html.substring(start, start + 40_000)
            val list = ArrayList<Item>()
            for (m in re.findAll(seg)) {
                val id = m.groupValues[1].toLongOrNull() ?: continue
                val title = m.groupValues[2]
                val startIdx = m.range.last
                val endIdx = seg.indexOf("</a>", startIdx).let { if (it < 0) seg.length else it }
                val pic = seg.substring(startIdx, minOf(endIdx, startIdx + 800))
                val cover = reImg.find(pic)?.groupValues?.get(1)
                list.add(Item(id, title, normUrl(cover ?: ""), ""))
            }
            out[d] = list.distinctBy { it.gvId }
        }
        out
    }

    /** 全站库: 首页 + 各分类, 去重。切源候选列表用 */
    suspend fun allLibrary(): List<Item> {
        val all = ArrayList<Item>()
        all += list(0, 1)
        for (t in intArrayOf(2, 3, 4, 33, 44)) {
            for (p in 1..2) {
                try { all += list(t, p) } catch (_: Exception) { break }
            }
        }
        return all.distinctBy { it.gvId }
    }

    /** 备用搜索: 用首页/分类列表做标题模糊匹配(站方搜索接口对无 JS 客户端返回兜底页) */
    suspend fun searchByTitle(keyword: String, pages: Int = 2): List<Item> {
        val all = ArrayList<Item>()
        // 首页 + 日番/美番等分类,去重后覆盖站内全库
        all += list(0, 1)
        for (t in intArrayOf(2, 3, 4, 33, 44)) {
            for (p in 1..pages) {
                try { all += list(t, p) } catch (_: Exception) { break }
            }
        }
        val kw = keyword.trim().lowercase()
        return all.filter {
            it.title.isNotBlank() && (it.title.lowercase().contains(kw) || kw.contains(it.title.lowercase().take(2)))
        }.distinctBy { it.gvId }.take(20)
    }
}
