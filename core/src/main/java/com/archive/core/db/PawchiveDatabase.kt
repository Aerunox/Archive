package com.archive.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.archive.core.model.ContentUpdateEntity
import com.archive.core.model.CreatorSubscriptionEntity
import com.archive.core.model.DownloadRecord
import com.archive.core.model.DownloadRuleEntity
import com.archive.core.model.PendingFavoriteActionEntity

/**
 * Pawchive 本地数据库（ARCH-004：下载历史存储迁移至 Room；ARCH-005：显式 Migration；
 * ARCH-FEATURE-001：离线归档与全文搜索索引【表已于 v9 移除，见 MIGRATION_8_9】；
 * ARCH-FEATURE-002：下载规则表；ARCH-FEATURE-003：创作者订阅与内容更新通知表）。
 *
 * schema 变更必须提供显式 [androidx.room.migration.Migration]（见 companion），
 * 禁止回退到 fallbackToDestructiveMigration，防止本地数据丢失。
 */
@Database(
    entities = [
        DownloadRecord::class,
        DownloadRuleEntity::class,
        CreatorSubscriptionEntity::class,
        ContentUpdateEntity::class,
        PendingFavoriteActionEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class PawchiveDatabase : RoomDatabase() {

    abstract fun downloadHistoryDao(): DownloadHistoryDao

    abstract fun pendingFavoriteActionDao(): PendingFavoriteActionDao

    abstract fun downloadRuleDao(): DownloadRuleDao

    abstract fun creatorSubscriptionDao(): CreatorSubscriptionDao

    abstract fun contentUpdateDao(): ContentUpdateDao

    companion object {
        const val NAME = "pawchive.db"

        /**
         * v1 → v2（ARCH-005）：下载主键从 url.hashCode() 迁移为 UUID。
         * 新增 dedupKey 去重指纹列与索引；旧记录保留原 id（url），dedupKey 为 null。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_records ADD COLUMN dedupKey TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_download_records_dedupKey ON download_records(dedupKey)")
            }
        }

        /**
         * v2 → v3（ARCH-FEATURE-001）：新增离线归档表 + FTS4 全文索引表。
         * 建表 SQL 与 Room schema 保持一致（FTS 表默认 simple tokenizer，
         * CJK 分词由应用层 bigram 预处理）。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS offline_archives (
                        id TEXT NOT NULL PRIMARY KEY,
                        service TEXT NOT NULL,
                        creatorId TEXT NOT NULL,
                        postId TEXT NOT NULL,
                        title TEXT,
                        contentText TEXT,
                        userName TEXT,
                        attachmentsText TEXT,
                        postJson TEXT NOT NULL,
                        favedAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS offline_archives_fts USING fts4(
                        entryId, title, content, userName, user, attachments
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v3 → v4（ARCH-FEATURE-002）：新增下载规则表。
         * 建表 SQL 与 Room schema 保持一致（fileType 以字符串存枚举名）。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS download_rules (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        creatorId TEXT,
                        service TEXT,
                        fileType TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        /**
         * v4 → v5（ARCH-FEATURE-003）：新增创作者订阅表 + 内容更新通知表。
         * 建表 SQL 与 Room schema 保持一致（复合主键、唯一索引、read 标记）。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS creator_subscriptions (
                        service TEXT NOT NULL,
                        creatorId TEXT NOT NULL,
                        name TEXT,
                        lastPostId TEXT,
                        subscribedAt INTEGER NOT NULL,
                        PRIMARY KEY(service, creatorId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_creator_subscriptions_service ON creator_subscriptions(service)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS content_updates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        service TEXT NOT NULL,
                        creatorId TEXT NOT NULL,
                        postId TEXT NOT NULL,
                        postTitle TEXT,
                        discoveredAt INTEGER NOT NULL,
                        read INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_content_updates_service ON content_updates(service)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_content_updates_service_creatorId_postId " +
                        "ON content_updates(service, creatorId, postId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_content_updates_read ON content_updates(read)"
                )
            }
        }

        /**
         * v5 → v6（FEAT-RESUME-RESTART）：下载记录新增断点续传检查点两列。
         * temp_offset = 临时文件已写字节数，temp_total = 对应的完整文件总长（0 = 未知）。
         * 既有记录缺省 0，语义为"无断点"，行为与升级前一致。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE download_records ADD COLUMN tempOffset INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE download_records ADD COLUMN tempTotal INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v6 → v7（FEAT-OFFLINE-FAV-QUEUE）：新增离线收藏动作队列表。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_favorite_actions (
                        objectKey TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        service TEXT NOT NULL,
                        creatorId TEXT NOT NULL,
                        postId TEXT,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(objectKey)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * v7 → v8（作者主页关注/提醒改造）：订阅行新增两个提醒开关。
         *
         * 默认 1（开启）—— 既有订阅的提醒行为与升级前完全一致，不会因为升级静默丢掉提醒。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE creator_subscriptions " +
                        "ADD COLUMN notifyEnabled INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE creator_subscriptions " +
                        "ADD COLUMN pushEnabled INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * v8 → v9（离线归档退役）：移除 offline_archives + offline_archives_fts 两张表。
         *
         * 这套 Room FTS4 归档从未有调用方接入——实际生效的离线归档一直是 BookmarkManager
         * 的 DataStore 收藏全文缓存（收藏帖打开详情页时写入，全文搜索/离线阅读都走它）。
         * 两张表自建库以来始终为空，DROP 不丢任何数据；显式迁移而非 destructive，
         * 与本项目"禁止 destructive 回退"的约定一致。
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS offline_archives_fts")
                db.execSQL("DROP TABLE IF EXISTS offline_archives")
            }
        }
    }
}
