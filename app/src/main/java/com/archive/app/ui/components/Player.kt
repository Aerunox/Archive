package com.archive.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.video.videoFramePercent
import com.archive.core.api.ApiClient
import com.archive.app.ui.LocalSettingsManager
import com.archive.app.ui.design.AppSpinner
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalBlurThumbnails
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.util.BlurTransformation
import kotlinx.coroutines.delay

/**
 * 媒体源工厂：所有请求（含 HLS 的 m3u8 与分片）都走带会话与 Cloudflare 凭据的
 * OkHttp，站点对 file/t1 等子域只认应用专属 UA，用默认 HTTP 栈会被 403。
 *
 * 站点未导入原文件的视频（deferred）给的是 HLS 临时流，因此必须挂 HLS 数据源，
 * 否则 DefaultMediaSourceFactory 认不出 m3u8。
 */
private fun buildMediaSourceFactory(): DefaultMediaSourceFactory =
    DefaultMediaSourceFactory(OkHttpDataSource.Factory(ApiClient.sharedOkHttpClient))

/**
 * 视频元数据缓存（进程内）：播放器拿到 metadata 后写入，列表里的海报格据此
 * 显示「0:10」角标，并按视频真实画面比例决定格子形状（竖屏视频就是竖格子）。
 */
object VideoMetas {
    private val durations = mutableStateMapOf<String, Long>()
    private val aspects = mutableStateMapOf<String, Float>()

    fun duration(url: String): Long? = durations[url]?.takeIf { it > 0 }

    fun aspect(url: String): Float? = aspects[url]?.takeIf { it > 0.05f }

    fun put(url: String, durationMs: Long) {
        if (durationMs > 0) durations[url] = durationMs
    }

    fun putAspect(url: String, width: Int, height: Int) {
        if (width > 0 && height > 0) aspects[url] = width.toFloat() / height.toFloat()
    }
}

/** 进程内的续播位置（按 URL 记录，与 Pawchive 的 videoResumePositions 同思路）。 */
object VideoResumePositions {
    private val positions = HashMap<String, Long>()

    fun get(url: String): Long = positions[url] ?: 0L

    fun save(url: String, position: Long) {
        if (position > 3_000L) positions[url] = position else positions.remove(url)
    }
}

/**
 * X 风格的视频海报格（对照参考图里带视频的帖子）：
 * 圆角黑底 + 视频首帧封面 + 居中播放三角 + 右下角时长角标（0:10）。
 *
 * 首帧用 Coil 的 [videoFramePercent] 解码：直链 mp4 能出画面，
 * HLS 临时流解不出来就留在黑底上（站点对 deferred 视频也不给缩略图）。
 * 这里不持有播放器，一次渲染多个格子没有额外的网络与解码开销。
 */
@Composable
fun VideoPosterCell(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    defaultAspectRatio: Float = 16f / 9f,
    /** 是否跟随「模糊缩略图」开关；查看器里（已明确要看）可传 false。 */
    blurredOverride: Boolean? = null,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val duration = VideoMetas.duration(url)
    // 视频首帧属于「内容本身」：模糊开关开启时同样要糊（webm 动图尤其如此），
    // 否则帖子页的视频行会直接透出画面内容 —— 静态封面糊了、视频行却是清晰的，很怪。
    val blur = blurredOverride ?: LocalBlurThumbnails.current
    // 播放过一次后就知道真实比例：竖屏视频显示成竖格子，但不做成长条（夹在 3:4 ~ 16:9）
    val aspect = (VideoMetas.aspect(url) ?: defaultAspectRatio).coerceIn(0.75f, 16f / 9f)
    Box(
        modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 直链 mp4 才去解首帧：HLS（.m3u8）解不出来，还会白白向站点多发一次请求
        if (!url.contains(".m3u8", ignoreCase = true)) {
            AsyncImage(
                model = remember(url, blur) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .videoFramePercent(0.0)
                        .apply { if (blur) transformations(BlurTransformation()) }
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Icon(
            imageVector = LucideIcons.play,
            contentDescription = contentDescription ?: "播放视频",
            tint = Color.White,
            modifier = Modifier.size(46.dp),
        )
        if (duration != null) {
            Text(
                formatPlayerTime(duration),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * 统一媒体查看器里的**视频页**（自带画面与播放控件）：
 *
 * ```
 *                   [   视频   ]                        ← 居中，等比适配（FIT）
 *
 *  (⏸) ────────────────────────────                  ← 播放/暂停 + 细进度条
 *                                 0:04 / 17:27        ← 时间右对齐
 * ```
 *
 * 只有当前页（[active]）才持有 ExoPlayer：翻走即离开组合并释放播放器，播放位置留在
 * [VideoResumePositions]，翻回来按设置里的续播开关接着播 —— 因此任意时刻只有一个实例。
 * 倍速由调用方（查看器的 ⋯ 菜单）经 [speed] 下发，播放中改变即时生效。
 *
 * 站点对 file / t1 等子域只认应用专属 UA，媒体源一律走 [buildMediaSourceFactory]。
 */
@Composable
internal fun ViewerVideoPane(
    url: String,
    active: Boolean,
    speed: Float,
    modifier: Modifier = Modifier,
    /** 是否处于全屏播放（由调用方持有状态：全屏时要同时收起查看器的顶栏）。 */
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
) {
    // 非当前页不持有播放器：给首帧（mp4）或黑底（HLS）占位，落定成当前页后才创建
    if (!active) {
        VideoPagePlaceholder(url, modifier)
        return
    }

    val context = LocalContext.current
    val settingsManager = LocalSettingsManager.current
    val colors = LocalPurrColors.current

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

    // 当前页才存在这个实例：翻走即释放（并记住播放位置），翻回来重新创建
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(buildMediaSourceFactory())
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                setPlaybackSpeed(speed)
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                val total = player.duration.coerceAtLeast(0L)
                duration = total
                if (total > 0) VideoMetas.put(url, total)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                VideoMetas.putAspect(url, videoSize.width, videoSize.height)
            }
        }
        player.addListener(listener)
        onDispose {
            VideoResumePositions.save(url, player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    // 禁用后台播放：应用退到后台（Home / 最近任务 / 锁屏）时立刻暂停。
    //
    // 为什么不能只靠上面那个 DisposableEffect：退到后台时本组件**没有离开组合**，
    // release 根本不会执行，声音会一直响下去（这也正是「后台还能听到视频」的原因）。
    // 这里盯的是 STARTED 生命周期 —— 界面彻底不可见就暂停。
    // 刻意不做「回前台自动续播」：由用户决定是否继续看，避免一解锁就自己响起来。
    // 用 STARTED 而不是 RESUMED：弹权限框 / 系统对话框之类不会误暂停。
    LifecycleStartEffect(player) {
        onStopOrDispose { player.pause() }
    }

    LaunchedEffect(player) {
        if (settingsManager?.isVideoResumeEnabled() == true) {
            val saved = VideoResumePositions.get(url)
            if (saved > 0) player.seekTo(saved)
        }
    }

    // 倍速由查看器的 ⋯ 菜单下发：播放中改变即时生效
    LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }

    LaunchedEffect(player, dragging) {
        while (true) {
            if (!dragging) position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val activity = remember(context) { context.findActivity() }
        // 系统栏显隐控制器（全屏时隐藏状态栏/导航栏，退出时恢复）
        val insetsController = remember(activity) {
            activity?.window?.let { window -> WindowCompat.getInsetsController(window, window.decorView) }
        }
        // 是否全屏过：决定「退出全屏」时是明确要求竖屏，还是继续交给设备传感器
        val enteredFullscreenOnce = remember { mutableStateOf(false) }
        LaunchedEffect(fullscreen) {
            if (fullscreen) enteredFullscreenOnce.value = true
        }

        DisposableEffect(fullscreen) {
            // 方向策略（这里踩过坑，注释留个底）：
            // - 首次进入本页：跟随设备方向 —— 竖屏视频竖着看，宽视频把手机横过来即可；
            // - 进入全屏：**锁横屏**，显式请求，不受系统「自动旋转」开关影响；
            // - 退出全屏：**显式要求竖屏**。之前这里只把方向交回 SENSOR，在系统自动旋转
            //   关闭的机型（如 MIUI）上屏幕会一直停在横屏 —— 表现为「全屏按钮点了没反应、
            //   退不回竖屏」。显式要竖屏才能保证一定转回来。
            // - 翻走 / 离开本页：方向交还系统（见下面 onDispose；非当前页时本组件整体
            //   走 early-return，状态一并丢弃，重新回到本页又会从「跟随设备方向」开始）。
            activity?.requestedOrientation =
                videoOrientationFor(fullscreen, enteredFullscreenOnce.value)
            insetsController?.let { controller ->
                if (fullscreen) {
                    // 从边缘下滑可临时唤出系统栏，不打断播放
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose { }
        }

        // 离开本页（翻走或关掉查看器）时彻底还原：方向交还系统、系统栏恢复。
        // 少了这一步，横屏会被带回帖子页 —— 视频只是查看器里的一页，随时可能被销毁。
        DisposableEffect(Unit) {
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // 兜底：主题切换等场景会重新调用 enableEdgeToEdge（它重新配置窗口），
        // 可能把系统栏又显示出来。全屏期间每次组合后都幂等地再隐藏一次。
        if (fullscreen) {
            SideEffect { insetsController?.hide(WindowInsetsCompat.Type.systemBars()) }
        }
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    // 只避让底部导航栏：顶部已由查看器的常驻顶栏让出高度，不再叠加顶部内边距。
                    // 全屏时导航栏已隐藏，不避让 —— 画面与控制条一直贴到底。
                    .padding(
                        bottom = if (fullscreen) {
                            0.dp
                        } else {
                            WindowInsets.navigationBars
                                .asPaddingValues()
                                .calculateBottomPadding()
                        },
                    ),
            ) {
                // ── 视频画面（占满可用区域，由 PlayerView 的 FIT 等比适配）──
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                this.player = player
                            }
                        },
                        update = { it.player = player },
                        onRelease = { view -> view.player = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (buffering) {
                        AppSpinner(size = 36.dp, strokeWidth = 3.dp, color = Color.White)
                    }
                }

                // ── 控制条：播放/暂停 + 细进度条，时间右对齐在下方 ──
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerCircleButton(
                        if (isPlaying) LucideIcons.pause else LucideIcons.play,
                        if (isPlaying) "暂停" else "播放",
                    ) { if (isPlaying) player.pause() else player.play() }
                    ThinSeekBar(
                        position = position,
                        duration = duration,
                        accent = colors.primary,
                        onSeek = { fraction ->
                            dragging = true
                            position = (fraction * duration).toLong()
                        },
                        onSeekFinished = {
                            player.seekTo(position)
                            dragging = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    )
                    // 全屏：切横屏 + 隐藏系统栏与查看器顶栏；再点一次原样还原。
                    // 放在控制条右端，与播放键分列两端，单手够得着。
                    PlayerCircleButton(
                        icon = if (fullscreen) LucideIcons.collapse else LucideIcons.expand,
                        contentDescription = if (fullscreen) "退出全屏" else "全屏播放",
                        onClick = onToggleFullscreen,
                    )
                }
                Text(
                    "${formatPlayerTime(position)} / ${formatPlayerTime(duration)}",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp, top = 2.dp),
                )
            }
        }
    }
}

/**
 * 视频页在「不是当前页」时的占位画面：能解首帧的直链 mp4 显示首帧，
 * HLS 临时流解不出首帧就留黑底。
 *
 * 这里**只读 Coil 缓存**（命中就出画面，命中不了就黑底）：解视频首帧要从站点下载
 * 一段数据，而当前页正有一支视频在播/在缓冲，不能让它抢带宽。帖子里的海报格才是
 * 真正去取首帧的地方。
 */
@Composable
private fun VideoPagePlaceholder(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // .m3u8 解不出首帧，还会白跑一次请求
        if (!url.contains(".m3u8", ignoreCase = true)) {
            AsyncImage(
                model = remember(url) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .videoFramePercent(0.0)
                        .networkCachePolicy(CachePolicy.READ_ONLY)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 细进度条（对照 X：一条细线，可点可拖）。
 * 可视高度只有 2dp，外层留 28dp 触控区，手指好按。
 */
@Composable
private fun ThinSeekBar(
    position: Long,
    duration: Long,
    accent: Color,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val fraction = if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                    onSeekFinished()
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onSeek((offset.x / widthPx).coerceIn(0f, 1f)) },
                    onDragEnd = { onSeekFinished() },
                ) { change, _ ->
                    onSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.3f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .background(accent),
        )
    }
}

/** 播放器里的圆形浮动按钮（白 18% 底 + 白色图标，48dp 触控区）。 */
@Composable
private fun PlayerCircleButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** 简易音频播放器：播放/暂停 + 名称 + 进度时间。 */
@Composable
fun AudioPlayer(
    name: String,
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = LocalPurrColors.current
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(buildMediaSourceFactory())
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // 同上：音频附件同样不允许退到后台继续响（应用里没有后台播放/通知栏控制这回事，
    // 留着就只是「看不见还在出声」，一并按同一规则暂停）
    LifecycleStartEffect(player) {
        onStopOrDispose { player.pause() }
    }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { if (isPlaying) player.pause() else player.play() },
            contentAlignment = Alignment.Center,
        ) {
            if (buffering) {
                AppSpinner(size = 20.dp, color = Color.White)
            } else {
                Icon(
                    imageVector = if (isPlaying) LucideIcons.pause else LucideIcons.play,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            AppText(name, style = AppTextStyle.MAIN, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    formatPlayerTime(position),
                    style = AppTextStyle.SMALL,
                    color = colors.textTertiary,
                )
                AppText(
                    " / ${formatPlayerTime(duration)}",
                    style = AppTextStyle.SMALL,
                    color = colors.textTertiary,
                )
            }
        }
    }
}

/**
 * 视频页的方向策略 —— 「当前该向系统请求哪个方向」。
 *
 * 抽成纯函数是因为这里踩过坑，且坑很难在设备上复现：
 *
 * - 首次进入本页（既没全屏过）：[ActivityInfo.SCREEN_ORIENTATION_SENSOR]，跟随设备方向 ——
 *   竖屏视频竖着看，宽视频把手机横过来即可；
 * - 进入全屏：[ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE]，显式锁横屏，不受系统
 *   「自动旋转」开关影响；
 * - **退出全屏：[ActivityInfo.SCREEN_ORIENTATION_PORTRAIT]，显式要求竖屏。**
 *   这一条是修复的关键：早先这里只把方向交回 SENSOR，在系统自动旋转关闭的机型
 *   （如 MIUI）上屏幕会一直停在横屏 —— 表现为「再点全屏按钮没反应、退不回竖屏」，
 *   而那其实不是按钮坏了，是屏幕没转回来；
 * - 从未全屏过就不该强制竖屏，否则会顺手干掉「把手机横过来看宽视频」这个既有能力。
 *
 * @param fullscreen 当前是否处于全屏
 * @param enteredFullscreenOnce 本次进入本页后是否全屏过（非当前页时组件整体重建，会重置）
 */
internal fun videoOrientationFor(fullscreen: Boolean, enteredFullscreenOnce: Boolean): Int = when {
    fullscreen -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    enteredFullscreenOnce -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
}

/**
 * 可选倍速档位 —— 播放器悬浮菜单与倍速文案共用同一份，避免两处列表漂移。
 *
 * 0.75x / 1.25x 这类细档位也留着：倍速是**实时**下发的（见 `LaunchedEffect(player, speed)`），
 * 点一下立刻生效，用户可以连点对比着挑。
 */
internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

/** 倍速倍率文案（选项按钮用）：始终是「1.0x」这样的倍率，而不是「倍速」这种相对说法。 */
internal fun speedRateLabel(speed: Float): String =
    if (speed in PLAYBACK_SPEEDS) "${speed}x" else "%.2fx".format(speed)

private fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
