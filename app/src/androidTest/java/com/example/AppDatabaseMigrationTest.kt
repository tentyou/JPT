package com.example

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate5To7KeepsLegacyRowsAndAddsAssetMetadata() {
        val db = helper.createDatabase("migration-test", 5)
        db.execSQL("INSERT INTO projects VALUES ('p1','旧项目','','','','[]',1,1,1,1,1,1,1)")
        db.execSQL("INSERT INTO stock_items VALUES ('s1','旧资产','','','A-1',1,'已生成','p1',1,'[]',1)")
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            "migration-test",
            7,
            true,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7
        )
        migrated.query("SELECT COUNT(*) FROM projects").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM stock_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("PRAGMA table_info(remote_asset_bindings)").use { cursor ->
            val columns = mutableSetOf<String>()
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            assertTrue("assetCode column missing", "assetCode" in columns)
            assertTrue("assetName column missing", "assetName" in columns)
        }
        migrated.close()
    }
}