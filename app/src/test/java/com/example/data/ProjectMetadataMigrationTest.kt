package com.example.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectMetadataMigrationTest {
    @Test fun upgradingProtectsExistingManualSettingsAndLeavesUnsetProjectsSyncable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE projects (id TEXT PRIMARY KEY, baseDate TEXT NOT NULL, companyName TEXT NOT NULL, reportType TEXT NOT NULL)")
                    db.execSQL("INSERT INTO projects VALUES ('edited', '2025-01-01', '本地单位', '咨询报告')")
                    db.execSQL("INSERT INTO projects VALUES ('empty', '', '', '评估报告')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        try {
            val db = helper.writableDatabase
            AppDatabase.MIGRATION_7_8.migrate(db)
            db.query("SELECT metadataLocallyEdited, baseDate, companyName FROM projects WHERE id = 'edited'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
                assertEquals("2025-01-01", it.getString(1))
                assertEquals("本地单位", it.getString(2))
            }
            db.query("SELECT metadataLocallyEdited FROM projects WHERE id = 'empty'").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        } finally { helper.close() }
    }
}
