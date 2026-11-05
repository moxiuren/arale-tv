package com.cycitv.ui.screens

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.PlayerView
import com.cycitv.App
import com.cycitv.data.api.GirigiriApi
import com.cycitv.data.dto.SectionDto
import com.cycitv.data.room.HistoryEntity
import com.cycitv.ui.components.tvFocus
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.PrimaryDim
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain
import com.cycitv.util.PlayerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f) // 看动漫同款速度档

/** 创建播放器;software=true 时只允许软件 AVC 解码器(硬解崩溃时用于绕过厂商解码器 bug) */
private fun createPlayer(ctx: Context, software: Boolean, bandwidthMeter: DefaultBandwidthMeter? = null): ExoPlayer =
    PlayerFactory.create(ctx, software, bandwidthMeter)

@Composable
fun PlayScreen(
    videoId: Long,
    initialSectionId: Long,
    playerCode: String,
    animeTitle: String,
    initialSections: List<SectionDto> = emptyList(),
    directUrl: String? = null, // 调试/兜底:非空则绕过 play-url 直接播该地址
    onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as App
    val scope = rememberCoroutineScope()

    var sections by remember { mutableStateOf(initialSections) }
    var players by remember { mutableStateOf(listOf(playerCode)) }
    var sectionId by remember { mutableLongStateOf(initialSectionId) }
    var playing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    // 控制条:默认隐藏,播放中全屏;按 OK 暂停并呼出,播放中几秒无操作自动隐藏
    var showControls by remember { mutableStateOf(false) }
    var showEpList by remember { mutableStateOf(false) }
    var showSpeedList by remember { mutableStateOf(false) }
    var showGiriList by remember { mutableStateOf(false) }  // girigiri 切源候选弹层
    var giriItems by remember { mutableStateOf<List<GirigiriApi.Item>>(emptyList()) }
    var giriSel by remember { mutableIntStateOf(0) }
    var giriLoading by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var hideCountdown by remember { mutableLongStateOf(0L) } // 控制条自动隐藏倒计时(ms)
    var wasPlayingOnStop by remember { mutableStateOf(false) }   // 退后台前是否在播(HOME 恢复用)
    var playerCodeNow by remember { mutableStateOf(playerCode) }
    // 封面地址: 详情接口才返回, 拉到后供"继续观看"列表展示。
    // 原实现 HistoryEntity.coverUrl 字段存在但从未被写入, 恒为空串。
    var animeCover by remember { mutableStateOf("") }
    var speedIdx by remember { mutableIntStateOf(2) } // 1.0x
    var resolving by remember { mutableStateOf(false) } // 正在请求播放地址
    var buffering by remember { mutableStateOf(true) }  // 播放器缓冲中
    // 软解模式:进入时若本集已记录"需要软解"(硬解崩溃过),直接走软解
    var softwareDecode by remember { mutableStateOf(initialSectionId in app.softDecodeSections) }
    // 网络带宽表:供"视频信息"按钮显示实时网速
    val bandwidthMeter = remember { DefaultBandwidthMeter.Builder(ctx).build() }
    var bandwidthKbps by remember { mutableStateOf(0L) } // 由 ticker 刷新
    // 自管控制条焦点(Compose 焦点树在 AnimatedContent 嵌套下不可靠):0=播放/暂停 1=下一话 2=重播 3=选集 4=速度 5=信息 6=切源 7=进度条
    var ctrlSel by remember { mutableIntStateOf(0) }
    // 弹层自管焦点:选集/速度弹层的选项行(Compose 焦点树被 PlayerView 阻断,必须自管)
    var panelSel by remember { mutableIntStateOf(0) }
    var errSel by remember { mutableIntStateOf(0) } // 错误弹窗:0=重试 1=退出
    // 进度/时长由 ticker 驱动,保证进度条和时间为实时值
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    // 统一的中央 OSD: 文本 + 过期时刻。清除交给 ticker, 不再依赖 LaunchedEffect(key),
    // 彻底杜绝「提示残留不消失」(key 未变化时 LaunchedEffect 不重启的历史坑)。
    var osdText by remember { mutableStateOf<String?>(null) }
    var osdUntil by remember { mutableLongStateOf(0L) }
    fun showOsd(t: String, ms: Long = 1500) {
        osdText = t
        osdUntil = SystemClock.elapsedRealtime() + ms
    }
    // 长按加速跳转: 以「按下瞬间的位置」为固定起点做累积位移, 长按才能一路加速且数值准确
    var holdBase by remember { mutableLongStateOf(-1L) }
    var holdAccum by remember { mutableLongStateOf(0L) }
    var holdLastAt by remember { mutableLongStateOf(0L) }
    var giriTried by remember { mutableStateOf(false) }  // 当前播放已尝试过 girigiri 备用源(防死循环)
    var sourceNow by remember { mutableStateOf("主站") }  // 当前播放来源:主站 / 备用源
    var currentSectionId by remember { mutableLongStateOf(initialSectionId) }
    var paused by remember { mutableStateOf(false) } // 播放器实际暂停中(非"加载流程"标志)

    // 可重建播放器:硬解崩溃时切软解重建
    var player by remember { mutableStateOf(createPlayer(ctx, softwareDecode, bandwidthMeter)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            posMs = player.currentPosition
            durMs = player.duration
            paused = !player.isPlaying
            if (osdText != null && SystemClock.elapsedRealtime() > osdUntil) osdText = null
            bandwidthKbps = bandwidthMeter.bitrateEstimate / 1000 // 实时网速 Kbps
            // 控制条自动隐藏:播放中 5 秒无按键操作
            if (showControls && player.isPlaying) {
                hideCountdown += 500
                if (hideCountdown >= 5000) {
                    showControls = false
                    hideCountdown = 0
                }
            } else {
                hideCountdown = 0
            }
        }
    }

    DisposableEffect(player) {
        val p = player // 捕获当前值:不能读最新 state,否则切软解重建时会误释放新播放器
        onDispose { p.release() }
    }

    // 播放时保持屏幕常亮;退后台(HOME)暂停播放并释放常亮,回前台恢复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val win = (ctx as? android.app.Activity)?.window
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (player.isPlaying) {
                        wasPlayingOnStop = true
                        player.pause()
                        showControls = false
                    }
                    win?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Lifecycle.Event.ON_START -> {
                    if (wasPlayingOnStop) {
                        wasPlayingOnStop = false
                        player.play()
                        showOsd("▶")
                    }
                    win?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        win?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            win?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // girigiri 切源:打开全库候选列表让用户选番(手动选择,不依赖脆弱的自动匹配)
    // preferTitle 非空时(cycani 失败自动兜底),把候选按标题相似度排序,最像的排最前
    fun openGiriPicker(preferTitle: String? = null) {
        showEpList = false; showSpeedList = false; showInfo = false
        if (giriLoading) return
        giriLoading = true
        showGiriList = true
        giriItems = emptyList()
        giriSel = 0
        scope.launch {
            try {
                val items = app.giri.allLibrary()
                giriItems = if (preferTitle.isNullOrBlank()) items
                else {
                    val kw = preferTitle.trim().lowercase()
                    items.sortedByDescending { item ->
                        val t = item.title.trim().lowercase()
                        when {
                            t == kw -> 100
                            t.contains(kw) || kw.contains(t) -> 60
                            t.take(2) == kw.take(2) && t.take(2).isNotBlank() -> 30
                            else -> 0
                        }
                    }
                }
            } catch (_: Exception) {
                giriItems = emptyList()
            }
            giriLoading = false
        }
    }

    // 选中 girigiri 番 → 拉详情 → 按当前集号播放
    fun playGiriItem(item: GirigiriApi.Item?) {
        if (item == null) return
        showGiriList = false
        scope.launch {
            resolving = true
            try {
                val d = app.giri.detail(item.gvId)
                val eps = d.episodes
                if (eps.isEmpty()) throw IllegalStateException("该番无播放集")
                // 当前集号:cycani 选集标题 "第05集" → 5
                val epNo = try {
                    val all = app.api.allSections(videoId, playerCodeNow)
                    val cur = all.find { it.id == currentSectionId }
                    Regex("(\\d+)").find(cur?.title ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
                } catch (_: Exception) { null }
                // 按集号匹配 girigiri 播放路径 /playGV{id}-{sid}-{n}/;匹配不到用序号
                val ep = (if (epNo != null) {
                    eps.firstOrNull { Regex("-(\\d+)/$").find(it)?.groupValues?.get(1)?.toIntOrNull() == epNo }
                } else null)
                    ?: eps.getOrNull((epNo ?: 1) - 1)
                    ?: eps.first()
                val url = app.giri.playUrl(ep)
                android.util.Log.e("CYC", "giri 切源: ${d.title} 集号=$epNo ep=$ep -> ${url.take(60)}")
                resolving = false
                sourceNow = "备用源"
                showOsd("🔁")
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
                playing = true
            } catch (e: Exception) {
                resolving = false
                errorMsg = "备用源播放失败: ${e.message}"
            }
        }
    }

    // 播放指定集(含线路轮换重试)
    fun loadSection(id: Long, playerIdx: Int = 0) {
        scope.launch {
            playing = true
            resolving = true
            errorMsg = null
            try {
                val t0 = android.os.SystemClock.elapsedRealtime()
                val url = app.api.playUrl(id).url
                android.util.Log.e("CYC", "loadSection playUrl ${android.os.SystemClock.elapsedRealtime() - t0}ms url=${url.take(80)}")
                if (url.isBlank()) throw IllegalStateException("播放地址为空")
                resolving = false
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
                sectionId = id
                currentSectionId = id
                // 断点续播
                val hist = try { app.db().historyDao().bySection(id) } catch (_: Exception) { null }
                if (hist != null && hist.progressMs > 10_000 && hist.progressMs < hist.durationMs - 10_000) {
                    player.seekTo(hist.progressMs)
                }
            } catch (e: Exception) {
                val next = playerIdx + 1
                if (next < players.size) {
                    playerCodeNow = players[next]
                    sections = try { app.api.allSections(videoId, playerCodeNow) } catch (_: Exception) { sections }
                    loadSection(id, next)
                } else if (e is com.cycitv.data.api.ApiException && (e.apiCode == 401 || e.apiCode == 1002)) {
                    // 能走到这里说明 API 层的静默续登也没成功(凭据缺失或已失效)
                    errorMsg = "登录已失效，自动续登失败\n请到「设置 → 登录账号」重新扫码登录"
                    playing = false
                } else {
                    // 主源失败(坏节点/超时) → 弹 girigiri 候选列表,当前番优先
                    errorMsg = null
                    openGiriPicker(animeTitle)
                }
            }
        }
    }

    // 调试/兜底:直接播放指定地址(绕过 play-url)
    fun loadDirect(url: String) {
        scope.launch {
            playing = true
            resolving = true
            errorMsg = null
            try {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
                resolving = false
                sectionId = initialSectionId
            } catch (e: Exception) {
                errorMsg = "播放失败: ${e.message}"
                playing = false
                resolving = false
            }
        }
    }

    // 硬解崩溃 → 切软解重建播放器并重放当前集
    fun switchToSoftwareDecoder() {
        if (softwareDecode) return
        softwareDecode = true
        player.release()
        player = createPlayer(ctx, software = true, bandwidthMeter)
        if (directUrl != null) loadDirect(directUrl) else loadSection(sectionId)
    }

    // 播放器状态监听:缓冲中 / 播放出错(硬解崩溃自动切软解重放)
    DisposableEffect(player) {
        val p = player // 捕获当前值
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            }
            override fun onPlayerError(error: PlaybackException) {
                buffering = false
                if (!softwareDecode && error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED) {
                    // 小米电视硬解偶发崩溃:本集记入软解名单,切软解重放。
                    // 注意:此回调在播放器内部线程,所有播放器操作必须回主线程执行
                    app.softDecodeSections.add(sectionId)
                    scope.launch {
                        try { switchToSoftwareDecoder() } catch (_: Exception) {}
                    }
                } else if (directUrl == null && !giriTried) {
                    // 主源流拉不动(CDN 坏节点) → 弹 girigiri 候选列表,当前番优先
                    giriTried = true
                    scope.launch { openGiriPicker(animeTitle) }
                } else {
                    errorMsg = "播放出错: ${error.errorCodeName}\n${error.message?.take(160) ?: ""}"
                }
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    // 加载选集与线路:详情页已传选集则立即播放(只差一个播放地址请求),后台再刷新线路/选集
    LaunchedEffect(videoId) {
        if (directUrl != null) {
            // 调试直达:直接播指定地址,不请求 play-url
            loadDirect(directUrl)
            return@LaunchedEffect
        }
        if (sections.isNotEmpty()) {
            loadSection(initialSectionId)
            // 后台刷新最新线路与选集(播放不等待)
            scope.launch {
                try {
                    val d = app.api.videoDetail(videoId)
                    if (d.coverUrl.isNotBlank()) animeCover = d.coverUrl
                    val ps = d.playFrom.map { it.code }.ifEmpty { listOf(playerCode) }
                    if (ps.none { it == playerCodeNow }) playerCodeNow = ps.first()
                    val secs = app.api.allSections(videoId, playerCodeNow)
                    if (secs.isNotEmpty()) {
                        sections = secs
                        if (sections.none { it.id == sectionId }) loadSection(sections.first().id)
                    }
                } catch (_: Exception) {}
            }
            return@LaunchedEffect
        }
        // 无传入选集:完整加载后播放
        val d = try { app.api.videoDetail(videoId) } catch (_: Exception) { null }
        if (!d?.coverUrl.isNullOrBlank()) animeCover = d.coverUrl
        players = d?.playFrom?.map { it.code }?.ifEmpty { listOf(playerCode) } ?: listOf(playerCode)
        if (players.none { it == playerCodeNow }) playerCodeNow = players.first()
        sections = try { app.api.allSections(videoId, playerCodeNow) } catch (_: Exception) { emptyList() }
        if (sections.isNotEmpty()) {
            loadSection(initialSectionId)
        } else {
            errorMsg = "选集加载失败"
        }
    }

    // 进度落盘: 抽成一个函数, 周期上报与退出前补写共用同一份字段组装,
    // 避免两处各写一份、日后加字段时漏改其中一处
    suspend fun saveProgress(pos: Long, dur: Long, sid: Long) {
        if (dur <= 0 || pos <= 0) return
        try {
            app.db().historyDao().upsert(
                HistoryEntity(
                    sectionId = sid,
                    animeId = videoId,
                    title = animeTitle,
                    coverUrl = animeCover,
                    sectionTitle = sections.firstOrNull { it.id == sid }?.title ?: "",
                    progressMs = pos,
                    durationMs = dur,
                )
            )
        } catch (_: Exception) {}
    }

    // 周期上报进度
    LaunchedEffect(sectionId) {
        while (true) {
            delay(10_000)
            saveProgress(player.currentPosition, player.duration, sectionId)
        }
    }

    // 退出前补写一次: 每 10 秒一写意味着最多丢 10 秒, 短片段上就是"续播回到开头"
    DisposableEffect(sectionId) {
        onDispose {
            val pos = player.currentPosition
            val dur = player.duration
            val sid = sectionId
            // onDispose 是同步的, 落盘必须丢到应用级 scope, 否则协程随页面一起被取消
            app.scope.launch { saveProgress(pos, dur, sid) }
        }
    }

    // 播放结束自动下一集。
    // 修正: 原来用「剩余 8 秒」预判(pos > dur - 8000)就切集, 会把片尾最后几秒整段吃掉,
    // 导致视频看着"没播完"。改为等播放器真正进入 STATE_ENDED 才切。
    val curSectionId by rememberUpdatedState(sectionId)
    val curSections by rememberUpdatedState(sections)
    DisposableEffect(player) {
        val p = player
        val endListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val idx = curSections.indexOfFirst { it.id == curSectionId }
                val next = curSections.getOrNull(idx + 1) ?: return
                sectionId = next.id
                loadSection(next.id)
            }
        }
        p.addListener(endListener)
        onDispose { p.removeListener(endListener) }
    }

    /** 毫秒 -> 时:分:秒 */
    fun fmtTime(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
    }

    /**
     * 左右键跳转:
     *  - 按一下 = 固定 10 秒(连按 5 次正好 50 秒, 步长不累积)
     *  - 长按 = 按 repeatCount 提速(20s -> 30s -> 60s -> 120s), 中央 OSD 显示目标时间实时递增
     *  - 以按下瞬间的位置为固定起点累积, 避免 seek 未生效时越跳越偏
     */
    fun seekKey(forward: Boolean, repeatCount: Int) {
        val now = SystemClock.elapsedRealtime()
        // 距上次按键超过 700ms 视为新的一轮(遥控器松手后重新按)
        if (holdBase < 0 || now - holdLastAt > 700) {
            holdBase = player.currentPosition
            holdAccum = 0
        }
        val stepMs = when {
            repeatCount <= 0 -> 10_000L
            repeatCount < 4 -> 20_000L
            repeatCount < 10 -> 30_000L
            repeatCount < 20 -> 60_000L
            else -> 120_000L
        }
        holdAccum += if (forward) stepMs else -stepMs
        val durRaw = player.duration
        val dur = if (durRaw > 0) durRaw else Long.MAX_VALUE
        val target = (holdBase + holdAccum).coerceIn(0L, dur)
        holdAccum = target - holdBase
        player.seekTo(target)
        holdLastAt = now
        val sign = if (forward) "+" else "-"
        showOsd(fmtTime(target) + "  " + sign + (kotlin.math.abs(holdAccum) / 1000) + "s", 1600)
    }

    fun goEpisode(delta: Int) {
        val idx = sections.indexOfFirst { it.id == sectionId }
        val target = sections.getOrNull(idx + delta) ?: return
        sectionId = target.id
        loadSection(target.id)
    }

    // 重播当前集:回到开头从头开始
    fun replayCurrent() {
        showSpeedList = false
        player.seekTo(0)
        if (!player.isPlaying) player.play()
        showOsd("↻")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val code = ev.nativeKeyEvent.keyCode
                when {
                    code == KeyEvent.KEYCODE_BACK ->
                        if (showEpList) { showEpList = false; true }
                        else if (showSpeedList) { showSpeedList = false; true }
                        else if (showInfo) { showInfo = false; true }
                        else if (showControls) { showControls = false; true }
                        else { onExit(); true }

                    errorMsg != null -> when (code) {
                        // 错误弹窗自管焦点:LEFT/RIGHT 切换 重试/退出,OK 执行
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> { errSel = 1 - errSel; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                            if (errSel == 0) {
                                errorMsg = null
                                loadSection(sectionId)
                            } else {
                                onExit()
                            }
                            true
                        }
                        else -> false
                    }

                    // 右侧选集弹层导航(自管焦点:UP/DOWN 选择,OK 确定)
                    showEpList -> when (code) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M,
                        KeyEvent.KEYCODE_DPAD_LEFT -> { showEpList = false; true }
                        KeyEvent.KEYCODE_DPAD_UP -> { panelSel = (panelSel - 1).coerceAtLeast(0); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { panelSel = (panelSel + 1).coerceAtMost(sections.size - 1); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                            sections.getOrNull(panelSel)?.let { s ->
                                sectionId = s.id
                                loadSection(s.id)
                            }
                            showEpList = false
                            true
                        }
                        else -> false // 放行给弹层自身焦点系统(上下移动/OK 选择)
                    }

                    // 右侧速度弹层导航(自管焦点:UP/DOWN 选择,OK 确定)
                    showSpeedList -> when (code) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M,
                        KeyEvent.KEYCODE_DPAD_LEFT -> { showSpeedList = false; true }
                        KeyEvent.KEYCODE_DPAD_UP -> { panelSel = (panelSel - 1).coerceAtLeast(0); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { panelSel = (panelSel + 1).coerceAtMost(SPEEDS.size - 1); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                            speedIdx = panelSel
                            player.setPlaybackSpeed(SPEEDS[speedIdx])
                            showSpeedList = false
                            true
                        }
                        else -> false
                    }

                    // girigiri 切源弹层导航(自管焦点:UP/DOWN 选番,OK 确认切源)
                    showGiriList -> when (code) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M,
                        KeyEvent.KEYCODE_DPAD_LEFT -> { showGiriList = false; true }
                        KeyEvent.KEYCODE_DPAD_UP -> { giriSel = (giriSel - 1).coerceAtLeast(0); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { giriSel = (giriSel + 1).coerceAtMost(giriItems.size - 1); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                            playGiriItem(giriItems.getOrNull(giriSel))
                            true
                        }
                        else -> false
                    }

                    // 右侧信息弹层导航
                    showInfo -> when (code) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M,
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            showInfo = false; true
                        }
                        else -> false
                    }

                    // 控制条呼出时自管焦点(方向键移动选择,OK 执行;操作即重置自动隐藏倒计时)
                    showControls -> when (code) {
                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M -> { showControls = false; true }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            hideCountdown = 0
                            if (ctrlSel == 7) seekKey(false, ev.nativeKeyEvent.repeatCount) else ctrlSel = (ctrlSel - 1).coerceAtLeast(0)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            hideCountdown = 0
                            if (ctrlSel == 7) seekKey(true, ev.nativeKeyEvent.repeatCount) else ctrlSel = (ctrlSel + 1).coerceAtMost(7)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> { hideCountdown = 0; if (ctrlSel == 7) ctrlSel = 0; true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { hideCountdown = 0; ctrlSel = if (ctrlSel == 7) 0 else 7; true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                            hideCountdown = 0
                            when (ctrlSel) {
                                0 -> if (player.isPlaying) { player.pause(); showOsd("⏸") }
                                else { player.play(); showOsd("▶") }
                                1 -> { showEpList = false; showSpeedList = false; showInfo = false; goEpisode(1) }
                                2 -> replayCurrent()
                                3 -> {
                                    panelSel = sections.indexOfFirst { it.id == sectionId }.coerceAtLeast(0)
                                    showEpList = true; showSpeedList = false; showInfo = false
                                }
                                4 -> {
                                    panelSel = speedIdx
                                    showSpeedList = true; showEpList = false; showInfo = false
                                }
                                5 -> { showInfo = true; showEpList = false; showSpeedList = false }
                                6 -> openGiriPicker()
                                7 -> { player.seekTo(0); showOsd("00:00") }
                            }
                            true
                        }
                        else -> false
                    }

                    // 默认态(控制条隐藏):OK 暂停并呼出控制条;UP/MENU 只呼出不暂停;左右 seek
                    else -> when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { seekKey(false, ev.nativeKeyEvent.repeatCount); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { seekKey(true, ev.nativeKeyEvent.repeatCount); true }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M -> {
                            showControls = true; true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                            showControls = true
                            if (player.isPlaying) { player.pause(); showOsd("⏸") }
                            true
                        }
                        KeyEvent.KEYCODE_0 -> { sectionId = sections.firstOrNull()?.id ?: sectionId; true }
                        in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                            val n = ev.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_1
                            sections.getOrNull(n)?.let { sectionId = it.id }
                            true
                        }
                        else -> false
                    }
                }
            }
    ) {
        AndroidView(
            factory = { c: Context ->
                PlayerView(c).apply {
                    this.player = player
                    useController = false
                    // 禁止 PlayerView 抢焦点,否则按键事件不进入 Compose
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            update = { view -> view.player = player }, // 切软解重建播放器后重新绑定
            modifier = Modifier.fillMaxSize()
        )

        // 顶部信息(随控制条一起显隐,全屏播放时保持干净)
        if (showControls) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(20.dp)
            ) {
                Text(animeTitle, color = TextMain, fontSize = 20.sp, maxLines = 1)
                Text(
                    sections.firstOrNull { it.id == sectionId }?.title ?: "",
                    color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1
                )
            }
        }

        // 右侧选集弹层(竖列,参照看动漫:按选集按钮后右侧出现整排直列选项)
        if (showEpList) {
            val epListState = rememberLazyListState()
            // 打开时定位到当前集(选集列表可能很长,默认从第 1 集开始不合理)
            LaunchedEffect(Unit) {
                val idx = sections.indexOfFirst { it.id == sectionId }.coerceAtLeast(0)
                epListState.scrollToItem(idx)
            }
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(0.85f)
                    .width(300.dp)
                    .clip(CycShape.panel)
                    .background(Color(0xE6121218))
                    .padding(vertical = 16.dp)
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                    Text("选集", color = TextMain, fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                    LazyColumn(state = epListState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        itemsIndexed(sections) { i, s ->
                            val sel = i == panelSel // 自管焦点高亮
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { sectionId = s.id; loadSection(s.id); showEpList = false }
                                    .height(46.dp)
                                    .clip(CycShape.chip)
                                    .background(if (sel) PrimaryDim else Surface)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    (if (s.id == sectionId) "● " else "") + (s.title.ifBlank { "${s.id}" }),
                                    color = when {
                                        sel -> Color(0xFF08131F)
                                        s.id == sectionId -> Primary
                                        else -> TextMain
                                    },
                                    fontSize = 15.sp, maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // 右侧速度弹层(竖列,参照看动漫:0.5/0.75/1.0/1.25/1.5/2.0/3.0/4.0)
        if (showSpeedList) {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(220.dp)
                    .clip(CycShape.panel)
                    .background(Color(0xE6121218))
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("播放速度", color = TextMain, fontSize = 18.sp, modifier = Modifier.padding(bottom = 10.dp))
                SPEEDS.forEachIndexed { i, sp ->
                    val sel = i == panelSel // 自管焦点高亮
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clickable {
                                speedIdx = i
                                player.setPlaybackSpeed(sp)
                                showSpeedList = false
                            }
                            .height(44.dp)
                            .clip(CycShape.chip)
                            .background(if (sel) PrimaryDim else Surface)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "${sp}x",
                            color = if (sel) Color(0xFF08131F) else TextMain,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // 右侧信息浮层(网速/画质等,参照看动漫按钮6)
        if (showInfo) {
            val idx = sections.indexOfFirst { it.id == sectionId }
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(320.dp)
                    .clip(CycShape.panel)
                    .background(Color(0xE6121218))
                    .padding(20.dp)
            ) {
                Text("视频信息", color = TextMain, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
                InfoRow("片名", animeTitle)
                InfoRow("当前集", sections.getOrNull(idx)?.title ?: "${idx + 1}")
                InfoRow("画质", if (player.videoSize.height > 1080) "${player.videoSize.height}p 高清" else if (player.videoSize.height > 0) "${player.videoSize.height}p" else "未知")
                InfoRow("网速", if (bandwidthKbps > 0) "${bandwidthKbps / 1000.0} Mbps" else "计算中…")
                InfoRow("播放源", sourceNow)
                InfoRow("倍速", "${SPEEDS[speedIdx]}x")
                InfoRow("播放状态", if (player.isPlaying) "▶ 播放中" else "⏸ 已暂停")
                Text("按 返回 关闭", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp))
            }
        }

        // 底部控制条(隐藏态全屏;OK/UP 呼出,播放中 5 秒无操作自动隐藏)
        if (showControls) {
            val idx = sections.indexOfFirst { it.id == sectionId }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xE6121218))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CtrlButton(
                        if (player.isPlaying) "⏸ 暂停" else "▶ 播放",
                        selected = ctrlSel == 0
                    ) {
                        if (player.isPlaying) { player.pause(); showOsd("⏸") }
                        else { player.play(); showOsd("▶") }
                    }
                    CtrlButton("下一话", enabled = idx in 0 until sections.size - 1, selected = ctrlSel == 1) {
                        showEpList = false; showSpeedList = false; showInfo = false; goEpisode(1)
                    }
                    CtrlButton("重播", selected = ctrlSel == 2) { replayCurrent() }
                    CtrlButton("选集", selected = ctrlSel == 3) {
                        showEpList = true; showSpeedList = false; showInfo = false
                    }
                    CtrlButton("${SPEEDS[speedIdx]}x", selected = ctrlSel == 4) {
                        showSpeedList = true; showEpList = false; showInfo = false
                    }
                    CtrlButton("信息", selected = ctrlSel == 5) {
                        showInfo = true; showEpList = false; showSpeedList = false
                    }
                    CtrlButton("切源", selected = ctrlSel == 6) {
                        openGiriPicker()
                    }
                }
                // 进度条行:时间文字左/进度条中/总时长右,同级并排(与看动漫一致)
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(formatMs(posMs), color = TextDim, fontSize = 13.sp)
                    SeekBar(
                        pos = posMs, dur = durMs,
                        selected = ctrlSel == 7,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text(formatMs(durMs), color = TextDim, fontSize = 13.sp)
                }
            }
        }

        // 加载/缓冲指示
        if (resolving || buffering) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = Primary)
                Text(
                    if (resolving) "正在获取播放地址…" else "加载中…",
                    color = TextDim, fontSize = 18.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }

        // 错误弹窗:居中显示,提供重试/退出(自管焦点 errSel)
        errorMsg?.let { msg ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .clip(CycShape.panel)
                    .background(Color(0xEE121218))
                    .padding(horizontal = 48.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("播放失败", color = Color(0xFFFF8080), fontSize = 26.sp)
                Text(
                    msg, color = TextDim, fontSize = 15.sp,
                    modifier = Modifier.padding(top = 12.dp), textAlign = TextAlign.Center
                )
                Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    CtrlButton("重试", selected = errSel == 0) {
                        errorMsg = null
                        loadSection(sectionId)
                    }
                    CtrlButton("退出", selected = errSel == 1) { onExit() }
                }
            }
        }

        // 暂停角标(控制条隐藏时暂停,提示当前为暂停态)
        if (paused && !showControls && errorMsg == null && !resolving && !buffering) {
            Text(
                "已暂停",
                color = TextDim, fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 130.dp)
                    .clip(CycShape.chip)
                    .background(Color(0x99000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // 中央 OSD: 单一渲染点(此前 seekHint 与 osdIcon 两块都画 osdText, 会大小字重叠)
        //   纯符号(▶/⏸/↻/🔁) -> 大字号; 时间类文本("12:34  +130s") -> 常规字号
        osdText?.let { txt ->
            val isIcon = txt.length <= 2
            Text(
                txt,
                color = Color.White,
                fontSize = if (isIcon) 72.sp else 42.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CycShape.chip)
                    .background(Color(0x99000000))
                    .padding(
                        horizontal = if (isIcon) 44.dp else 30.dp,
                        vertical = if (isIcon) 22.dp else 14.dp,
                    )
            )
        }
    }
}

@Composable
private fun CtrlButton(
    text: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clickable(enabled = enabled, onClick = onClick)
            .height(50.dp)
            .clip(CycShape.chip)
            .background(if (selected) PrimaryDim else Surface)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = when {
                selected -> Color(0xFF08131F)
                enabled -> TextMain
                else -> TextDim
            },
            fontSize = 16.sp
        )
    }
}

/** 信息浮层行:左侧标签(暗色),右侧值(亮色) */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 14.sp, modifier = Modifier.width(60.dp))
        Text(
            value, color = TextMain, fontSize = 15.sp,
            modifier = Modifier.weight(1f), maxLines = 1
        )
    }
}

/** 进度条:selected 时显示白色外框(自管焦点,不依赖 Compose 焦点系统);LEFT/RIGHT seek 由外部处理 */
@Composable
private fun SeekBar(
    pos: Long,
    dur: Long,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    // drawWithContent 的 lambda 不是 Composable 上下文, 取色需提前
    val primaryColor = Primary
    Box(
        modifier
            .height(44.dp)
            .drawWithContent {
                drawContent()
                val h = size.height
                val y = h / 2
                val trackH = 6.dp.toPx()
                drawRoundRect(
                    Color(0x66FFFFFF),
                    topLeft = Offset(0f, y - trackH / 2),
                    size = Size(size.width, trackH),
                    cornerRadius = CornerRadius(trackH / 2)
                )
                val ratio = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                if (ratio > 0) {
                    drawRoundRect(
                        primaryColor,
                        topLeft = Offset(0f, y - trackH / 2),
                        size = Size(size.width * ratio, trackH),
                        cornerRadius = CornerRadius(trackH / 2)
                    )
                }
                // 播放头
                val thumbX = size.width * ratio
                drawCircle(primaryColor, radius = 9.dp.toPx(), center = Offset(thumbX, y))
                if (selected) {
                    val inset = strokePx / 2
                    drawRoundRect(
                        Color.White,
                        style = Stroke(width = strokePx),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                    )
                }
            }
    ) { }
}

private fun formatMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0) // 播放器未就绪时 duration/position 可能是 -1(或极小负值)
    val s = safe / 1000
    return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}
