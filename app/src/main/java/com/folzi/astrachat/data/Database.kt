package com.folzi.astrachat.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "chats")
data class ChatRow(@PrimaryKey val id: String, val title: String = "Новый чат", val updated: Long = System.currentTimeMillis(), val pinned: Boolean = false, val providerId: String = "", val modelId: String = "", @ColumnInfo(defaultValue = "") val mcpServerIds: String = "")
@Serializable
@Entity(tableName = "messages", indices = [Index("chatId")], foreignKeys = [ForeignKey(entity = ChatRow::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)])
data class MessageRow(@PrimaryKey val id: String, val chatId: String, val position: Long, val role: String, val text: String, val state: String = "complete", val errorCategory: String = "", @ColumnInfo(defaultValue = "") val toolCalls: String = "", @ColumnInfo(defaultValue = "") val toolCallId: String = "", @ColumnInfo(defaultValue = "") val toolName: String = "", @ColumnInfo(defaultValue = "") val attachments: String = "")
@Entity(tableName = "providers")
data class ProviderRow(@PrimaryKey val id: String, val metadata: String)
@Entity(tableName = "models", primaryKeys = ["providerId", "modelId"], indices = [Index("providerId")], foreignKeys = [ForeignKey(entity = ProviderRow::class, parentColumns = ["id"], childColumns = ["providerId"], onDelete = ForeignKey.CASCADE)])
data class ModelRow(val providerId: String, val modelId: String, val metadata: String)
@Entity(tableName = "mcp_servers")
data class McpServerRow(@PrimaryKey val id: String, val metadata: String)
@Entity(tableName = "drafts", foreignKeys = [ForeignKey(entity = ChatRow::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)])
data class DraftRow(@PrimaryKey val chatId: String, val text: String)
@Serializable
@Entity(tableName = "branches")
data class BranchRow(@PrimaryKey val id: String, val originChatId: String, val originMessageId: String, val targetChatId: String)
@Entity(tableName = "usage", indices = [Index("chatId"), Index("timestamp")])
data class UsageRow(
    @PrimaryKey val requestId: String, val chatId: String, val providerId: String, val modelId: String,
    val timestamp: Long, val input: Long, val output: Long, val total: Long,
    val reasoning: Long?, val cached: Long?, val estimated: Boolean,
    val ttftMs: Double?, val durationMs: Long, val tokensPerSecond: Double, val cost: Double?,
    @ColumnInfo(defaultValue = "'complete'") val state: String = "complete",
)
@Dao
interface AstraDao {
    @Query("SELECT * FROM chats ORDER BY pinned DESC, updated DESC") fun chats(): Flow<List<ChatRow>>
    @Query("SELECT * FROM messages WHERE chatId=:id ORDER BY position, id") fun messages(id: String): Flow<List<MessageRow>>
    @Query("SELECT * FROM providers ORDER BY id") fun providers(): Flow<List<ProviderRow>>
    @Query("SELECT * FROM models ORDER BY providerId, modelId") fun models(): Flow<List<ModelRow>>
    @Query("SELECT * FROM mcp_servers ORDER BY id") fun mcpServers(): Flow<List<McpServerRow>>
    @Query("SELECT * FROM usage ORDER BY timestamp DESC") fun usage(): Flow<List<UsageRow>>
    @Query("SELECT * FROM drafts WHERE chatId=:id") suspend fun draft(id: String): DraftRow?
    @Query("SELECT * FROM messages WHERE chatId=:id ORDER BY position, id") suspend fun history(id: String): List<MessageRow>
    @Query("SELECT * FROM chats WHERE id=:id") suspend fun chat(id: String): ChatRow?
    @Query("SELECT * FROM chats ORDER BY updated DESC") suspend fun allChats(): List<ChatRow>
    @Query("SELECT * FROM messages ORDER BY chatId, position, id") suspend fun allMessages(): List<MessageRow>
    @Query("SELECT * FROM branches") suspend fun allBranches(): List<BranchRow>
    @Query("SELECT * FROM providers") suspend fun allProviders(): List<ProviderRow>
    @Query("SELECT * FROM mcp_servers") suspend fun allMcpServers(): List<McpServerRow>
    @Upsert suspend fun saveChat(row: ChatRow)
    @Upsert suspend fun saveMessage(row: MessageRow)
    @Upsert suspend fun saveProvider(row: ProviderRow)
    @Upsert suspend fun saveModel(row: ModelRow)
    @Upsert suspend fun saveMcpServer(row: McpServerRow)
    @Upsert suspend fun saveUsage(row: UsageRow)
    @Upsert suspend fun saveDraft(row: DraftRow)
    @Upsert suspend fun saveBranch(row: BranchRow)
    @Query("DELETE FROM chats WHERE id=:id") suspend fun deleteChat(id: String)
    @Query("DELETE FROM branches WHERE originChatId=:id OR targetChatId=:id") suspend fun deleteBranches(id: String)
    @Query("DELETE FROM messages WHERE id=:id") suspend fun deleteMessage(id: String)
    @Query("DELETE FROM providers WHERE id=:id") suspend fun deleteProvider(id: String)
    @Query("DELETE FROM models WHERE providerId=:provider AND modelId=:model") suspend fun deleteModel(provider: String, model: String)
    @Query("DELETE FROM mcp_servers WHERE id=:id") suspend fun deleteMcpServer(id: String)
    @Query("DELETE FROM chats") suspend fun clearChats()
    @Query("DELETE FROM branches") suspend fun clearBranches()
    @Query("DELETE FROM usage") suspend fun clearUsage()
    @Query("UPDATE messages SET state='interrupted' WHERE state='generating'") suspend fun recoverMessages()
    @Query("UPDATE usage SET state='interrupted', estimated=1 WHERE state='generating'") suspend fun recoverUsage()
}
@Database(entities = [ChatRow::class, MessageRow::class, ProviderRow::class, ModelRow::class, DraftRow::class, BranchRow::class, UsageRow::class, McpServerRow::class], version = 5, exportSchema = true)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun dao(): AstraDao
    companion object {
        // v1 contained all tables above, but did not persist usage request state.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage ADD COLUMN state TEXT NOT NULL DEFAULT 'complete'")
            }
        }
        // v2 had no MCP support; v3 adds the additive mcp_servers table (public metadata JSON only).
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `mcp_servers` (`id` TEXT NOT NULL, `metadata` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        // v3 had no tool-calling columns; v4 adds per-chat MCP selection and tool-round persistence.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN mcpServerIds TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCalls TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolCallId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN toolName TEXT NOT NULL DEFAULT ''")
            }
        }
        // v4 had no attachment metadata; v5 adds the additive messages.attachments column (JSON list, no file bytes).
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
