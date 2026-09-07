package com.folzi.astrachat.data

import androidx.room.withTransaction
import com.folzi.astrachat.core.*
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

fun newId(): String = UUID.randomUUID().toString()
@Serializable
data class Backup(val version: Int = 1, val chats: List<ChatRow>, val messages: List<MessageRow>, val branches: List<BranchRow>)
@Singleton
class ChatRepository @Inject constructor(val db: AstraDatabase) {
    val dao = db.dao()
    val chats = dao.chats()
    val providers = dao.providers().map { rows -> rows.map { json.decodeFromString<Provider>(it.metadata) } }
    val models = dao.models().map { rows -> rows.map { json.decodeFromString<Model>(it.metadata) } }
    val usage = dao.usage()
    suspend fun initialize() = db.withTransaction {
        dao.recoverMessages(); dao.recoverUsage()
        if (dao.allProviders().isEmpty()) Profiles.defaults.forEach { saveProvider(it) }
    }
    suspend fun createChat(provider: String, model: String): String {
        val id = newId(); dao.saveChat(ChatRow(id = id, providerId = provider, modelId = model)); return id
    }
    suspend fun saveProvider(p: Provider) { dao.saveProvider(ProviderRow(p.id, json.encodeToString(p))) }
    suspend fun saveModel(m: Model) { dao.saveModel(ModelRow(m.providerId, m.id, json.encodeToString(m))) }
    suspend fun removeChat(id: String) = db.withTransaction { dao.deleteBranches(id); dao.deleteChat(id) }
    suspend fun clearChats() = db.withTransaction { dao.clearBranches(); dao.clearChats() }
    suspend fun appendExchange(chatId: String, input: String, providerId: String, modelId: String): MessageRow = db.withTransaction {
        val chat = requireNotNull(dao.chat(chatId))
        val position = (dao.history(chatId).lastOrNull()?.position ?: -1) + 1
        dao.saveMessage(MessageRow(newId(), chatId, position, "user", input))
        val assistant = MessageRow(newId(), chatId, position + 1, "assistant", "", "generating")
        dao.saveMessage(assistant); dao.saveDraft(DraftRow(chatId, ""))
        dao.saveChat(chat.copy(title = if (chat.title == "Новый чат") input.take(60) else chat.title,
            providerId = providerId, modelId = modelId, updated = System.currentTimeMillis()))
        assistant
    }
    suspend fun checkpoint(message: MessageRow, usage: UsageRow) = db.withTransaction {
        dao.saveMessage(message); dao.saveUsage(usage)
    }
    suspend fun branch(source: String, messageId: String, inclusive: Boolean): String = db.withTransaction {
        val sourceChat = requireNotNull(dao.chat(source))
        val history = dao.history(source)
        val end = history.indexOfFirst { it.id == messageId }
        require(end >= 0)
        val id = newId()
        dao.saveChat(sourceChat.copy(id = id, title = sourceChat.title.take(45) + " · ветка", updated = System.currentTimeMillis()))
        history.take(end + if (inclusive) 1 else 0).forEach { dao.saveMessage(it.copy(id = newId(), chatId = id)) }
        dao.saveBranch(BranchRow(newId(), source, messageId, id))
        id
    }
    suspend fun exportJson(): String = db.withTransaction { json.encodeToString(Backup(chats = dao.allChats(), messages = dao.allMessages(), branches = dao.allBranches())) }
    suspend fun exportMarkdown(): String = db.withTransaction {
        val messages = dao.allMessages().groupBy { it.chatId }
        dao.allChats().joinToString("\n\n---\n\n") { chat ->
            "# ${chat.title.replace('\n', ' ')}\n\n" + messages[chat.id].orEmpty().joinToString("\n\n") { "## ${if (it.role == "user") "Вы" else "Astra"}\n\n${it.text}" }
        }
    }
    suspend fun importJson(text: String): Int {
        require(text.length <= 20 * 1024 * 1024)
        val backup = json.decodeFromString<Backup>(text)
        require(backup.version == 1 && backup.chats.size <= 10000 && backup.messages.size <= 100000)
        require(backup.chats.map { it.id }.distinct().size == backup.chats.size)
        require(backup.messages.map { it.id }.distinct().size == backup.messages.size)
        val chatMap = backup.chats.associate { it.id to newId() }
        val msgMap = backup.messages.associate { it.id to newId() }
        require(backup.messages.all { it.chatId in chatMap && it.role in setOf("user", "assistant") && it.position >= 0 })
        db.withTransaction {
            backup.chats.forEach { dao.saveChat(it.copy(id = chatMap.getValue(it.id))) }
            backup.messages.forEach { dao.saveMessage(it.copy(id = msgMap.getValue(it.id), chatId = chatMap.getValue(it.chatId), state = if (it.state == "generating") "interrupted" else it.state)) }
            backup.branches.filter { it.originChatId in chatMap && it.targetChatId in chatMap && it.originMessageId in msgMap }.forEach {
                dao.saveBranch(it.copy(id = newId(), originChatId = chatMap.getValue(it.originChatId), targetChatId = chatMap.getValue(it.targetChatId), originMessageId = msgMap.getValue(it.originMessageId)))
            }
        }
        return backup.chats.size
    }
}
