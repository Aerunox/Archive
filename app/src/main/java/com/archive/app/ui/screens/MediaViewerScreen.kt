package com.archive.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFramePercent
import com.archive.core.api.ApiClient
import com.archive.data.repository.DownloadEnqueuer
import com.archive.app.data.remote.UrlProvider
import com.archive.app.data.repo.PostRepository
import com.archive.app.domain.Attachment
import com.archive.app.data.forPurpose
import com.archive.app.ocr.OcrService
import com.archive.app.domain.PostDetail
import com.archive.app.ui.LocalAppSettings
import com.archive.app.ui.LocalSettingsManager
import com.archive.app.ui.components.PLAYBACK_SPEEDS
import com.archive.app.ui.components.ThumbImage
import com.archive.app.ui.components.ViewerImageSheetHost
import com.archive.app.ui.components.ViewerMedia
import com.archive.app.ui.components.ViewerVideoPane
import com.archive.app.ui.components.speedRateLabel
import com.archive.app.ui.components.viewerMedia
import com.archive.app.ui.design.AppDivider
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppSmallTitle
import com.archive.app.ui.design.AppSpinner
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import okhttp3.Request

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val postRepository: PostRepository,
    private val downloadEnqueuer: DownloadEnqueuer,
    private val ocr: OcrService,
    private val vision: com.archive.app.data.VisionClient,
    private val translationSettings: com.archive.app.data.TranslationSettingsRepository,
) : ViewModel() {

    val service: String = savedStateHandle.get<String>("service") ?: ""
    val creatorId: String = savedStateHandle.get<String>("creatorId") ?: ""
    val postId: String = savedStateHandle.get<String>("postId") ?: ""

    val detail = MutableStateFlow<PostDetail?>(null)
    val error = MutableStateFlow<String?>(null)

    /** 原图探测结果缓存（url -> Content-Length；null = 站点没有这张原图）。 */
    private val originalSizes = LinkedHashMap<String, Long?>()
    private val probeLock = Mutex()

    init {
        // 秒开：帖子页刚取到的同一份详情直接拿来渲染，媒体立刻开始加载，不等网络往返
        postRepository.lastDetail
            ?.takeIf { it.service == service && it.creatorId == creatorId && it.postId == postId }
            ?.let { detail.value = it }
        viewModelScope.launch {
            runCatching { postRepository.detail(service, creatorId, postId) }
                .onSuccess { detail.value = it }
                .onFailure { if (detail.value == null) error.value = it.message ?: "加载失败" }
        }
    }

    /**
     * 探测原图是否可取以及大小（HEAD 取 Content-Length）。
     *
     * 走 [ApiClient.sharedOkHttpClient]：file 子域只认应用专属 UA，换默认 HTTP 栈会 403；
     * 站点未导入原图（preview_only）时返回 404，据此把「查看原图 3MB」换成「仅预览」。
     */
    suspend fun probeOriginalSize(url: String): Long? = probeLock.withLock {
        if (originalSizes.containsKey(url)) return@withLock originalSizes[url]
        val size = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).head().build()
                ApiClient.sharedOkHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        null
                    } else {
                        response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                    }
                }
            }
        }.getOrNull()
        originalSizes[url] = size
        size
    }

    /** 保存当前媒体：图片进图片队列，视频进视频队列（deferred 视频走临时直链）。 */
    fun download(item: ViewerMedia, baseUrl: String) {
        val attachment = item.attachment
        val url = UrlProvider.downloadUrl(baseUrl, attachment.path, attachment.tempDownloadUrl) ?: return
        val name = attachment.displayName()
        val ext = name.substringAfterLast('.', "").lowercase()
        viewModelScope.launch {
            runCatching {
                when (item) {
                    is ViewerMedia.Image ->
                        downloadEnqueuer.enqueueImageDownload(
                            url,
                            name,
                            if (ext in IMAGE_EXTS) "image/$ext" else "application/octet-stream",
                        )

                    is ViewerMedia.Video ->
                        downloadEnqueuer.enqueueVideoDownload(
                            url,
                            name,
                            if (ext in VIDEO_EXTS) "video/$ext" else "video/mp4",
                        )
                }
            }
        }
    }

    /**
     * 识别一张图片里的文字（菜单里的「提取文字」）。
     * OCR 引擎在 [OcrService] 里首次使用时才加载模型，之后复用。
     */
    suspend fun recognizeText(url: String): List<String> =
        ocr.recognize(url).map { line -> line.text }

    // ── 图片理解（菜单里的「AI 摘要」与「图片问答」）──

    /** 视觉模型配置：跟随设置页变化，改完立即生效。 */
    private var aiSettings: com.archive.app.data.TranslationSettings =
        com.archive.app.data.TranslationSettings()

    init {
        viewModelScope.launch { translationSettings.settings.collect { aiSettings = it } }
    }

    /**
     * 「AI 摘要」：把当前这张图交给视觉模型概括。
     * 失败直接抛出交给界面显示（摘要与问答各有自己的错误位）。
     */
    suspend fun summarizeImage(url: String): String =
        vision.summarize(url, aiSettings.forPurpose(com.archive.app.data.AiPurpose.VISION))
            .getOrThrow()

    /**
     * 「图片问答」：带着已有历史追问。[history] 由界面持有并逐轮回传 ——
     * 多模态接口本身不保存会话，模型要靠这段历史才记得前面问过什么。
     */
    suspend fun askImage(
        url: String,
        history: List<com.archive.app.data.AiTurn>,
        question: String,
    ): String = vision.ask(
        url = url,
        history = history,
        question = question,
        settings = aiSettings.forPurpose(com.archive.app.data.AiPurpose.VISION),
    ).getOrThrow()

    private companion object {
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val VIDEO_EXTS = setOf("mp4", "webm", "mov", "mkv", "m4v")
    }
}

/**
 * 统一媒体查看器：一个帖子的**图片与视频混在同一个分页里**，左右滑动就是
 * 「上一个 / 下一个媒体文件」，两种媒体之间可以连续滑过去。
 *
 * ```
 *  (←)               1 / 9                    (⋯)      ← 返回 / 页码 / 更多
 *
 *              [   图片 / 视频   ]                     ← 统一分页
 *
 *  图片页：[查看原图 3MB]        (↪) (↓) (⊞)          ← 原图胶囊 + 分享/保存/宫格
 *  视频页：(⏸) ─────────────   0:04 / 17:27           ← 播放控件在视频页内
 * ```
 *
 * - 媒体顺序 = 帖子的附件顺序（[viewerMedia]），只有能看的进列表：图片必须有 path，
 *   视频必须有可播放地址且临时链接未过期；其余仍在帖子正文里以附件行 + 提示展示；
 * - 图片页是「缩略图垫底 + 原图叠加」+ 双指缩放 + 长按操作窗口；
 * - 视频页只有成为当前页才创建播放器（[ViewerVideoPane]），翻走即释放并记住播放位置；
 * - 倍速在 ⋯ 菜单里（视频页生效，按设置记忆）。
 */
@Composable
fun MediaViewerScreen(
    navController: NavHostController,
    service: String,
    creatorId: String,
    postId: String,
    initialIndex: Int,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val post = detail
        when {
            error != null -> Text(
                error ?: "",
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )

            post == null -> AppLoading()

            else -> {
                val items = remember(post, settings.baseUrl) { viewerMedia(settings.baseUrl, post) }
                if (items.isEmpty()) {
                    Text(
                        "该帖没有可查看的媒体文件",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                } else {
                    MediaViewer(
                        items = items,
                        initialIndex = initialIndex,
                        baseUrl = settings.baseUrl,
                        onClose = { navController.popBackStack() },
                        probeOriginalSize = viewModel::probeOriginalSize,
                        onDownload = { viewModel.download(it, settings.baseUrl) },
                        onRecognizeText = viewModel::recognizeText,
                        onSummarizeImage = viewModel::summarizeImage,
                        onAskImage = viewModel::askImage,
                    )
                }
            }
        }
    }
}

/** 查看器主体：分页 + 常驻顶栏（返回/页码/更多）+ 按媒体类型变化的底部操作。 */
@Composable
private fun BoxScope.MediaViewer(
    items: List<ViewerMedia>,
    initialIndex: Int,
    baseUrl: String,
    onClose: () -> Unit,
    probeOriginalSize: suspend (String) -> Long?,
    onDownload: (ViewerMedia) -> Unit,
    onRecognizeText: suspend (String) -> List<String>,
    onSummarizeImage: suspend (String) -> String,
    onAskImage: suspend (String, List<com.archive.app.data.AiTurn>, String) -> String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = LocalSettingsManager.current

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )

    // 倍速：沿用设置里记住的上一次选择，视频页由它下发
    var speed by remember {
        mutableFloatStateOf(
            if (settingsManager?.isRememberPlaybackSpeedEnabled() == true) {
                settingsManager.getLastPlaybackSpeed()
            } else {
                1.0f
            },
        )
    }
    var showActions by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    // 视频的「更多操作」悬浮菜单（下载视频 / 倍速播放 / 分享链接）
    var showMenu by remember { mutableStateOf(false) }
    // 「查看原图」被点击的次数：作为信号交给当前图片页重新发起原图请求
    var originalRetryTick by remember { mutableIntStateOf(0) }
    // 当前图片页上报的「查看原图」胶囊状态
    var originalState by remember { mutableStateOf(OriginalState()) }
    // 常驻顶栏占的高度：媒体区域整体下移同样高度，保证顶栏不遮挡画面
    var chromeHeightPx by remember { mutableIntStateOf(0) }
    // 底部操作条占的高度：图片页据此让媒体区域收进来，画面在「顶栏 ↔ 操作条」之间居中
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    // 视频全屏：收起查看器顶栏、隐藏系统栏并把屏幕切横屏（由视频页里的按钮驱动）。
    // 用 rememberSaveable：桌面模式 / 多窗口（ChromeOS、平板分屏）下 Activity 可能被重建，
    // 若此时全屏状态归零，就会出现「界面看着不像全屏、按钮却以为在全屏」的状态残留。
    var videoFullscreen by rememberSaveable { mutableStateOf(false) }
    // 翻页即退出全屏并收起菜单：视频只是分页里的一页，翻走后若不退出，
    // 就会停在「没有顶栏、还锁着横屏」的画面上，用户找不到出口
    LaunchedEffect(pagerState.currentPage) {
        videoFullscreen = false
        showMenu = false
    }
    // 全屏时返回键先退全屏，而不是直接关掉查看器
    androidx.activity.compose.BackHandler(enabled = videoFullscreen) { videoFullscreen = false }
    val density = LocalDensity.current

    val currentItem = items.getOrNull(pagerState.currentPage)
    val currentAttachment = currentItem?.attachment
    val currentUrl = currentAttachment?.let {
        UrlProvider.downloadUrl(baseUrl, it.path, it.tempDownloadUrl)
    }.orEmpty()

    HorizontalPager(
        state = pagerState,
        // 分页器占满整屏：裁剪边界就是屏幕，图片放大后才能越过中段铺满整屏。
        // 「顶栏 ↔ 底部操作条」之间那段中段改由每一页自己内缩实现（见下面的 band）——
        // 内缩若加在分页器上，裁剪边界会跟着一起缩，放大后画面就被裁在中段里，上下永远留黑边。
        modifier = Modifier.fillMaxSize(),
        // 只预组合相邻一页：邻页只加载便宜的缩略图（原图仅当前页请求），换页时已经有画面；
        // 邻页若是视频则只读缓存，不会跟「正在看的那支视频」抢带宽
        beyondViewportPageCount = 1,
    ) { page ->
        val item = items.getOrNull(page) ?: return@HorizontalPager
        // 中段：媒体在顶栏与底部操作条之间居中（视频页的操作条在页内，由 ViewerVideoPane 自己让位）。
        // 视频全屏时不再内缩：顶栏已收起，画面直接铺满整屏。
        val fullscreenVideo = item is ViewerMedia.Video && videoFullscreen
        val band = Modifier
            .fillMaxSize()
            .padding(
                top = if (fullscreenVideo) 0.dp else with(density) { chromeHeightPx.toDp() },
                bottom = if (item is ViewerMedia.Image) {
                    with(density) { bottomBarHeightPx.toDp() }
                } else {
                    0.dp
                },
            )
        when (item) {
            is ViewerMedia.Image -> ImagePage(
                attachment = item.attachment,
                baseUrl = baseUrl,
                isCurrent = page == pagerState.currentPage,
                retryTick = originalRetryTick,
                onOriginalState = { if (page == pagerState.currentPage) originalState = it },
                onLongPress = { showActions = true },
                probeOriginalSize = probeOriginalSize,
                modifier = band,
            )

            is ViewerMedia.Video -> ViewerVideoPane(
                url = item.url,
                active = page == pagerState.currentPage,
                speed = speed,
                fullscreen = videoFullscreen,
                onToggleFullscreen = { videoFullscreen = !videoFullscreen },
                modifier = band,
            )
        }
    }

    // ── 顶部：返回 / 页码 / 更多（媒体区域已按它的高度下移）──
    // 全屏只隐藏「返回 / 页码」，右上角的「更多」**始终留在原位**：
    // 它本来就是这条顶栏里的按钮，一旦跟着布局（顶栏收起 / 画面下移）上下浮动，
    // 用户就找不到它了 —— 而且全屏时它是唯一的菜单入口，更不能消失。
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            // 全屏时不更新顶栏高度：此时顶栏只剩一个按钮、且系统栏已隐藏，
            // 记下这个值会让退出全屏的瞬间用错内边距，画面上跳一下
            .onSizeChanged { if (!videoFullscreen) chromeHeightPx = it.height }
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!videoFullscreen) {
                ViewerCircleButton(LucideIcons.back, "返回", onClose)
                Box(Modifier.weight(1f))
                if (items.size > 1) {
                    Text(
                        "${pagerState.currentPage + 1} / ${items.size}",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
            Box(Modifier.weight(1f))
            // 更多：视频打开悬浮菜单（下载 / 倍速 / 分享），图片打开操作面板
            ViewerCircleButton(LucideIcons.ellipsis, "更多", onClick = {
                if (currentItem is ViewerMedia.Video) showMenu = true else showActions = true
            })
        }
    }

    // ── 底部：图片页给「查看原图」胶囊与动作；视频页的播放控件在视频页内 ──
    if (currentItem is ViewerMedia.Image) {
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomBarHeightPx = it.height }
                .systemBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val state = originalState
            if (state.visible) {
                OriginalPill(state = state, onLoadOriginal = { originalRetryTick++ })
            }
            Box(Modifier.weight(1f))
            ViewerCircleButton(
                icon = LucideIcons.forward,
                contentDescription = "分享图片",
                onClick = { shareText(context, currentUrl) },
            )
            ViewerCircleButton(
                icon = LucideIcons.download,
                contentDescription = "保存原图",
                onClick = { currentItem.let(onDownload) },
            )
            ViewerCircleButton(
                icon = LucideIcons.gridView,
                contentDescription = "媒体列表",
                onClick = { showGrid = true },
            )
        }
    }

    // 长按 / 「更多」：图片走底部菜单（保存原图 / 提取文字 / 分享），视频保留原来的动作列表
    when (val item = currentItem) {
        is ViewerMedia.Image -> ViewerImageSheetHost(
            show = showActions,
            title = currentAttachment?.name,
            imageUrl = UrlProvider.fileUrl(baseUrl, item.attachment.path.orEmpty()),
            // preview_only 的帖子未登录拿不到原图，OCR / AI 取图失败时退用预览图
            previewUrl = UrlProvider.thumbnailUrl(baseUrl, item.attachment.path.orEmpty()),
            onDismiss = { showActions = false },
            onSave = { onDownload(item) },
            recognize = onRecognizeText,
            summarize = onSummarizeImage,
            ask = onAskImage,
        )

        // 视频的动作改由画面右上角的悬浮菜单承载（见 VideoActionMenu）：
        // 菜单是「贴着按钮浮出的一小块」而不是占屏对话框，横屏全屏下也不打断观看
        is ViewerMedia.Video, null -> Unit
    }

    // 视频「更多操作」悬浮菜单：贴画面右上角浮出，点外部即关，全程不碰播放器状态
    VideoActionMenu(
        show = showMenu && currentItem is ViewerMedia.Video,
        title = currentAttachment?.name,
        speed = speed,
        // 菜单的落点要跟着画面顶部走：普通模式画面从顶栏下方开始，全屏时从屏幕顶部开始
        topPadding = if (videoFullscreen) 0.dp else with(density) { chromeHeightPx.toDp() },
        onDismiss = { showMenu = false },
        onPickSpeed = { picked ->
            // 实时生效：speed 一变，Player.kt 里的 LaunchedEffect(player, speed) 立刻下发；
            // 菜单不关，方便连点几档对比着选
            speed = picked
            if (settingsManager?.isRememberPlaybackSpeedEnabled() == true) {
                settingsManager.setLastPlaybackSpeed(picked)
            }
        },
        onDownload = { currentItem?.let(onDownload) },
        onShare = { shareText(context, currentUrl) },
    )

    // 媒体列表（缩略图宫格，点一张跳到那一页）
    if (showGrid) {
        ViewerGridSheet(
            items = items,
            baseUrl = baseUrl,
            currentIndex = pagerState.currentPage,
            onSelect = { index ->
                showGrid = false
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            onDismiss = { showGrid = false },
        )
    }
}

/**
 * 播放器的「更多操作」悬浮菜单（画面右上角 ⋯ 打开）。
 *
 * **为什么不用 Popup**：`Popup` 是独立窗口，不继承全屏的沉浸式标志位 —— 横屏全屏下打开它
 * 会把系统栏唤醒并留在那里（又一处状态残留）。这里用同一棵组合树里的「透明遮罩 + 右上角
 * 浮出的小卡片」：位置天然跟随当前布局，也不会碰播放器的任何状态。
 *
 * 交互约定：
 * - 点遮罩（小卡片之外的任意位置）= 关闭，**播放不中断**（进度、暂停态一个都不动）；
 * - 倍速点了立刻生效、菜单不关 —— 方便连点几档对比着挑；
 * - 下载 / 分享这类「一次性动作」执行后自动关掉，免得挡住后续画面。
 */
@Composable
private fun VideoActionMenu(
    show: Boolean,
    title: String?,
    speed: Float,
    topPadding: Dp,
    onDismiss: () -> Unit,
    onPickSpeed: (Float) -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    if (!show) return
    val colors = LocalPurrColors.current
    // 遮罩不显示涟漪（整屏闪一下灰蓝很难看）；卡片自己吞掉点击，避免点菜单内部也关掉
    val scrimInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier
                // 落点在画面右上角那个 ⋯ 按钮的正下方：按钮 48dp + 上下各留余量
                .padding(top = topPadding + 60.dp, end = 12.dp)
                .width(248.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceContainerHigh)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(vertical = 6.dp),
        ) {
            title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = colors.textTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            VideoMenuRow("下载视频", LucideIcons.download) {
                onDismiss()
                onDownload()
            }
            AppDivider(Modifier.padding(vertical = 4.dp))
            AppSmallTitle("倍速播放", Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            // 六档铺成两行三列：一行六档会把卡片撑到超出横屏宽度
            PLAYBACK_SPEEDS.chunked(3).forEach { rowOptions ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowOptions.forEach { option ->
                        SpeedChip(
                            label = speedRateLabel(option),
                            selected = option == speed,
                            onClick = { onPickSpeed(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            AppDivider(Modifier.padding(vertical = 4.dp))
            VideoMenuRow("分享链接", LucideIcons.link) {
                onDismiss()
                onShare()
            }
        }
    }
}

/** 悬浮菜单里的一行动作（左文字右图标）。 */
@Composable
private fun VideoMenuRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = LocalPurrColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = colors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** 倍速档位按钮：选中档高亮；点一下立刻生效（菜单不关，可连点对比）。 */
@Composable
private fun SpeedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPurrColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.primary else colors.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) colors.onPrimary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * 查看器里的图片页：**缩略图垫底 + 原图叠加**，可双指缩放，长按弹操作窗口。
 *
 * 加载优先级（「先看到，再看到好的」）：
 * 1. 缩略图：帖子网格里已经请求过，这里通常直接命中 Coil 磁盘缓存，几乎立刻出画面；
 * 2. 原图：**只有当前页才发起请求**（用的是「正在看的那张」应有的带宽），加载完淡入盖住缩略图；
 * 3. 原图 404（preview_only）时不换数据源重下：垫底的缩略图留着即可，省掉一次往返；
 * 4. 原图体积的 HEAD 探测同样只在当前页做，左右邻页不会各发一个 HEAD 抢队列。
 *
 * 布局由调用方给的 [modifier] 决定：它把本页内缩到「顶栏 ↔ 底部操作条」之间的中段，
 * 缩放/平移就挂在这一层上 —— 中段本身不裁剪，所以放大后的画面能越过中段铺满整屏。
 */
@Composable
private fun ImagePage(
    attachment: Attachment,
    baseUrl: String,
    isCurrent: Boolean,
    retryTick: Int,
    onOriginalState: (OriginalState) -> Unit,
    onLongPress: () -> Unit,
    probeOriginalSize: suspend (String) -> Long?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imagePath = attachment.path ?: return
    val zoomState = rememberZoomState()
    val originalUrl = UrlProvider.fileUrl(baseUrl, imagePath)
    val thumbnailUrl = UrlProvider.thumbnailUrl(baseUrl, imagePath)

    var originalFailed by remember(imagePath) { mutableStateOf(false) }
    var originalDisplayed by remember(imagePath) { mutableStateOf(false) }
    var reloadTick by remember(imagePath) { mutableIntStateOf(0) }
    var originalSize by remember(imagePath) { mutableStateOf<Long?>(null) }
    var probed by remember(imagePath) { mutableStateOf(false) }

    if (isCurrent) {
        LaunchedEffect(imagePath, baseUrl) {
            originalSize = probeOriginalSize(originalUrl)
            probed = true
        }
        LaunchedEffect(retryTick) {
            if (retryTick > 0) {
                originalFailed = false
                reloadTick++
            }
        }
        LaunchedEffect(isCurrent, probed, originalDisplayed, originalFailed, originalSize) {
            onOriginalState(
                OriginalState(
                    visible = !originalDisplayed,
                    probed = probed,
                    available = originalSize != null,
                    size = originalSize,
                    loading = !originalDisplayed && !originalFailed,
                ),
            )
        }
    }

    val gestures = Modifier
        .fillMaxSize()
        .pointerInput(imagePath) {
            detectTapGestures(onLongPress = { onLongPress() })
        }
        .zoomable(zoomState)

    // 内缩（中段）与缩放/平移挂在同一层上，缩略图与原图**共用同一套变换**：
    // - 只给原图加 zoomable 时，放大后拖动会把下面那层没缩放的缩略图从边缘露出来；
    // - 中段这一层不裁剪，所以放大后的画面能越过中段铺满整屏。
    Box(modifier.then(gestures), contentAlignment = Alignment.Center) {
        // ① 垫底缩略图：不做 crossfade，命中缓存的第一帧就要立刻出现
        val thumbModel = remember(thumbnailUrl) {
            ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .build()
        }
        AsyncImage(
            model = thumbModel,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        // ② 原图：只有当前页请求；成功淡入盖住缩略图，失败则继续显示缩略图，不重复下载
        if (isCurrent && !originalFailed) {
            val model = remember(imagePath, baseUrl, originalUrl, reloadTick) {
                ImageRequest.Builder(context)
                    .data(originalUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = model,
                contentDescription = attachment.name ?: "图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { originalDisplayed = true },
                onError = { originalFailed = true },
            )
        }
    }
}

/** 当前图片页的原图状态（驱动左下胶囊）。 */
private data class OriginalState(
    val visible: Boolean = false,
    val probed: Boolean = false,
    val available: Boolean = false,
    val size: Long? = null,
    val loading: Boolean = false,
)

/**
 * 左下「查看原图 3MB」胶囊（对照微信看图界面）：
 * - 原图还没显示出来 → 可点，点了立刻重新加载原图；
 * - 站点没有原图（preview_only）→ 灰色「仅预览·无原图」，不可点。
 */
@Composable
private fun OriginalPill(
    state: OriginalState,
    onLoadOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state.available
    // 触控区做到 48dp，视觉上仍是 36dp 的小胶囊
    Box(
        modifier
            .height(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onLoadOriginal),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.10f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (state.loading && !state.probed) {
                AppSpinner(
                    size = 13.dp,
                    strokeWidth = 1.5.dp,
                    color = Color.White,
                )
            }
            Text(
                text = when {
                    !state.probed || state.loading -> "查看原图"
                    state.available -> "查看原图" + (state.size?.let { " ${formatFileSize(it)}" } ?: "")
                    else -> "仅预览·无原图"
                },
                color = Color.White.copy(alpha = if (enabled) 1f else 0.65f),
                fontSize = 13.sp,
            )
        }
    }
}

/** 圆形浮动按钮（白 18% 底 + 白色图标，48dp 触控区）。 */
@Composable
private fun ViewerCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
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
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 媒体宫格：图片用缩略图、视频用首帧（+ 播放角标），点一张跳到那一页。 */
@Composable
private fun ViewerGridSheet(
    items: List<ViewerMedia>,
    baseUrl: String,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .systemBarsPadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "全部媒体（${items.size}）",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    ViewerCircleButton(
                        icon = LucideIcons.close,
                        contentDescription = "关闭",
                        onClick = onDismiss,
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items.size) { index ->
                        val selected = index == currentIndex
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) colors.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { onSelect(index) },
                        ) {
                            when (val item = items[index]) {
                                is ViewerMedia.Image -> ThumbImage(
                                    url = UrlProvider.thumbnailUrl(
                                        baseUrl,
                                        item.attachment.path.orEmpty(),
                                    ),
                                    modifier = Modifier.fillMaxSize(),
                                    contentDescription = "第 ${index + 1} 个",
                                    blurredOverride = false,
                                    cornerRadius = 8,
                                )

                                is ViewerMedia.Video -> VideoGridCell(item.url, index)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 宫格里的视频格：首帧（mp4 可解）+ 居中播放角标；HLS 解不出首帧就留黑底。 */
@Composable
private fun VideoGridCell(url: String, index: Int) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.contains(".m3u8", ignoreCase = true)) {
            AsyncImage(
                model = remember(url) {
                    ImageRequest.Builder(context)
                        .data(url)
                        .videoFramePercent(0.0)
                        .build()
                },
                contentDescription = "第 ${index + 1} 个",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Icon(
            imageVector = LucideIcons.play,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun shareText(context: Context, url: String) {
    if (url.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "分享")) }
}

private fun copyText(context: Context, label: String, url: String) {
    if (url.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, url))
}

/** 「3MB」「288KB」这类展示（对照参考图里的原图体积）。 */
private fun formatFileSize(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return when {
        mb >= 1.0 -> {
            val text = "%.1f".format(mb)
            (if (text.endsWith(".0")) text.dropLast(2) else text) + "MB"
        }

        bytes >= 1024 -> "${bytes / 1024}KB"
        else -> "${bytes}B"
    }
}
