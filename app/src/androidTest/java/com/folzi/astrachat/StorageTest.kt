package com.folzi.astrachat

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.folzi.astrachat.core.Credentials
import com.folzi.astrachat.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun branchesAndBackupsDoNotOverwriteOriginals() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, AstraDatabase::class.java).build().use { db ->
            val repo = ChatRepository(db); repo.initialize()
            val id = repo.createChat("openai", "model")
            val answer = repo.appendExchange(id, "original", "openai", "model")
            db.dao().saveMessage(answer.copy(text = "answer", state = "complete"))
            val branch = repo.branch(id, answer.id, true)
            assertEquals(2, db.dao().history(id).size); assertEquals(2, db.dao().history(branch).size)
            val backup = repo.exportJson()
            assertFalse(backup.contains("Credentials")); assertFalse(backup.contains("api_key"))
            assertEquals(2, repo.importJson(backup)); assertEquals(4, db.dao().allChats().size)
        }
    }
    @Test fun migrationPreservesUsageAndAddsState() = runBlocking {
        val name = "migration-test.db"; context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, AstraDatabase::class.java, name).build()
        val dao = original.dao()
        val usage = UsageRow("request", "chat", "provider", "model", 1, 10, 20, 30, null, null, false, 100.0, 1000, 22.0, null)
        dao.saveUsage(usage)
        // Recreate the actual v1 usage table from v2, preserving every historical column.
        val sql = original.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE usage_v1 AS SELECT requestId,chatId,providerId,modelId,timestamp,input,output,total,reasoning,cached,estimated,ttftMs,durationMs,tokensPerSecond,cost FROM usage")
        sql.execSQL("DROP TABLE usage")
        sql.execSQL("CREATE TABLE usage (requestId TEXT NOT NULL PRIMARY KEY, chatId TEXT NOT NULL, providerId TEXT NOT NULL, modelId TEXT NOT NULL, timestamp INTEGER NOT NULL, input INTEGER NOT NULL, output INTEGER NOT NULL, total INTEGER NOT NULL, reasoning INTEGER, cached INTEGER, estimated INTEGER NOT NULL, ttftMs REAL, durationMs INTEGER NOT NULL, tokensPerSecond REAL NOT NULL, cost REAL)")
        sql.execSQL("INSERT INTO usage SELECT * FROM usage_v1"); sql.execSQL("DROP TABLE usage_v1")
        sql.execSQL("CREATE INDEX index_usage_chatId ON usage(chatId)"); sql.execSQL("CREATE INDEX index_usage_timestamp ON usage(timestamp)")
        sql.execSQL("PRAGMA user_version=1"); original.close()
        Room.databaseBuilder(context, AstraDatabase::class.java, name).addMigrations(AstraDatabase.MIGRATION_1_2).build().use { migrated ->
            migrated.openHelper.readableDatabase.query("SELECT total,state FROM usage WHERE requestId='request'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(30, cursor.getInt(0)); assertEquals("complete", cursor.getString(1))
            }
        }
        context.deleteDatabase(name)
    }
    @Test fun vaultCiphertextIsNotPlaintextAndCanBeRemoved() {
        val vault = SecretVault(context); val id = "instrumentation-vault-test"
        try {
            vault.write(id, Credentials("local-test-value"))
            val disk = context.getSharedPreferences("encrypted_credentials", android.content.Context.MODE_PRIVATE).getString(id, "")!!
            assertFalse(disk.contains("local-test-value")); assertEquals("local-test-value", vault.read(id).key)
            vault.delete(id); assertEquals("", vault.read(id).key)
        } finally { vault.delete(id) }
    }
}
