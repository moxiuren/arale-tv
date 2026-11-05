package com.cycitv.util

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter

/**
 * 播放器工厂 —— 针对 XGIMI Z6X Pro 2025（海思 Hi3751V660 / 32 位用户空间 / 2GB RAM）调优。
 *
 * 目标设备的硬约束（实测）：
 * - 内核 armv8l，但用户空间只有 armeabi-v7a（`ro.product.cpu.abilist64` 为空，无 /system/lib64）
 *   → 所有代码跑 32 位 ART，指针 4 字节、地址空间 4GB、堆上限远低于 64 位机型
 * - MemTotal 1,830,856 KB，常驻系统进程吃掉大半，可用常年在 500–700 MB 间
 *   → 视频缓冲不能按默认 50 s 配，否则播放器自己就能吃掉上百 MB
 *
 * 因此这里做了三件事：
 * 1. LoadControl 按「字节优先」而非「时长优先」裁剪：上限 40 s、起播 15 s、回看 8 s
 * 2. 关闭 float 音频输出，省掉一次 PCM 格式转换
 * 3. 保留 decoder fallback + 强制同步 MediaCodec 队列（海思/MStar 系对 C2 异步队列
 *    DynamicANWBuffer 适配有 bug，个别流 configure 阶段就崩）
 */
object PlayerFactory {

    /** 国内 CDN 对默认 ExoPlayer UA 有拦截，伪装桌面 Chrome */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun create(
        ctx: Context,
        software: Boolean = false,
        bandwidthMeter: DefaultBandwidthMeter? = null,
    ): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(ctx).apply {
            setEnableDecoderFallback(true)
            // 16-bit PCM 直出，避免 float 中间缓冲（32 位进程里的额外分配）
            setEnableAudioFloatOutput(false)
            // 海思/MStar 硬解对 C2 异步队列适配有 bug，强制同步队列
            forceDisableMediaCodecAsynchronousQueueing()
            if (software) {
                setMediaCodecSelector(object : MediaCodecSelector {
                    override fun getDecoderInfos(
                        mimeType: String,
                        requiresSecureDecoder: Boolean,
                        requiresTunnelingDecoder: Boolean,
                    ): List<MediaCodecInfo> {
                        val all = try {
                            MediaCodecUtil.getDecoderInfos(
                                mimeType, requiresSecureDecoder, requiresTunnelingDecoder
                            )
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (MimeTypes.VIDEO_H264 == mimeType) {
                            // 个别流会打崩厂商硬解，仅保留 Google 软解兜底
                            return all.filter {
                                it.name.contains("c2.android.avc") || it.name.contains("OMX.google.h264")
                            }
                        }
                        return all
                    }
                })
            }
        }

        // 2GB 设备：上限 40s（默认 50s），且优先按字节阈值限制而不是时长
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs：起播所需缓冲，降低首屏等待
                40_000, // maxBufferMs：总缓冲上限，压低常驻内存
                1_000,  // bufferForPlaybackMs：就绪后 1s 即可开播
                2_500,  // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBackBuffer(8_000, true)
            .build()

        return ExoPlayer.Builder(ctx, renderersFactory)
            .setLoadControl(loadControl)
            .apply { if (bandwidthMeter != null) setBandwidthMeter(bandwidthMeter) }
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(ctx).setDataSourceFactory(
                    DefaultHttpDataSource.Factory()
                        // 国内 CDN 首字节慢（实测 ~8.4s），默认 8s 会陷入无限重试
                        .setConnectTimeoutMs(20_000)
                        .setReadTimeoutMs(20_000)
                        .setUserAgent(USER_AGENT)
                        .setAllowCrossProtocolRedirects(true)
                )
            )
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }
}
