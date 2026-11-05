package com.cycitv.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cycitv.data.api.GirigiriApi
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.OnPrimary
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain
import com.cycitv.util.PlayerFactory
import kotlinx.coroutines.launch

/**
 * 备用源 girigiri 播放页:详情 + 选集 + 播放。
 * 时间表切换源后点番进入;控制:OK 播放/暂停,UP/DOWN 上下集,MENU 选集,BACK 返回。
 */
@Composable
fun GiriPlayScreen(
    giri: GirigiriApi,
    gvId: Long,
    title: String,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    var detail by remember { mutableStateOf<GirigiriApi.Detail?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var epIdx by remember { mutableIntStateOf(0) }       // 当前播放集下标
    var playing by remember { mutableStateOf(false) }
    var showEpList by remember { mutableStateOf(false) }
    var panelSel by remember { mutableIntStateOf(0) }
    var resolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val player = remember { PlayerFactory.create(ctx) }

    // 播放指定集(解析地址后播放)
    fun playEp(i: Int) {
        val d = detail ?: return
        if (i < 0 || i >= d.episodes.size) return
        epIdx = i
        showEpList = false
        resolving = true
        scope.launch {
            try {
                val url = giri.playUrl(d.episodes[i])
                resolving = false
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
                player.play()
                playing = true
            } catch (e: Exception) {
                resolving = false
                err = "播放失败: ${e.message}"
            }
        }
    }

    // 加载详情
    LaunchedEffect(gvId) {
        resolving = true
        try {
            detail = giri.detail(gvId)
            if (detail?.episodes?.isNotEmpty() == true) playEp(0)
            else err = "该番无播放集"
        } catch (e: Exception) {
            err = "加载失败: ${e.message}"
        }
        resolving = false
    }

    // 播放器监听
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) {
                playing = s == Player.STATE_READY || s == Player.STATE_BUFFERING
            }
            override fun onPlayerError(e: PlaybackException) {
                err = "播放出错: ${e.errorCodeName}\n${e.message?.take(120) ?: ""}"
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // 保持屏幕常亮
    DisposableEffect(Unit) {
        val act = ctx as? Activity
        act?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            act?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // 按键:选集弹层自管焦点;其他:BACK 返回,MENU 选集,OK 播放/暂停,UP/DOWN 上下集
    val mod = Modifier.onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
            showEpList -> when (e.key) {
                Key.Back, Key.Menu, Key.DirectionLeft -> { showEpList = false; true }
                Key.DirectionUp -> { panelSel = (panelSel - 1).coerceAtLeast(0); true }
                Key.DirectionDown -> { panelSel = (panelSel + 1).coerceAtMost((detail?.episodes?.size ?: 1) - 1); true }
                Key.DirectionCenter, Key.Enter, Key.Spacebar -> { playEp(panelSel); true }
                else -> false
            }
            e.key == Key.Back -> { onBack(); true }
            e.key == Key.Menu || e.key == Key.M -> { showEpList = true; true }
            e.key == Key.DirectionCenter || e.key == Key.Enter -> {
                if (player.isPlaying) { player.pause(); playing = false } else { player.play(); playing = true }
                true
            }
            e.key == Key.DirectionUp -> { playEp(epIdx - 1); true }
            e.key == Key.DirectionDown -> { playEp(epIdx + 1); true }
            else -> false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0E0E14)).then(mod)
    ) {
        // 播放器
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = false
                    // 禁止 PlayerView 抢焦点,否则按键事件不进入 Compose
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 加载中遮罩
        if (resolving) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Text("加载中…", color = TextMain, fontSize = 20.sp)
            }
        }

        // 错误
        err?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).clip(CycShape.panel).background(Color(0xE6121218)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("出错", color = Color(0xFFFF5A5A), fontSize = 18.sp)
                Text(msg, color = TextMain, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                Text("按 返回 退出", color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }

        // 顶部:标题 + 集号
        Column(Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 24.dp)) {
            Text(title, color = TextMain, fontSize = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { d ->
                Text(
                    if (d.episodes.isNotEmpty()) "第${epIdx + 1}集 / 共${d.episodes.size}集" else "",
                    color = TextDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 右侧选集弹层
        if (showEpList && detail != null) {
            val eps = detail!!.episodes
            val epListState = rememberLazyListState()
            LaunchedEffect(Unit) { epListState.scrollToItem(epIdx) }
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
                    Text("选集 · 备用源", color = TextMain, fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                    LazyColumn(state = epListState, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        itemsIndexed(eps) { i, _ ->
                            val sel = i == panelSel
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { playEp(i) }
                                    .height(46.dp)
                                    .clip(CycShape.chip)
                                    .background(if (sel) Primary else Surface)
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    (if (i == epIdx) "● " else "") + "第${i + 1}集",
                                    color = when { sel -> OnPrimary; i == epIdx -> Primary; else -> TextMain },
                                    fontSize = 15.sp, maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
