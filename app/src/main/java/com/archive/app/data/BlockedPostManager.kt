package com.archive.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// 与「屏蔽作者」分开的另一个 DataStore：两套名单互不干扰
private val Context.blockedPostsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "blocked_posts")

/**
 * 一条被屏蔽的帖子记录。
 *
 * 存下来的字段是为了让「屏蔽管理」页能用**主页同款卡片**展示它
 * （`PostSummary` 需要标题、封面、发布时间）。
 */
@Serializable
data class BlockedPost(
    val service: String,
    val creatorId: String,
    val postId: String,
    val title: String? = null,
    /** 缩略图路径，用来还原卡片封面。 */
    val coverPath: String? = null,
    val published: String? = null,
    /** 站点只收录预览时，卡片上仍要显示遮罩。 */
    val previewOnly: Boolean = false,
)

/**
 * 「屏蔽帖子」名单。
 *
 * 与 [com.archive.data.repository.BlockedCreatorManager]（屏蔽作者）是**两套独立机制**：
 * 分开存储、分开判断，屏蔽帖子不会影响该作者的其他帖子，屏蔽作者也不会写进这里。
 */
@Singleton
class BlockedPostManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val dataStore = context.blockedPostsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    /** 已屏蔽的帖子列表（供屏蔽管理页展示）。 */
    val blockedPostsFlow: Flow<List<BlockedPost>> = dataStore.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { entry ->
            val name = entry.key.name
            if (!name.startsWith(KEY_PREFIX)) return@mapNotNull null
            val parts = name.removePrefix(KEY_PREFIX).split("|")
            if (parts.size != 3) return@mapNotNull null
            val raw = entry.value as? String ?: ""
            // 旧版本只存了标题（纯文本），解码失败时按标题兼容
            val decoded = runCatching { json.decodeFromString<BlockedPost>(raw) }.getOrNull()
            decoded ?: BlockedPost(
                service = parts[0],
                creatorId = parts[1],
                postId = parts[2],
                title = raw.takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun blockPost(
        service: String,
        creatorId: String,
        postId: String,
        title: String?,
        coverPath: String? = null,
        published: String? = null,
        previewOnly: Boolean = false,
    ) {
        val record = BlockedPost(
            service = service,
            creatorId = creatorId,
            postId = postId,
            title = title,
            coverPath = coverPath,
            published = published,
            previewOnly = previewOnly,
        )
        dataStore.edit { prefs -> prefs[keyOf(service, creatorId, postId)] = json.encodeToString(record) }
    }

    suspend fun unblockPost(service: String, creatorId: String, postId: String) {
        dataStore.edit { prefs -> prefs.remove(keyOf(service, creatorId, postId)) }
    }

    private fun keyOf(service: String, creatorId: String, postId: String) =
        stringPreferencesKey("$KEY_PREFIX$service|$creatorId|$postId")

    private companion object {
        const val KEY_PREFIX = "blocked_post_"
    }
}
