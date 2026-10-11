package com.archive.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.archive.data.repository.BlockedCreatorManager
import com.archive.app.domain.PostSummary
import com.archive.app.ui.design.AppText
import com.archive.app.ui.design.AppTextStyle
import com.archive.app.ui.design.lucide.LucideIcons
import com.archive.app.ui.theme.LocalPurrColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 帖子在多选里的身份：与下标无关，所以滚动、翻页、跨页面都稳定。 */
data class PostKey(val service: String, val creatorId: String, val postId: String)

fun PostSummary.selectionKey(): PostKey = PostKey(service, creatorId, postId)

/**
 * 帖子多选状态。
 *
 * 放在根组件上由所有列表页共享（见 [LocalPostSelection]）：长按任意列表里的帖子即可进入多选，
 * 不需要每个列表页各自接线；选中集合按 [PostKey] 记录，因此列表滚动、复用项回收都不会错位。
 */
class PostSelectionState(initial: Set<PostKey> = emptySet()) {

    var selected by mutableStateOf(initial)
        private set

    /**
     * 选中项对应的帖子本体：屏蔽帖子时要把标题 / 封面一起写进名单，
     * 屏蔽管理页才能用主页同款卡片展示。
     * （不参与状态保存，配置变更后只剩 key，影响仅限「卡片信息不如刚勾选时全」。）
     */
    val selectedPosts = mutableMapOf<PostKey, PostSummary>()

    /** 有选中项即处于多选模式。 */
    val active: Boolean get() = selected.isNotEmpty()

    val count: Int get() = selected.size

    fun isSelected(key: PostKey): Boolean = key in selected

    /** 长按 / 多选模式下点击：切换这一项的选中状态。 */
    fun toggle(key: PostKey, post: PostSummary? = null) {
        if (key in selected) {
            selected = selected - key
            selectedPosts.remove(key)
        } else {
            selected = selected + key
            if (post != null) selectedPosts[key] = post
        }
    }

    fun clear() {
        selected = emptySet()
        selectedPosts.clear()
    }

    companion object {
        /** 配置变更（旋转、切主题）后仍保留选中项。 */
        val Saver = listSaver<PostSelectionState, String>(
            save = { state -> state.selected.map { "${it.service}|${it.creatorId}|${it.postId}" } },
            restore = { saved ->
                PostSelectionState(
                    saved.mapNotNull { raw ->
                        val parts = raw.split("|")
                        if (parts.size == 3) PostKey(parts[0], parts[1], parts[2]) else null
                    }.toSet(),
                )
            },
        )
    }
}

/** 当前的多选状态；为 null 表示不在列表上下文里（卡片会退化成普通可点击卡片）。 */
val LocalPostSelection = staticCompositionLocalOf<PostSelectionState?> { null }

/**
 * 当前是否处于多选模式（有选中项）。
 *
 * 列表页用它在多选期间收起与多选无关的顶栏控件：排序、搜索、筛选标签、视图切换…
 * 多选时唯一该在场的操作，就是多选条里那三个（屏蔽作者 / 屏蔽帖子 / 取消）。
 *
 * 判定读的是根组件提供的 [LocalPostSelection]，所以「退出多选后恢复」是自动的 ——
 * 选中清空即 [PostSelectionState.active] 变 false，各页控件跟着回来，
 * 不需要任何页面自己记「之前是不是多选」。
 */
@Composable
fun isPostSelectionActive(): Boolean = LocalPostSelection.current?.active == true

/**
 * 多选期间收起一块与多选无关的界面（顶栏、信息栏…）：隐去内容，并**连同子控件一起失去响应**。
 *
 * 为什么不索性不渲染：这些块正被浮在上层的多选条压着，高度一旦塌掉，
 * 下面的列表就会滑到多选条底下被盖住。这里保留占位、只把内容隐去 —— 位置不变、看不到、也点不到。
 *
 * 「点不到」不能只靠 alpha（透明的东西照样能点）：还要在 [PointerEventPass.Initial] 阶段
 * 把按下事件消费掉。该阶段由父到子派发，子控件（按钮、搜索框）拿不到「未被消费的按下」，
 * 整块自然不再响应触摸 —— 这也是「只设透明」和「真正禁用」的区别所在。
 *
 * 顺带收起输入法：搜索框只是被隐去、焦点还在，不主动收键盘就会出现
 * 「看不见输入框却在打字」。
 *
 * 另加 [clearAndSetSemantics]：被隐去的控件不该继续留在无障碍树里 ——
 * 读屏用户走无障碍动作（不经过指针事件）照样能点到这些按钮。
 */
@Composable
fun Modifier.selectionChromeHidden(active: Boolean): Modifier {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(active) { if (active) focusManager.clearFocus() }
    return if (!active) {
        this
    } else {
        this
            .alpha(0f)
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
    }
}

/** 供根组件创建并记住多选状态（旋转 / 切主题不丢选中项）。 */
@Composable
fun rememberPostSelection(): PostSelectionState =
    rememberSaveable(saver = PostSelectionState.Saver) { PostSelectionState() }

/**
 * 给列表卡片加「多选」行为与视觉（`PostCard` 的两个分支各换一行即可）：
 *
 * - 长按：进入多选 / 取消这一项；多选模式下点击：切换选中，不再打开帖子；
 * - 选中项：淡主色遮罩 + 主色描边 + 右上角勾选框；
 * - 不在多选上下文（[LocalPostSelection] 为 null）时行为与原来的单击完全一致。
 */
@Composable
fun Modifier.postSelection(post: PostSummary, onClick: () -> Unit): Modifier {
    val selection = LocalPostSelection.current
    val colors = LocalPurrColors.current
    // 触感反馈用 Compose 的系统触感常量：不需要 VIBRATE 权限，
    // 也会自动跟随系统「触感反馈」开关（关掉就完全不震，不干扰正常交互）
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val key = post.selectionKey()
    val active = selection?.active == true
    val selected = selection?.isSelected(key) == true
    val density = LocalDensity.current
    val corner = with(density) { 12.dp.toPx() }
    val boxRadius = with(density) { 11.dp.toPx() }
    val inset = with(density) { 12.dp.toPx() }
    val stroke = with(density) { 2.dp.toPx() }

    return this
        .pointerInput(active, selected) {
            detectTapGestures(
                // 选中时把帖子本体一起带上，屏蔽后管理页才能用卡片把它展示出来
                onTap = { if (active) selection?.toggle(key, post) else onClick() },
                onLongPress = {
                    // 轻震一下确认「进入多选 / 取消这一项」；只有长按会震，点击不会
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                    )
                    selection?.toggle(key, post)
                },
            )
        }
        .drawWithContent {
            drawContent()
            if (selected) {
                // 明显的视觉反馈：淡色遮罩 + 主色描边
                drawRoundRect(
                    color = colors.primary.copy(alpha = 0.10f),
                    cornerRadius = CornerRadius(corner),
                )
                drawRoundRect(
                    color = colors.primary,
                    cornerRadius = CornerRadius(corner),
                    style = Stroke(width = stroke),
                )
            }
            if (active) {
                val cx = size.width - inset - boxRadius
                val cy = inset + boxRadius
                if (selected) {
                    drawCircle(color = colors.primary, radius = boxRadius, center = Offset(cx, cy))
                    val tick = Path().apply {
                        moveTo(cx - boxRadius * 0.45f, cy + boxRadius * 0.05f)
                        lineTo(cx - boxRadius * 0.10f, cy + boxRadius * 0.40f)
                        lineTo(cx + boxRadius * 0.50f, cy - boxRadius * 0.35f)
                    }
                    drawPath(
                        path = tick,
                        color = colors.onPrimary,
                        style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                } else {
                    drawCircle(
                        color = colors.surfaceContainerHigh,
                        radius = boxRadius,
                        center = Offset(cx, cy),
                    )
                }
            }
        }
}

/**
 * 多选模式的操作条（批量入口）：已选 N 项 +「屏蔽作者」/「屏蔽帖子」+「取消」。
 *
 * 两个屏蔽动作**互不干扰**：屏蔽作者会隐藏该作者的全部帖子，屏蔽帖子只隐藏这几篇。
 * 由根组件浮在最上层，所以四个列表页都不需要各自接线。
 */
@Composable
fun PostSelectionBar(
    count: Int,
    busy: Boolean = false,
    onBlock: () -> Unit,
    onBlockPosts: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Row(
        Modifier
            .fillMaxWidth()
            // 多选条浮在各页顶栏之上，但「画在上面」不等于「挡住触摸」：
            // 没有指针输入节点的区域，事件会穿到底下被压住的搜索 / 通知按钮上 —— 误触。
            // 挂一个只接不用的 pointerInput，让落在多选条范围内的触摸都停在它身上；
            // 条内按钮是子节点，Main 阶段先于父节点收到事件，所以不受影响。
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            "已选 $count 项",
            style = AppTextStyle.MAIN,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.primary,
                strokeWidth = 2.dp,
            )
        } else {
            BarAction("屏蔽作者", LucideIcons.close, colors.error, onBlock)
            BarAction("屏蔽帖子", LucideIcons.tasks, colors.error, onBlockPosts)
        }
        BarAction("取消", LucideIcons.check, colors.textSecondary, onCancel)
    }
}

@Composable
private fun BarAction(
    label: String,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val colors = LocalPurrColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        AppText(label, style = AppTextStyle.MAIN, color = colors.textPrimary, fontSize = 14.sp)
    }
}

/** 批量屏蔽的写入动作（根组件通过 hiltViewModel 取用）。 */
@HiltViewModel
class PostSelectionViewModel @Inject constructor(
    private val blockedCreators: BlockedCreatorManager,
    private val blockedPosts: com.archive.app.data.BlockedPostManager,
) : ViewModel() {

    /**
     * 屏蔽这些帖子所属的**作者**（同一作者只写一次），返回**本次新增**屏蔽的作者数，
     * 用于给用户一个明确的结果反馈。
     */
    suspend fun blockCreators(keys: Collection<PostKey>): Int =
        // 底层实现用 runBlocking 写 DataStore，必须挪到 IO 线程，否则卡主线程
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val creators = keys.map { it.service to it.creatorId }.distinct()
            var added = 0
            creators.forEach { (service, creatorId) ->
                if (!blockedCreators.isCreatorBlocked(service, creatorId)) {
                    blockedCreators.blockCreator(service, creatorId)
                    added++
                }
            }
            added
        }

    /**
     * 只屏蔽这些**帖子本身**（作者的其他帖子照常显示），返回新增屏蔽的帖子数。
     * 与 [blockCreators] 是两套独立名单，互不影响。
     *
     * 传进来的是帖子本体而不只是 key：标题 / 封面 / 发布时间要一并写进名单，
     * 屏蔽管理页才能用主页同款卡片把它们展示出来。
     */
    suspend fun blockPosts(posts: Collection<PostSummary>): Int {
        var added = 0
        posts.forEach { post ->
            if (!com.archive.app.data.BlockedContentFilter.isPostBlocked(
                    post.service,
                    post.creatorId,
                    post.postId,
                )
            ) {
                blockedPosts.blockPost(
                    service = post.service,
                    creatorId = post.creatorId,
                    postId = post.postId,
                    title = post.title,
                    coverPath = post.cover?.path,
                    published = post.published,
                    previewOnly = post.previewOnly,
                )
                added++
            }
        }
        return added
    }
}
