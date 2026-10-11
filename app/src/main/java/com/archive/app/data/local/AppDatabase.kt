package com.archive.app.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

@Entity(tableName = "creators", primaryKeys = ["service", "id"])
data class CreatorEntity(
    val service: String,
    val id: String,
    val name: String,
    val publicId: String?,
    val indexed: Long?,
    val updated: Long?,
    val favorited: Int,
    val everImported: Boolean,
)

@Entity(
    tableName = "favorite_posts",
    primaryKeys = ["service", "creatorId", "postId"],
)
data class FavoritePostEntity(
    val service: String,
    val creatorId: String,
    val creatorName: String,
    val postId: String,
    val title: String,
    val thumbPath: String?,
    val addedAt: Long,
)

@Entity(tableName = "history", primaryKeys = ["service", "creatorId", "postId"])
data class HistoryEntity(
    val service: String,
    val creatorId: String,
    val creatorName: String,
    val postId: String,
    val title: String,
    val thumbPath: String?,
    val viewedAt: Long,
)

@Dao
interface CreatorDao {
    @Upsert
    suspend fun upsertAll(items: List<CreatorEntity>)

    @Query("DELETE FROM creators")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM creators")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM creators WHERE name LIKE '%' || :query || '%' ORDER BY favorited DESC LIMIT 50",
    )
    suspend fun searchAll(query: String): List<CreatorEntity>

    @Query("SELECT * FROM creators WHERE service = :service AND id = :id LIMIT 1")
    suspend fun find(service: String, id: String): CreatorEntity?

    /** 名称或 ID 匹配（照 Pawchive：创作者搜索同时匹配名字与 id）。 */
    @Query(
        "SELECT * FROM creators WHERE (name LIKE '%' || :query || '%' OR id LIKE '%' || :query || '%') " +
            "ORDER BY favorited DESC LIMIT 200",
    )
    suspend fun searchByNameOrId(query: String): List<CreatorEntity>

    @Query("SELECT * FROM creators WHERE id = :id ORDER BY favorited DESC LIMIT 20")
    suspend fun byId(id: String): List<CreatorEntity>

    /**
     * 发现页本地作者库分页：排序键（`updated` / `favorited`）与升降序都做成参数，
     * 升序用「乘 -1 再 DESC」实现，省得给每种排序组合各写一条 SQL。
     *
     * [relevance] 为真时在最前面插一档**名称命中质量**（0 精确 > 1 前缀 > 2 其余包含，
     * 越小越靠前），同档内再用排序键兜底。相关度必须在 SQL 里算：本地库有 9 万+ 行且走
     * Paging，先取到内存再重排会把翻页顺序彻底打乱。用 `substr` 而非 `LIKE 'x%'`
     * 是为了让中日文名称的前缀判断同样准确（`LIKE` 对非 ASCII 不区分大小写，反而会误判）。
     */
    @Query(
        """
        SELECT * FROM creators
        WHERE (:service IS NULL OR service = :service)
          AND name LIKE '%' || :query || '%'
        ORDER BY
          (CASE WHEN :relevance AND :query <> '' THEN
             CASE
               WHEN LOWER(name) = LOWER(:query) THEN 0
               WHEN LOWER(substr(name, 1, length(:query))) = LOWER(:query) THEN 1
               ELSE 2
             END
           ELSE 0 END) ASC,
          (CASE WHEN :sortKey = 'favorited' THEN favorited ELSE COALESCE(updated, 0) END)
            * (CASE WHEN :ascending THEN -1 ELSE 1 END) DESC,
          id DESC
        """,
    )
    fun paging(
        service: String?,
        query: String,
        sortKey: String,
        ascending: Boolean,
        relevance: Boolean,
    ): PagingSource<Int, CreatorEntity>
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: FavoritePostEntity)

    @Query("DELETE FROM favorite_posts WHERE service = :service AND creatorId = :creatorId AND postId = :postId")
    suspend fun remove(service: String, creatorId: String, postId: String)

    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorite_posts WHERE service = :service AND creatorId = :creatorId AND postId = :postId)",
    )
    fun isFavorite(service: String, creatorId: String, postId: String): kotlinx.coroutines.flow.Flow<Boolean>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM favorite_posts WHERE service = :service AND creatorId = :creatorId AND postId = :postId)",
    )
    suspend fun existsOnce(service: String, creatorId: String, postId: String): Boolean

    @Query("SELECT * FROM favorite_posts ORDER BY addedAt DESC")
    fun observeAll(): PagingSource<Int, FavoritePostEntity>

    @Query("DELETE FROM favorite_posts")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(item: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY viewedAt DESC")
    fun observeAll(): PagingSource<Int, HistoryEntity>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE service = :service AND creatorId = :creatorId AND postId = :postId")
    suspend fun remove(service: String, creatorId: String, postId: String)
}

@Database(
    entities = [CreatorEntity::class, FavoritePostEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun creatorDao(): CreatorDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
}
