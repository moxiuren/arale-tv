package com.cycitv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme

/**
 * 色板 (2026-10-19 重建视觉系统)
 *
 * 上一版的问题: accent 被设成纯白, 全站没有任何品牌色, 所有高亮/选中/进度
 * 都只能靠白色表达 —— 信息层级塌缩成"白/灰"两档, 观众必须读字才能理解界面。
 * 本版引入樱花粉作为唯一强调色, 并把色温统一(侧栏曾硬编码 #131720 偏蓝,
 * 与底色 #0F1012 的中性黑并排明显发蓝)。
 *
 * 设计原则:
 *  1. 扁平   —— 纯色填充, 无渐变、无辉光、无柔和投影
 *  2. 品牌色 —— 樱花粉只用于"可交互/有状态"的位置(选中、进度、时间、焦点),
 *              绝不用作大面积背景, 保证它始终是信号而不是装饰
 *  3. 克制的粉 —— 用 #E888A8(花瓣) 而非 #F472B6(霓虹): 后者在近黑底上饱和度过高,
 *              长时间观看刺眼, 且易显廉价
 *  4. 明暗双模 —— 同一套语义槽位, 两套色值, 由 CycTheme(dark=...) 切换
 */
data class CycColors(
    val dark: Boolean,
    val backdrop: Color,
    val surface: Color,
    val surfaceHi: Color,
    /** 侧栏/导航栏底色 —— 必须与 backdrop 同色温, 仅明度略高 */
    val navSurface: Color,
    /** 主结构线(粗描边/选中框) */
    val line: Color,
    /** 细分隔线 */
    val lineSoft: Color,
    val textMain: Color,
    val textDim: Color,
    /** 品牌强调色(选中态/进度/时间/焦点) */
    val accent: Color,
    /** 强调底上的文字色 */
    val onAccent: Color,
    /** 品牌色的低透明填充(选中背景/进度条底) */
    val accentDim: Color,
    /** 状态: 已更新/已完结 */
    val ok: Color,
    /** 状态: 待更新/即将 */
    val warn: Color,
)

/** 暗色(默认): 近黑底 + 樱花粉强调 */
val CycDark = CycColors(
    dark = true,
    backdrop = Color(0xFF0F1012),
    surface = Color(0xFF191A1D),
    surfaceHi = Color(0xFF24262A),
    navSurface = Color(0xFF141317),   // 与 backdrop 同为中性, 仅略亮
    line = Color(0xFFE6E6E6),
    lineSoft = Color(0xFF33363B),
    textMain = Color(0xFFF2F2F2),
    textDim = Color(0xFF9BA1A8),
    accent = Color(0xFFE888A8),       // 花瓣粉: 深底对比度 7.9, 不刺眼
    onAccent = Color(0xFF1A1013),
    accentDim = Color(0x33E888A8),
    ok = Color(0xFF7ED9A8),           // 降饱和薄荷, 与粉同明度区间
    warn = Color(0xFFE8C87A),         // 降饱和琥珀
)

/** 亮色(白天): 纸白底 + 深粉强调 */
val CycLight = CycColors(
    dark = false,
    backdrop = Color(0xFFF7F6F5),
    surface = Color(0xFFFFFFFF),
    surfaceHi = Color(0xFFEDECEA),
    navSurface = Color(0xFFF1F0EE),
    line = Color(0xFF111111),
    lineSoft = Color(0xFFD5D5D0),
    textMain = Color(0xFF111111),
    textDim = Color(0xFF5C6066),
    accent = Color(0xFFB8456E),       // 亮底需要更深的粉才够对比
    onAccent = Color(0xFFFFFFFF),
    accentDim = Color(0x1FB8456E),
    ok = Color(0xFF15803D),
    warn = Color(0xFFA16207),
)

val LocalCycColors = staticCompositionLocalOf { CycDark }

/** 当前是否暗色(需要按明暗分支的地方用) */
val isCycDark: Boolean
    @Composable get() = LocalCycColors.current.dark

// ---------- 兼容既有代码的语义槽位(全部随主题切换) ----------

val Backdrop: Color
    @Composable get() = LocalCycColors.current.backdrop

val Surface: Color
    @Composable get() = LocalCycColors.current.surface

val SurfaceHi: Color
    @Composable get() = LocalCycColors.current.surfaceHi

/** 侧栏/导航栏底色: 与 backdrop 同色温, 不再硬编码偏蓝的 #131720 */
val NavSurface: Color
    @Composable get() = LocalCycColors.current.navSurface

val TextMain: Color
    @Composable get() = LocalCycColors.current.textMain

val TextDim: Color
    @Composable get() = LocalCycColors.current.textDim

val BorderSubtle: Color
    @Composable get() = LocalCycColors.current.lineSoft

/**
 * 结构强调色。
 *
 * 历史遗留命名: 原实现里它指向 line(白色), 导致全站焦点框、选中角标、
 * 进度标记一律是白色, 没有任何品牌识别度。现在它指向品牌樱花粉 ——
 * 19 处调用点(焦点框/角标/高亮)因此一次性获得统一强调色。
 */
val LineStrong: Color
    @Composable get() = LocalCycColors.current.accent

/** 保留原语义: 真正的"结构线"用这个(需要中性描边时用) */
val StructureLine: Color
    @Composable get() = LocalCycColors.current.line

val Primary: Color
    @Composable get() = LocalCycColors.current.accent

val OnMintText: Color
    @Composable get() = LocalCycColors.current.onAccent

val OnPrimary: Color
    @Composable get() = LocalCycColors.current.onAccent

val PrimaryDim: Color
    @Composable get() = LocalCycColors.current.accentDim

val MintPrimary: Color
    @Composable get() = LocalCycColors.current.accent

val MintPrimaryDim: Color
    @Composable get() = LocalCycColors.current.accentDim

val SakuraPink: Color
    @Composable get() = LocalCycColors.current.accent

val SakuraPinkDim: Color
    @Composable get() = LocalCycColors.current.accentDim

/** 聚焦描边笔刷: 纯色(扁平风格, 无渐变) */
val BrandGradient: Brush
    @Composable get() = SolidColor(LocalCycColors.current.accent)

/** 兼容占位: 扁平风格无辉光, 恒为透明 */
val BrandGlowColor: Color
    @Composable get() = Color.Transparent

/**
 * 双层主题:
 *  - 外层 TvMaterialTheme: 官方 TV 设计系统, 供 androidx.tv.material3 组件读取;
 *  - 内层 MaterialTheme: 供 androidx.compose.material3 组件读取;
 *  - CompositionLocalProvider 下发 CycColors, 供自有组件按语义取色。
 *
 * @param dark true=暗色(默认), false=白天模式
 */
@Composable
fun CycTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val c = if (dark) CycDark else CycLight

    val tvScheme = if (dark) {
        tvDarkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.ok,
            onSecondary = c.onAccent,
            background = c.backdrop,
            onBackground = c.textMain,
            surface = c.surface,
            onSurface = c.textMain,
            surfaceVariant = c.surfaceHi,
            onSurfaceVariant = c.textDim,
            border = c.line,
            scrim = Color.Black,
        )
    } else {
        tvLightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.ok,
            onSecondary = c.onAccent,
            background = c.backdrop,
            onBackground = c.textMain,
            surface = c.surface,
            onSurface = c.textMain,
            surfaceVariant = c.surfaceHi,
            onSurfaceVariant = c.textDim,
            border = c.line,
            scrim = Color.Black,
        )
    }

    val m3Scheme = if (dark) {
        darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.ok,
            onSecondary = c.onAccent,
            background = c.backdrop,
            onBackground = c.textMain,
            surface = c.surface,
            onSurface = c.textMain,
            surfaceVariant = c.surfaceHi,
            onSurfaceVariant = c.textDim,
        )
    } else {
        lightColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            secondary = c.ok,
            onSecondary = c.onAccent,
            background = c.backdrop,
            onBackground = c.textMain,
            surface = c.surface,
            onSurface = c.textMain,
            surfaceVariant = c.surfaceHi,
            onSurfaceVariant = c.textDim,
        )
    }

    CompositionLocalProvider(LocalCycColors provides c) {
        TvMaterialTheme(colorScheme = tvScheme) {
            MaterialTheme(colorScheme = m3Scheme, content = content)
        }
    }
}

/**
 * 全站统一形状梯度 (2026-10-19 重建)
 *
 * 上一版把所有圆角压成 0dp, 理由是"四方扁平更统一"。实际效果相反:
 * 当所有容器都是同一个直角时, 卡片、按钮、角标、面板之间失去形状区分,
 * 只能靠描边粗细区分层级 —— 这是界面显得"机械、像模板生成"的直接原因之一。
 *
 * 本版按元素尺寸给圆角, 尺寸越小圆角越小(视觉重量守恒):
 *   badge(小角标) < chip < button < card < panel
 * 装饰性细条(分隔线/进度条)保持直角 —— 它们不是容器, 圆角会显得脏。
 */
object CycShape {
    /** 按钮 */
    val button = RoundedCornerShape(10.dp)
    /** 卡片 / 海报单元 */
    val card = RoundedCornerShape(12.dp)
    /** 标签 / Chip / 输入框 */
    val chip = RoundedCornerShape(8.dp)
    /** 小角标(评分/进度/话数) */
    val badge = RoundedCornerShape(4.dp)
    /** 大面板(侧栏项/设置块) */
    val panel = RoundedCornerShape(14.dp)
    /** 装饰细条(分隔线/进度条底) —— 保持直角 */
    val bar = RoundedCornerShape(0.dp)
}

/** TV 官方 Button 的统一形状(ButtonShape 包装) */
@Composable
fun cycButtonShape(): androidx.tv.material3.ButtonShape =
    androidx.tv.material3.ButtonDefaults.shape(
        shape = CycShape.button,
        focusedShape = CycShape.button,
        pressedShape = CycShape.button,
    )
