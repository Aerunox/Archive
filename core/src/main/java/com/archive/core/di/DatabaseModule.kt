package com.archive.core.di

import android.content.Context
import androidx.room.Room
import com.archive.core.db.ContentUpdateDao
import com.archive.core.db.PendingFavoriteActionDao
import com.archive.core.db.CreatorSubscriptionDao
import com.archive.core.db.DownloadHistoryDao
import com.archive.core.db.DownloadRuleDao
import com.archive.core.db.PawchiveDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room 数据库提供模块（ARCH-004；ARCH-005：显式 Migration 替代 destructive 迁移）。
 *
 * schema 变更必须提供显式 [androidx.room.migration.Migration]（见 PawchiveDatabase），
 * 禁止回退到 fallbackToDestructiveMigration，防止下载历史等本地数据丢失。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PawchiveDatabase {
        return Room.databaseBuilder(context, PawchiveDatabase::class.java, PawchiveDatabase.NAME)
            .addMigrations(
                PawchiveDatabase.MIGRATION_1_2,
                PawchiveDatabase.MIGRATION_2_3,
                PawchiveDatabase.MIGRATION_3_4,
                PawchiveDatabase.MIGRATION_4_5,
                PawchiveDatabase.MIGRATION_5_6,
                PawchiveDatabase.MIGRATION_6_7,
            PawchiveDatabase.MIGRATION_7_8,
            PawchiveDatabase.MIGRATION_8_9
            )
            .build()
    }

    @Provides
    fun provideDownloadHistoryDao(db: PawchiveDatabase): DownloadHistoryDao {
        return db.downloadHistoryDao()
    }

    @Provides
    fun providePendingFavoriteActionDao(db: PawchiveDatabase): PendingFavoriteActionDao {
        return db.pendingFavoriteActionDao()
    }

    @Provides
    fun provideDownloadRuleDao(db: PawchiveDatabase): DownloadRuleDao {
        return db.downloadRuleDao()
    }

    @Provides
    fun provideCreatorSubscriptionDao(db: PawchiveDatabase): CreatorSubscriptionDao {
        return db.creatorSubscriptionDao()
    }

    @Provides
    fun provideContentUpdateDao(db: PawchiveDatabase): ContentUpdateDao {
        return db.contentUpdateDao()
    }
}
