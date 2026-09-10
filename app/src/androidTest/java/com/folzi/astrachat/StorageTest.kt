package com.folzi.astrachat

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
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
    // Recreate chats/messages in their exact v3 shape (DDL from schemas/3.json), preserving data,
    // so additive ALTER TABLE migrations can run against a genuine older-version database.
    // SQLite on API 26 has no DROP COLUMN, and Room cannot downgrade schema by itself.
    private fun downgradeChatTablesToV3(sql: SupportSQLiteDatabase) {
        sql.execSQL("PRAGMA foreign_keys=OFF")
        sql.execSQL("CREATE TABLE chats_old AS SELECT id,title,updated,pinned,providerId,modelId FROM chats")
        sql.execSQL("CREATE TABLE messages_old AS SELECT id,chatId,position,role,text,state,errorCategory FROM messages")
        sql.execSQL("DROP TABLE messages")
        sql.execSQL("DROP TABLE chats")
        sql.execSQL("CREATE TABLE IF NOT EXISTS `chats` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `updated` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `providerId` TEXT NOT NULL, `modelId` TEXT NOT NULL, PRIMARY KEY(`id`))")
        sql.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `chatId` TEXT NOT NULL, `position` INTEGER NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `state` TEXT NOT NULL, `errorCategory` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sql.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
        sql.execSQL("INSERT INTO chats SELECT * FROM chats_old")
        sql.execSQL("INSERT INTO messages SELECT * FROM messages_old")
        sql.execSQL("DROP TABLE chats_old")
        sql.execSQL("DROP TABLE messages_old")
        sql.execSQL("PRAGMA foreign_keys=ON")
    }
    // Recreate messages in their exact v4 shape (DDL from schemas/4.json); v5 only adds attachments.
    private fun downgradeMessagesToV4(sql: SupportSQLiteDatabase) {
        sql.execSQL("PRAGMA foreign_keys=OFF")
        sql.execSQL("CREATE TABLE messages_old AS SELECT id,chatId,position,role,text,state,errorCategory,toolCalls,toolCallId,toolName FROM messages")
        sql.execSQL("DROP TABLE messages")
        sql.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `chatId` TEXT NOT NULL, `position` INTEGER NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `state` TEXT NOT NULL, `errorCategory` TEXT NOT NULL, `toolCalls` TEXT NOT NULL, `toolCallId` TEXT NOT NULL, `toolName` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`chatId`) REFERENCES `chats`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        sql.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_chatId` ON `messages` (`chatId`)")
        sql.execSQL("INSERT INTO messages SELECT * FROM messages_old")
        sql.execSQL("DROP TABLE messages_old")
        sql.execSQL("PRAGMA foreign_keys=ON")
    }
    @Test fun branchesAndBackupsDoNotOverwriteOriginals() = runBlocking<Unit> {
        // Room 2.8 RoomDatabase no longer implements Closeable, so kotlin.use does not apply.
        val db = Room.inMemoryDatabaseBuilder(context, AstraDatabase::class.java).build()
        try {
            val repo = ChatRepository(db); repo.initialize()
            val id = repo.createChat("openai", "model")
            val answer = repo.appendExchange(id, "original", "openai", "model")
            db.dao().saveMessage(answer.copy(text = "answer", state = "complete"))
            val branch = repo.branch(id, answer.id, true)
            assertEquals(2, db.dao().history(id).size); assertEquals(2, db.dao().history(branch).size)
            val backup = repo.exportJson()
            assertFalse(backup.contains("Credentials")); assertFalse(backup.contains("api_key"))
            assertEquals(2, repo.importJson(backup)); assertEquals(4, db.dao().allChats().size)
        } finally { db.close() }
    }
    // JUnit4 requires void test methods: runBlocking<Unit> keeps the inferred return type Unit
    // even though the last statement (deleteDatabase) returns Boolean.
    @Test fun migrationPreservesUsageAndAddsState() = runBlocking<Unit> {
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
        downgradeChatTablesToV3(sql)
        sql.execSQL("PRAGMA user_version=1"); original.close()
        val migrated = Room.databaseBuilder(context, AstraDatabase::class.java, name)
            .addMigrations(AstraDatabase.MIGRATION_1_2, AstraDatabase.MIGRATION_2_3, AstraDatabase.MIGRATION_3_4, AstraDatabase.MIGRATION_4_5).build()
        try {
            migrated.openHelper.readableDatabase.query("SELECT total,state FROM usage WHERE requestId='request'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(30, cursor.getInt(0)); assertEquals("complete", cursor.getString(1))
            }
        } finally { migrated.close() }
        context.deleteDatabase(name)
    }
    @Test fun migrationV2ToV3AddsMcpServersAndPreservesData() = runBlocking<Unit> {
        val name = "migration-v3-test.db"; context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, AstraDatabase::class.java, name).build()
        original.dao().saveChat(ChatRow("chat-1", "История"))
        // v3 differs from v2 only by the additive mcp_servers table: remove it and mark the file as v2.
        val sql = original.openHelper.writableDatabase
        sql.execSQL("DROP TABLE mcp_servers")
        downgradeChatTablesToV3(sql)
        sql.execSQL("PRAGMA user_version=2"); original.close()
        val migrated = Room.databaseBuilder(context, AstraDatabase::class.java, name)
            .addMigrations(AstraDatabase.MIGRATION_1_2, AstraDatabase.MIGRATION_2_3, AstraDatabase.MIGRATION_3_4, AstraDatabase.MIGRATION_4_5).build()
        try {
            assertEquals("История", migrated.dao().chat("chat-1")?.title)
            migrated.dao().saveMcpServer(McpServerRow("server-1", """{"id":"server-1","name":"Test","url":"https://example.com/mcp"}"""))
            assertEquals(listOf("server-1"), migrated.dao().allMcpServers().map { it.id })
        } finally { migrated.close() }
        context.deleteDatabase(name)
    }
    @Test fun migrationV3ToV4AddsToolColumnsAndPreservesData() = runBlocking<Unit> {
        val name = "migration-v4-test.db"; context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, AstraDatabase::class.java, name).build()
        original.dao().saveChat(ChatRow("chat-1", "История", mcpServerIds = "srv-1"))
        original.dao().saveMessage(MessageRow("m-1", "chat-1", 0, "tool", "Sunny", "complete", toolCallId = "call-1", toolName = "get_weather"))
        // v4 differs from v3 only by four additive TEXT columns: recreate the v3 shape and mark the file as v3.
        val sql = original.openHelper.writableDatabase
        downgradeChatTablesToV3(sql)
        sql.execSQL("PRAGMA user_version=3"); original.close()
        val migrated = Room.databaseBuilder(context, AstraDatabase::class.java, name)
            .addMigrations(AstraDatabase.MIGRATION_1_2, AstraDatabase.MIGRATION_2_3, AstraDatabase.MIGRATION_3_4, AstraDatabase.MIGRATION_4_5).build()
        try {
            val chat = migrated.dao().chat("chat-1")
            assertEquals("История", chat?.title); assertEquals("", chat?.mcpServerIds)
            val message = migrated.dao().history("chat-1").single()
            assertEquals("Sunny", message.text); assertEquals("tool", message.role)
            assertEquals("", message.toolCallId); assertEquals("", message.toolName); assertEquals("", message.toolCalls)
        } finally { migrated.close() }
        context.deleteDatabase(name)
    }
    @Test fun migrationV4ToV5AddsAttachmentsAndPreservesData() = runBlocking<Unit> {
        val name = "migration-v5-test.db"; context.deleteDatabase(name)
        val original = Room.databaseBuilder(context, AstraDatabase::class.java, name).build()
        original.dao().saveChat(ChatRow("chat-1", "Чат"))
        original.dao().saveMessage(MessageRow("msg-1", "chat-1", 0, "user", "Файл?", attachments = """[{"id":"a1","name":"photo.png","mimeType":"image/png","sizeBytes":3}]"""))
        assertTrue(original.dao().history("chat-1").single().attachments.contains("photo.png"))
        original.close()
        val downgrade = Room.databaseBuilder(context, AstraDatabase::class.java, name)
            .addMigrations(AstraDatabase.MIGRATION_1_2, AstraDatabase.MIGRATION_2_3, AstraDatabase.MIGRATION_3_4, AstraDatabase.MIGRATION_4_5)
            .build()
        downgrade.openHelper.writableDatabase.apply {
            downgradeMessagesToV4(this)
            execSQL("PRAGMA user_version=4")
        }
        downgrade.close()
        val migrated = Room.databaseBuilder(context, AstraDatabase::class.java, name)
            .addMigrations(AstraDatabase.MIGRATION_1_2, AstraDatabase.MIGRATION_2_3, AstraDatabase.MIGRATION_3_4, AstraDatabase.MIGRATION_4_5)
            .build()
        try {
            val message = migrated.dao().history("chat-1").single()
            // Text survives the surgical v4 downgrade; the attachments column returns with its empty default.
            assertEquals("Файл?", message.text); assertEquals("user", message.role)
            assertEquals("", message.attachments)
        } finally { migrated.close() }
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
