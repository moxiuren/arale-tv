package com.cycitv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cycitv.ui.components.LocalAppImageLoader
import com.cycitv.ui.nav.NavController
import com.cycitv.ui.nav.PlayIntent
import com.cycitv.ui.nav.Screen
import com.cycitv.ui.nav.rememberNavController
import com.cycitv.ui.screens.CategoryScreen
import com.cycitv.ui.screens.DetailScreen
import com.cycitv.ui.screens.GiriPlayScreen
import com.cycitv.ui.screens.HomeScreen
import com.cycitv.ui.screens.PlayScreen
import com.cycitv.ui.screens.ScheduleScreen
import com.cycitv.ui.screens.SearchScreen
import com.cycitv.ui.screens.SettingsScreen
import com.cycitv.ui.theme.SurfaceHi
import com.cycitv.ui.theme.BorderSubtle
import com.cycitv.ui.theme.CycShape
import com.cycitv.ui.theme.CycTheme
import com.cycitv.ui.theme.NavSurface
import com.cycitv.ui.theme.Primary
import com.cycitv.ui.theme.Surface
import com.cycitv.ui.theme.TextDim
import com.cycitv.ui.theme.TextMain

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sec = intent?.getIntExtra("play_section", 0) ?: 0
        PlayIntent = if (sec > 0) {
            Screen.Play(
                videoId = (intent.getIntExtra("play_video", 0) ?: 0).toLong(),
                sectionId = sec.toLong(),
                playerCode = intent.getStringExtra("play_code") ?: "cychub",
                animeTitle = intent.getStringExtra("play_title") ?: "",
                directUrl = intent.getStringExtra("play_url"),
            )
        } else if (intent.getIntExtra("go_settings", 0) > 0) Screen.Settings
        else if (intent.getIntExtra("go_schedule", 0) > 0) Screen.Schedule else null

        setContent {
            val cycApp = applicationContext as App
            val cycDark by cycApp.darkTheme.collectAsState()
            CycTheme(dark = cycDark) {
                val app = LocalContext.current.applicationContext as App
                val nav = rememberNavController()
                val screen by nav.current
                CompositionLocalProvider(LocalAppImageLoader provides app.imageLoader) {

                    BackHandler(enabled = screen !is Screen.Home) {
                        if (!nav.back()) finish()
                    }

                    if (screen is Screen.Play) {
                        val p = screen as Screen.Play
                        PlayScreen(
                            videoId = p.videoId,
                            initialSectionId = p.sectionId,
                            playerCode = p.playerCode,
                            animeTitle = p.animeTitle,
                            initialSections = p.sections,
                            directUrl = p.directUrl,
                            onExit = { if (!nav.back()) finish() },
                        )
                    } else {
                        Row(Modifier.fillMaxSize()) {
                            SideNav(
                                selected = screen,
                                onSelect = { nav.open(it) },
                                modifier = Modifier.width(180.dp)
                            )
                            Box(Modifier.weight(1f).background(com.cycitv.ui.theme.Backdrop)) {
                                androidx.compose.animation.AnimatedContent(
                                    targetState = screen,
                                    transitionSpec = {
                                        (fadeIn(tween(250)) +
                                            scaleIn(
                                                initialScale = 0.98f,
                                                animationSpec = tween(250)
                                            ))
                                            .togetherWith(fadeOut(tween(200)))
                                    },
                                    label = "screen"
                                ) { s ->
                                    when (s) {
                                        is Screen.Home -> HomeScreen(
                                            app.home,
                                            onOpen = { nav.open(Screen.Detail(it)) },
                                            // 继续观看: 直接进播放页。playerCode 传空串,
                                            // PlayScreen 会发现它不在线路列表里, 自动回落到第一条可用线路
                                            onResume = { h ->
                                                nav.open(
                                                    Screen.Play(
                                                        videoId = h.animeId,
                                                        sectionId = h.sectionId,
                                                        playerCode = "",
                                                        animeTitle = h.title,
                                                    )
                                                )
                                            },
                                        )
                                        is Screen.Category -> CategoryScreen(app.api, onOpen = { nav.open(Screen.Detail(it)) })
                                        is Screen.Schedule -> ScheduleScreen(
                                            app.api,
                                            onOpen = { nav.open(Screen.Detail(it)) },
                                        )
                                        is Screen.Search -> SearchScreen(app.api, onOpen = { nav.open(Screen.Detail(it)) })
                                        is Screen.Settings -> SettingsScreen()
                                        is Screen.Detail -> DetailScreen(
                                            app.api,
                                            (s as Screen.Detail).videoId,
                                            onPlay = { vid, sid, code, title, secs ->
                                                nav.open(Screen.Play(vid, sid, code, title, secs))
                                            },
                                            onBack = { nav.back() },
                                        )
                                        is Screen.Play -> Unit
                                        is Screen.GiriPlay -> GiriPlayScreen(
                                            app.giri,
                                            (s as Screen.GiriPlay).gvId,
                                            (s as Screen.GiriPlay).title,
                                            onBack = { nav.back() },
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
}

private data class NavItemData(
    val label: String,
    val screen: Screen,
    val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItemData("首页", Screen.Home, Icons.Rounded.Home),
    NavItemData("分类", Screen.Category, Icons.Rounded.GridView),
    NavItemData("时间表", Screen.Schedule, Icons.Rounded.CalendarMonth),
    NavItemData("搜索", Screen.Search, Icons.Rounded.Search),
    NavItemData("设置", Screen.Settings, Icons.Rounded.Settings),
)

@Composable
private fun SideNav(
    selected: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            // 底色改走主题槽位: 原先硬编码 #131720 偏蓝, 与主区 #0F1012 的中性黑
            // 并排时左右色温割裂, 是一眼"不专业"的主要来源
            .background(NavSurface)
            .border(width = 1.dp, color = BorderSubtle)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 品牌字标: 纯文字排版, 去掉圆形插画头像与 10sp 副标题。
            // 原设计的头像 + "MewType" 是手机尺寸的排版, 电视 3 米距离下
            // 10sp 等于噪点, 彩色圆形插画在纯黑侧栏上也与扁平体系冲突。
            // 改为: 加大字标 + 一条品牌色短线做视觉锚点。
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "阿拉蕾",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        color = TextMain,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        "TV",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        color = Primary,
                        letterSpacing = 0.5.sp,
                    )
                }
                // 品牌色短线: 替代原来那行 10sp 小字, 提供品牌识别又不制造噪点
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .width(28.dp)
                        .height(3.dp)
                        .background(Primary, RoundedCornerShape(2.dp))
                )
            }

            // 导航: 圆角胶囊项, 间距替代细线分隔
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                NAV_ITEMS.forEach { item ->
                    val active = item.screen::class == selected::class &&
                        !(selected is Screen.Detail || selected is Screen.Play || selected is Screen.GiriPlay)
                    var focused by remember { mutableStateOf(false) }
                    val bg = when {
                        focused -> Primary.copy(alpha = 0.18f)
                        active -> SurfaceHi
                        else -> Color.Transparent
                    }
                    val fg = when {
                        focused -> Primary
                        active -> TextMain
                        else -> TextDim
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .clip(CycShape.chip)
                            .background(bg)
                            .then(
                                if (focused) Modifier.border(2.dp, Primary, CycShape.chip)
                                else Modifier
                            )
                            .clickable { onSelect(item.screen) }
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = fg,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            item.label,
                            fontSize = 15.sp,
                            fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium,
                            color = fg
                        )
                    }
                }
            }
        }

    }
}
