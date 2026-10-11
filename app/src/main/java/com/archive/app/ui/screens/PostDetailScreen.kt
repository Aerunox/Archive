package com.archive.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.archive.core.model.Post
import com.archive.core.model.PostRevision
import com.archive.core.store.SettingsManager
import com.archive.data.repository.AuthRepository
import com.archive.data.repository.BookmarkManager
import com.archive.data.repository.CreatorSubscriptionRepository
import com.archive.data.repository.DownloadEnqueuer
import com.archive.app.data.FavoriteRecord
import com.archive.app.data.HistoryRecord
import com.archive.app.data.SettingsRepository
import com.archive.app.data.remote.UrlProvider
import com.archive.app.data.repo.LocalStore
import com.archive.app.data.repo.PostRepository
import com.archive.app.domain.Attachment
import com.archive.app.domain.MediaKind
import com.archive.app.domain.kind
import com.archive.app.domain.PostComment
import com.archive.app.domain.PostDetail
import com.archive.app.ui.components.AudioPlayer
import com.archive.app.ui.components.HtmlText
import com.archive.app.ui.components.CreatorAvatar
import com.archive.app.ui.components.ThumbImage
import com.archive.app.ui.components.VideoPosterCell
import com.archive.app.ui.components.indexOfAttachment
import com.archive.app.ui.components.viewerMedia
import com.archive.app.ui.design.AppButton
import com.archive.app.ui.design.AppCard
import com.archive.app.ui.design.AppErrorState
import com.archive.app.ui.design.AppIconButton
import com.archive.app.ui.design.AppLoading
import com.archive.app.ui.design.AppListItem
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.AppTopBar
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.archive.app.ui.design.AppDialog
import com.archive.app.ui.theme.LocalPurrColors
import com.archive.app.ui.theme.onServiceBrandColor
import com.archive.app.ui.theme.serviceBrandColor
import com.archive.app.util.ExternalLinks
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.shape.CircleShape
import com.archive.app.ui.design.AppDivider
import com.archive.app.ui.LocalAppSettings
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.archive.app.ui.components.ImageAction
import com.archive.app.ui.components.ImageActionsDialog
import com.archive.app.ui.design.AppScaffold
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.navigation.Routes
import com.archive.app.util.Fmt
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 领域模型 → Pawchive core 模型（供 BookmarkManager 使用）。 */
fun PostDetail.toTheirPost(): Post = Post(
    id = postId,
    user = creatorId,
    userName = creatorId,
    service = service,
    title = title,
    content = contentHtml,
    added = added,
    published = published,
    edited = edited,
    file = file?.let { com.archive.core.model.PostFile(it.name, it.path, previewOnly) },
    attachments = attachments.map {
        com.archive.core.model.Attachment(it.name, it.path, previewOnly = false)
    },
    sharedFile = false,
)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    /** 错误消息需要本地化，这里只要一个 Context 用来取字符串。 */
    @dagger.hilt.android.qualifiers.ApplicationContext
    private val context: android.content.Context,
    savedStateHandle: SavedStateHandle,
    private val postRepository: PostRepository,
    private val localStore: LocalStore,
    settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val bookmarkManager: BookmarkManager,
    private val creatorSubscriptionRepository: CreatorSubscriptionRepository,
    private val settingsManager: SettingsManager,
    private val downloadEnqueuer: DownloadEnqueuer,
    private val favoriteActions: com.archive.app.data.repo.FavoriteActions,
    private val displayNames: com.archive.app.data.repo.DisplayNames,
) : ViewModel() {

    /** 作者显示名（本地作者库命中时用真实名字，否则用 id）与订阅状态。 */
    val creatorName = MutableStateFlow<String?>(null)
    val isSubscribed = MutableStateFlow(false)
    private val subscriptions = creatorSubscriptionRepository


    fun toggleSubscribe() {
        viewModelScope.launch {
            if (isSubscribed.value) {
                subscriptions.unsubscribe(service, creatorId)
                isSubscribed.value = false
            } else {
                subscriptions.subscribe(service, creatorId, creatorName.value, null)
                isSubscribed.value = true
            }
        }
    }

    val service: String = savedStateHandle.get<String>("service") ?: ""
    val creatorId: String = savedStateHandle.get<String>("creatorId") ?: ""
    val postId: String = savedStateHandle.get<String>("postId") ?: ""

    val detail = MutableStateFlow<Result<PostDetail>?>(null)
    val comments = MutableStateFlow<List<PostComment>>(emptyList())
    val revisions = MutableStateFlow<List<PostRevision>>(emptyList())
    val loading = MutableStateFlow(true)

    /** 一次性提示（下载不可用等），由界面弹 Toast 后清空。 */
    val message = MutableStateFlow<String?>(null)

    fun clearMessage() {
        message.value = null
    }

    val isFavorite: StateFlow<Boolean> = localStore
        .isFavorite(service, creatorId, postId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 云端收藏可用（已登录）。 */
    val loggedIn: StateFlow<Boolean> = MutableStateFlow(authRepository.isLoggedIn())

    val baseUrl: StateFlow<String> = settingsRepository.settings
        .map { it.baseUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UrlProvider.DEFAULT_BASE_URL)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            val result = runCatching { postRepository.detail(service, creatorId, postId) }
            detail.value = result
            result.getOrNull()?.let { post ->
                // 卡片收藏时只有摘要、无法建全文索引，这里补建
                viewModelScope.launch {
                    runCatching {
                        favoriteActions.ensureArchived(
                            FavoriteRecord(
                                service = post.service,
                                creatorId = post.creatorId,
                                creatorName = creatorName.value ?: post.creatorId,
                                postId = post.postId,
                                title = post.title,
                                thumbPath = post.file?.path ?: post.attachments.firstOrNull()?.path,
                            ),
                            post.toTheirPost(),
                        )
                    }
                }
                localStore.recordHistory(
                    HistoryRecord(
                        service = post.service,
                        creatorId = post.creatorId,
                        creatorName = post.creatorId,
                        postId = post.postId,
                        title = post.title,
                        thumbPath = post.file?.path ?: post.attachments.firstOrNull()?.path,
                    ),
                )
            }
            comments.value = runCatching {
                postRepository.comments(service, creatorId, postId)
            }.getOrDefault(emptyList())
            loading.value = false
        }
        // 作者显示名（本地作者库 → 内存缓存 → 线上兜底）与订阅状态
        viewModelScope.launch {
            creatorName.value = displayNames.resolve(service, creatorId)
        }
        viewModelScope.launch {
            isSubscribed.value = runCatching { subscriptions.isSubscribed(service, creatorId) }.getOrDefault(false)
        }
        // 修订历史（公开端点，失败静默）
        viewModelScope.launch {
            revisions.value = runCatching {
                com.archive.core.api.ApiClient.publicApi.getPostRevisions(service, creatorId, postId)
            }.getOrDefault(emptyList())
        }
    }

    /**
     * 收藏 / 取消收藏（Pawchive 行为对齐）：
     * - 本地收藏
     * - 已登录时同步云端收藏；离线时由待补写队列兜底
     * - 「收藏即订阅」开启时自动订阅作者
     */
    fun toggleFavorite(post: PostDetail) {
        viewModelScope.launch {
            favoriteActions.toggle(
                FavoriteRecord(
                    service = post.service,
                    creatorId = post.creatorId,
                    creatorName = creatorName.value ?: post.creatorId,
                    postId = post.postId,
                    title = post.title,
                    thumbPath = post.file?.path ?: post.attachments.firstOrNull()?.path,
                ),
                fullPost = post.toTheirPost(),
            )
        }
    }

    fun download(attachment: Attachment) = enqueue(attachment)

    /** 保存全部图片（顶栏动作，对照 Pawchive 的 btn_save_all_images）。 */
    fun downloadAllImages(post: PostDetail) {
        (listOfNotNull(post.file) + post.attachments)
            .filter { it.kind() == MediaKind.IMAGE }
            .forEach { enqueue(it) }
    }

    private fun enqueue(attachment: Attachment) {
        // 站点未导入原文件的附件（deferred 视频）只有临时直链可下；
        // 两者都没有时不能拼出一个必然 404 的地址去入队
        val url = UrlProvider.downloadUrl(baseUrl.value, attachment.path, attachment.tempDownloadUrl)
        if (url == null || attachment.tempLinkExpired()) {
            message.value = if (attachment.tempLinkExpired()) {
                context.getString(com.archive.app.R.string.link_expired_short)
            } else {
                context.getString(com.archive.app.R.string.no_original_short)
            }
            return
        }
        val name = attachment.displayName()
        val ext = name.substringAfterLast('.', "").lowercase()
        val imageExts = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        val videoExts = setOf("mp4", "webm", "mov", "mkv", "m4v")
        val mime = when {
            ext in imageExts -> "image/$ext"
            ext in videoExts -> "video/*"
            ext in setOf("mp3", "m4a", "wav", "ogg", "flac", "aac", "opus") -> "audio/*"
            else -> "application/octet-stream"
        }
        viewModelScope.launch {
            runCatching {
                when {
                    ext in imageExts -> downloadEnqueuer.enqueueImageDownload(url, name, mime)
                    ext in videoExts -> downloadEnqueuer.enqueueVideoDownload(url, name, mime)
                    else -> downloadEnqueuer.enqueueAttachmentDownload(url, name, mime)
                }
            }.onFailure {
                message.value = context.getString(
                    com.archive.app.R.string.download_add_failed,
                    it.message ?: context.getString(com.archive.app.R.string.unknown_error),
                )
            }
        }
    }
}

@Composable
fun PostDetailScreen(
    navController: NavHostController,
    service: String,
    creatorId: String,
    postId: String,
    viewModel: PostDetailViewModel = hiltViewModel(),
) {
    val detailResult by viewModel.detail.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val revisions by viewModel.revisions.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val creatorName by viewModel.creatorName.collectAsStateWithLifecycle()
    val isSubscribed by viewModel.isSubscribed.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 下载不可用等一次性提示
    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.post_detail_title),
                onBack = { navController.popBackStack() },
                actions = {
                    AppIconButton(
                        onClick = { detailResult?.getOrNull()?.let { viewModel.downloadAllImages(it) } },
                        icon = LucideIcons.downloads,
                        contentDescription = androidx.compose.ui.res.stringResource(com.archive.app.R.string.save_all_images),
                    )
                    AppIconButton(
                        onClick = { detailResult?.getOrNull()?.let { viewModel.toggleFavorite(it) } },
                        icon = if (isFavorite) LucideIcons.favoritesFill else LucideIcons.favorites,
                        contentDescription = androidx.compose.ui.res.stringResource(
                    if (isFavorite) com.archive.app.R.string.unfavorite
                    else com.archive.app.R.string.favorite,
                ),
                    )
                },
            )
        },
    ) { padding ->
        val result = detailResult
        when {
            loading -> AppLoading(
                Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            )
            result == null -> Unit
            result.isFailure -> AppErrorState(
                message = context.getString(
                    com.archive.app.R.string.load_failed,
                    result.exceptionOrNull()?.message
                        ?: context.getString(com.archive.app.R.string.network_error),
                ),
                onRetry = { viewModel.load() },
            )
            else -> {
                val post = result.getOrThrow()
                DetailContent(
                    topPadding = padding.calculateTopPadding(),
                    post = post,
                    creatorName = creatorName,
                    isSubscribed = isSubscribed,
                    onToggleSubscribe = viewModel::toggleSubscribe,
                    onDownloadAll = { viewModel.downloadAllImages(post) },
                    comments = comments,
                    revisions = revisions,
                    baseUrl = baseUrl,
                    // 图片与视频共用同一个查看器入口：index 是统一媒体列表里的下标
                    onOpenMedia = { index ->
                        navController.navigate(
                            Routes.viewer(post.service, post.creatorId, post.postId, index),
                        )
                    },
                    onDownload = viewModel::download,
                    onOpenCreator = {
                        navController.navigate(Routes.creator(post.service, post.creatorId))
                    },
                    onOpenPost = { targetId ->
                        navController.navigate(Routes.post(post.service, post.creatorId, targetId)) {
                            popUpTo(Routes.post(post.service, post.creatorId, post.postId)) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    topPadding: androidx.compose.ui.unit.Dp,
    post: PostDetail,
    creatorName: String?,
    isSubscribed: Boolean,
    onToggleSubscribe: () -> Unit,
    onDownloadAll: () -> Unit,
    comments: List<PostComment>,
    revisions: List<PostRevision>,
    baseUrl: String,
    onOpenMedia: (Int) -> Unit,
    onDownload: (Attachment) -> Unit,
    onOpenCreator: () -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val media = listOfNotNull(post.file) + post.attachments
    // 图片必须有 path 才能取缩略图与原图（站点未导入的 deferred 附件没有 path）
    val images = media.filter { it.kind() == MediaKind.IMAGE && !it.path.isNullOrBlank() }
    val imageItems = images.mapNotNull { attachment -> attachment.path?.let { attachment to it } }
    val videos = media.filter { it.kind() == MediaKind.VIDEO }
    // 视频列表：可播放的进 X 风格海报格，放不了的（未导入/链接过期）留成普通附件行并给提示
    val videoEntries = videos.mapIndexed { i, attachment ->
        VideoEntry(i, attachment, UrlProvider.playbackUrl(baseUrl, attachment.path, attachment.tempUrl))
    }
    val playableVideos = videoEntries.filter { it.url != null && !it.attachment.tempLinkExpired() }
    val nonImageMedia = media.filter { it.kind() == MediaKind.AUDIO } +
        videoEntries.filterNot { it in playableVideos }.map { it.attachment }
    val mainImage = post.file?.takeIf { it.kind() == MediaKind.IMAGE && !it.path.isNullOrBlank() }
    // 统一媒体查看器的页列表（图片 + 可播放视频混排）：缩略图/海报格都用它换算查看器下标，
    // 与查看器内部同源，保证「点哪张就从哪张开始左右滑」，且能连续滑到相邻的另一种媒体
    val viewerItems = remember(post, baseUrl) { viewerMedia(baseUrl, post) }
    // 列表 key：deferred 附件没有 path，用名称 + 序号保证唯一且在同一帖内稳定
    fun mediaKey(index: Int, attachment: Attachment): String =
        attachment.path ?: "deferred-${attachment.name ?: "file"}-$index"
    val externalLinks = remember(post.contentHtml) { ExternalLinks.extract(post.contentHtml) }
    var pendingLink by remember { mutableStateOf<ExternalLinks.Link?>(null) }
    // 长按图片：记录被长按的那张（缩略图菜单）
    var longPressed by remember { mutableStateOf<Attachment?>(null) }
    val context = LocalContext.current
    val colors = LocalPurrColors.current
    val settings = LocalAppSettings.current

    val localStore = com.archive.app.ui.LocalLocalStore.current
    val favoriteActions = com.archive.app.ui.LocalFavoriteActions.current
    val scope = rememberCoroutineScope()
    val favoriteState by remember(localStore, post.service, post.creatorId, post.postId) {
        localStore?.observeFavorite(post.service, post.creatorId, post.postId)
            ?: kotlinx.coroutines.flow.flowOf(false)
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteRecord = com.archive.app.data.FavoriteRecord(
        service = post.service,
        creatorId = post.creatorId,
        creatorName = creatorName ?: post.creatorId,
        postId = post.postId,
        title = post.title,
        thumbPath = post.file?.path ?: post.attachments.firstOrNull()?.path,
    )

    fun share() {
        val link = settings.baseUrl.trimEnd('/') + "/" + post.service + "/user/" +
            post.creatorId + "/post/" + post.postId
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, post.title + "\n" + link)
                    },
                    context.getString(com.archive.app.R.string.share_post),
                ),
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = 24.dp),
    ) {
        // ── 作者头部（X 的帖子详情顶部：大头像 + 名字 + @handle + 关注按钮）──
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CreatorAvatar(
                    url = UrlProvider.iconUrl(baseUrl, post.service, post.creatorId),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenCreator),
                    contentDescription = androidx.compose.ui.res.stringResource(com.archive.app.R.string.author_avatar),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    AppText(
                        creatorName ?: post.creatorId,
                        style = AppTextStyle.MAIN,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        "@" + post.creatorId + " · " + post.service,
                        style = AppTextStyle.SECONDARY,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 关注按钮（对应本应用的订阅）
                AppButton(
                    text = androidx.compose.ui.res.stringResource(
                        if (isSubscribed) com.archive.app.R.string.subscribed
                        else com.archive.app.R.string.subscribe,
                    ),
                    onClick = onToggleSubscribe,
                    primary = !isSubscribed,
                )
            }
        }

        // ── 标题 / 正文文本（全宽，不缩进）──
        item {
            // 翻译开启时标题**原地替换**为译文（与正文同一次请求返回，不多打接口）
            val translation: com.archive.app.ui.components.PostTranslationViewModel = hiltViewModel()
            val translatedTitle by translation.translatedTitle.collectAsStateWithLifecycle()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                AppText(
                    translatedTitle ?: post.title,
                    style = AppTextStyle.TITLE,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (post.tags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        items(post.tags.size) { i -> SimpleTag(post.tags[i], post.service) }
                    }
                }
            }
        }

        // ── 主图（圆角全宽）──
        mainImage?.let { file ->
            val filePath = file.path ?: return@let
            item {
                ThumbImage(
                    url = UrlProvider.thumbnailUrl(baseUrl, filePath),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onOpenMedia(viewerItems.indexOfAttachment(file)) },
                    contentDescription = file.name ?: post.title,
                    blurredOverride = false,
                    cornerRadius = 16,
                    originalUrl = UrlProvider.fileUrl(baseUrl, filePath),
                )
            }
        }

        // ── 正文 HTML ──
        if (post.contentHtml.isNotBlank()) {
            item {
                // 翻译开启时正文**原地替换**为译文 HTML：与原文走同一个渲染器，
                // 段落划分、标题层级、列表符号与编号、换行因此和原文严格对齐
                // （不再在下方追加一段排版独立的纯文本）
                val translation: com.archive.app.ui.components.PostTranslationViewModel = hiltViewModel()
                val translatedHtml by translation.translatedHtml.collectAsStateWithLifecycle()
                HtmlText(
                    html = translatedHtml ?: post.contentHtml,
                    baseUrl = baseUrl,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // 帖子翻译控件（对照参考图的「显示翻译 + 齿轮」）：译文由上面的标题 / 正文原地替换。
        // 接口/提示词/采样参数在「我 → 设置 → 帖子翻译」里配。
        item {
            com.archive.app.ui.components.PostTranslationSection(
                title = post.title,
                contentHtml = post.contentHtml,
            )
        }

        if (post.previewOnly) {
            item {
                AppText(
                    androidx.compose.ui.res.stringResource(com.archive.app.R.string.preview_only_notice),
                    style = AppTextStyle.SMALL,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        // ── 时间行（灰色，对应 X 的「时间 · 查看次数」）──
        item {
            AppText(
                buildString {
                    append(Fmt.isoDate(post.published))
                    post.edited?.let { append(" · 编辑于 ").append(Fmt.isoDate(it)) }
                    if (media.isNotEmpty()) append(" · 附件 ").append(media.size).append(" 项")
                },
                style = AppTextStyle.SECONDARY,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ── 操作行（对应 X 的回复/转发/喜欢/书签/分享，这里放真实可用的动作）──
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (comments.isNotEmpty()) {
                    DetailAction(
                        icon = LucideIcons.messages,
                        text = comments.size.toString(),
                        contentDescription = androidx.compose.ui.res.stringResource(com.archive.app.R.string.cd_comments),
                        onClick = null,
                    )
                }
                DetailAction(
                    icon = LucideIcons.folder,
                    text = media.size.toString(),
                    contentDescription = androidx.compose.ui.res.stringResource(com.archive.app.R.string.cd_attachments),
                    onClick = null,
                )
                DetailAction(
                    icon = LucideIcons.downloads,
                    text = null,
                    contentDescription = androidx.compose.ui.res.stringResource(com.archive.app.R.string.save_all_images),
                    onClick = onDownloadAll,
                )
                DetailAction(
                    icon = if (favoriteState) LucideIcons.favoritesFill else LucideIcons.favorites,
                    text = null,
                    contentDescription = androidx.compose.ui.res.stringResource(
                    if (favoriteState) com.archive.app.R.string.unfavorite
                    else com.archive.app.R.string.favorite,
                ),
                    tint = if (favoriteState) colors.primary else colors.textTertiary,
                    onClick = {
                        favoriteActions?.let { actions ->
                            scope.launch { actions.toggle(favoriteRecord, fullPost = post.toTheirPost()) }
                        }
                    },
                )
                DetailAction(
                    icon = LucideIcons.externalLink,
                    text = null,
                    contentDescription = androidx.compose.ui.res.stringResource(com.archive.app.R.string.cd_share),
                    onClick = { share() },
                )
            }
            AppDivider()
        }

        // ── 附件 ──
        if (media.isNotEmpty()) {
            item {
                SectionHeader(
                    androidx.compose.ui.res.stringResource(
                        com.archive.app.R.string.section_attachments,
                        media.size,
                    ),
                )
            }
            // 视频：X 风格海报格（首帧 + 播放三角 + 时长角标），点开进统一媒体查看器
            if (playableVideos.isNotEmpty()) {
                item {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        playableVideos.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { entry ->
                                    val url = entry.url ?: return@forEach
                                    VideoPosterCell(
                                        url = url,
                                        contentDescription = entry.attachment.displayName(),
                                        modifier = Modifier.weight(1f),
                                        defaultAspectRatio = if (playableVideos.size == 1) 16f / 9f else 1f,
                                    ) { onOpenMedia(viewerItems.indexOfAttachment(entry.attachment)) }
                                }
                                if (rowItems.size == 1 && playableVideos.size > 1) {
                                    Box(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            if (imageItems.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        imageItems.chunked(3).forEach { rowItems ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowItems.forEach { (attachment, imagePath) ->
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .pointerInput(imagePath) {
                                                detectTapGestures(
                                                    onLongPress = { longPressed = attachment },
                                                    onTap = { onOpenMedia(viewerItems.indexOfAttachment(attachment)) },
                                                )
                                            },
                                    ) {
                                        ThumbImage(
                                            url = UrlProvider.thumbnailUrl(baseUrl, imagePath),
                                            modifier = Modifier.fillMaxSize(),
                                            contentDescription = attachment.name
                        ?: androidx.compose.ui.res.stringResource(com.archive.app.R.string.attachment_image),
                                            blurredOverride = false,
                                            originalUrl = UrlProvider.fileUrl(baseUrl, imagePath),
                                        )
                                    }
                                }
                                repeat(3 - rowItems.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            nonImageMedia.forEachIndexed { mediaIndex, attachment ->
                item(key = mediaKey(mediaIndex, attachment)) {
                    val playbackUrl = UrlProvider.playbackUrl(baseUrl, attachment.path, attachment.tempUrl)
                    val expired = attachment.tempLinkExpired()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        if (attachment.kind() == MediaKind.AUDIO && playbackUrl != null && !expired) {
                            AudioPlayer(
                                name = attachment.displayName("audio"),
                                url = playbackUrl,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppText(
                                attachment.displayName(),
                                style = AppTextStyle.SMALL,
                                modifier = Modifier.weight(1f),
                            )
                            AppIconButton(
                                onClick = { onDownload(attachment) },
                                icon = LucideIcons.download,
                                contentDescription = androidx.compose.ui.res.stringResource(
                        com.archive.app.R.string.download_named,
                        attachment.displayName(),
                    ),
                            )
                        }
                        val mediaHint = when {
                            expired -> androidx.compose.ui.res.stringResource(
                                com.archive.app.R.string.link_expired_long,
                            )

                            attachment.previewOnly -> androidx.compose.ui.res.stringResource(
                                com.archive.app.R.string.link_preview_only_long,
                            )

                            playbackUrl == null -> androidx.compose.ui.res.stringResource(
                                com.archive.app.R.string.link_deferred_long,
                            )
                            else -> null
                        }
                        mediaHint?.let {
                            AppText(
                                it,
                                style = AppTextStyle.SMALL,
                                color = LocalPurrColors.current.textTertiary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            if (externalLinks.isNotEmpty()) {
                item {
                    AppText(
                        androidx.compose.ui.res.stringResource(com.archive.app.R.string.external_links),
                        style = AppTextStyle.SECONDARY,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                items(externalLinks.size, key = { "link-" + externalLinks[it].url }) { i ->
                    ExternalLinkCard(link = externalLinks[i]) { pendingLink = externalLinks[i] }
                }
            }
        }

        // ── 评论（对应 X 的回复列表样式）──
        if (comments.isNotEmpty()) {
            item {
                SectionHeader(
                    androidx.compose.ui.res.stringResource(
                        com.archive.app.R.string.section_comments,
                        comments.size,
                    ),
                )
            }
            items(comments.take(50).size) { i ->
                val comment = comments.take(50)[i]
                CommentRow(comment = comment, baseUrl = baseUrl)
            }
        }

        // ── 修订历史 ──
        if (revisions.isNotEmpty()) {
            item {
                SectionHeader(
                    androidx.compose.ui.res.stringResource(
                        com.archive.app.R.string.section_revisions,
                        revisions.size,
                    ),
                )
            }
            items(revisions.take(20).size) { i ->
                val rev = revisions.take(20)[i]
                AppListItem(
                    title = androidx.compose.ui.res.stringResource(
                        com.archive.app.R.string.revision_title,
                        rev.revisionId,
                        rev.title ?: androidx.compose.ui.res.stringResource(
                            com.archive.app.R.string.revision_no_title,
                        ),
                    ),
                    summary = listOfNotNull(
                        rev.edited?.let {
                            androidx.compose.ui.res.stringResource(
                                com.archive.app.R.string.revision_edited,
                                Fmt.isoDate(it),
                            )
                        },
                        rev.published?.let {
                            androidx.compose.ui.res.stringResource(
                                com.archive.app.R.string.revision_published,
                                Fmt.isoDate(it),
                            )
                        },
                    ).joinToString(" · ").ifBlank { null },
                    onClick = null,
                )
            }
        }

        // ── 上一篇 / 下一篇 ──
        if (post.prev != null || post.next != null) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    post.prev?.let {
                        AppButton(
                        androidx.compose.ui.res.stringResource(com.archive.app.R.string.prev_post),
                        { onOpenPost(it) },
                        Modifier.weight(1f),
                        primary = false,
                    )
                    }
                    post.next?.let {
                        AppButton(
                        androidx.compose.ui.res.stringResource(com.archive.app.R.string.next_post),
                        { onOpenPost(it) },
                        Modifier.weight(1f),
                        primary = false,
                    )
                    }
                }
            }
        }
    }

    // 长按图片的操作窗口
    longPressed?.let { attachment ->
        val url = UrlProvider.downloadUrl(baseUrl, attachment.path, attachment.tempDownloadUrl).orEmpty()
        ImageActionsDialog(
            show = true,
            onDismiss = { longPressed = null },
            title = attachment.name,
            actions = listOf(
                ImageAction(
                    androidx.compose.ui.res.stringResource(com.archive.app.R.string.share_image),
                ) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(
                                send,
                                context.getString(com.archive.app.R.string.share_image),
                            ),
                        )
                    }
                },
                ImageAction(
                    androidx.compose.ui.res.stringResource(com.archive.app.R.string.save_original),
                ) { onDownload(attachment) },
                ImageAction(
                    androidx.compose.ui.res.stringResource(com.archive.app.R.string.save_all_originals),
                ) { images.forEach(onDownload) },
                ImageAction(
                    androidx.compose.ui.res.stringResource(com.archive.app.R.string.copy_image_link),
                ) {
                    val clipboard = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE,
                    ) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            // 剪贴板标签用应用名（系统提示里显示，属于元数据）
                            context.getString(com.archive.app.R.string.app_name),
                            url,
                        ),
                    )
                },
            ),
        )
    }

    // 外部链接确认（对照 Pawchive 的 external_link_warning 对话框）
    pendingLink?.let { link ->
        AppDialog(
            show = true,
            title = androidx.compose.ui.res.stringResource(com.archive.app.R.string.open_external_link),
            summary = androidx.compose.ui.res.stringResource(
                com.archive.app.R.string.open_link_prompt,
                link.url,
            ),
            onDismiss = { pendingLink = null },
            confirmLabel = androidx.compose.ui.res.stringResource(com.archive.app.R.string.action_open),
            onConfirm = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                }
                pendingLink = null
            },
            dismissLabel = androidx.compose.ui.res.stringResource(com.archive.app.R.string.action_cancel),
        )
    }
}

/** X 式底部操作项：图标 + 可选计数。 */
@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String?,
    contentDescription: String,
    onClick: (() -> Unit)?,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = LocalPurrColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.textTertiary,
            modifier = Modifier.size(20.dp),
        )
        text?.let {
            AppText(it, style = AppTextStyle.SMALL, color = colors.textTertiary)
        }
    }
}

/** 评论行（对应 X 的回复样式：头像 + 名字 + 时间 + 正文）。 */
@Composable
private fun CommentRow(comment: PostComment, baseUrl: String) {
    val colors = LocalPurrColors.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    comment.commenter.take(1).uppercase(),
                    style = AppTextStyle.SMALL,
                    color = colors.textSecondary,
                )
            }
            AppText(
                comment.commenter,
                style = AppTextStyle.MAIN,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 10.dp),
            )
            AppText(
                " · " + Fmt.relativeIso(comment.published),
                style = AppTextStyle.SMALL,
                color = colors.textTertiary,
            )
        }
        HtmlText(
            html = comment.content,
            baseUrl = baseUrl,
            modifier = Modifier.padding(start = 42.dp, top = 4.dp),
        )
        AppDivider(Modifier.padding(start = 42.dp, top = 8.dp))
    }
}

/** 分区标题。 */
@Composable
private fun SectionHeader(text: String) {
    AppText(
        text,
        style = AppTextStyle.TITLE,
        fontSize = 18.sp,
        color = LocalPurrColors.current.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** 平台徽标（胶囊）：文字与底色取平台品牌色（Patreon 红 / Fanbox 蓝），未知平台回退主题色。 */
@Composable
private fun ServiceBadge(service: String) {
    val colors = LocalPurrColors.current
    val brand = com.archive.app.ui.theme.serviceBrandColor(service) ?: colors.primary
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(brand.copy(alpha = 0.20f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        AppText(
            com.archive.app.ui.theme.servicePlatformLabel(service),
            style = AppTextStyle.SMALL,
            color = brand,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 外部链接卡片：图标 + 标题 + 域名 + 外链图标。 */
@Composable
private fun ExternalLinkCard(link: ExternalLinks.Link, onClick: () -> Unit) {
    val colors = LocalPurrColors.current
    AppCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = LucideIcons.link,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                AppText(
                    link.title,
                    style = AppTextStyle.MAIN,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppText(link.host, style = AppTextStyle.SMALL, color = colors.textTertiary)
            }
            Icon(
                imageVector = LucideIcons.externalLink,
                contentDescription = androidx.compose.ui.res.stringResource(com.archive.app.R.string.cd_open_link),
                tint = colors.textTertiary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 帖子标签：底色用内容平台的品牌色（Patreon 珊瑚橙 / Fanbox 蓝），
 * 让「这条帖子来自哪家」在标签上也一眼可辨；没有约定色的服务维持原来的中性卡片。
 */
@Composable
private fun SimpleTag(label: String, service: String) {
    val brand = serviceBrandColor(service)
    if (brand == null) {
        AppCard(onClick = null) {
            AppText(label, style = AppTextStyle.SMALL, modifier = Modifier.padding(horizontal = 4.dp))
        }
        return
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(brand)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        AppText(label, style = AppTextStyle.SMALL, color = onServiceBrandColor(brand))
    }
}

/** 帖子里的一个视频：原始下标（决定全屏播放器的圆点位置）+ 可播放地址（站点未导入时为 null）。 */
private data class VideoEntry(
    val index: Int,
    val attachment: Attachment,
    val url: String?,
)
