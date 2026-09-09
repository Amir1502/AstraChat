package com.folzi.astrachat.data

import com.folzi.astrachat.core.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

fun mcpVaultId(id: String) = "mcp-$id"

@Singleton
class McpRepository @Inject constructor(db: AstraDatabase, private val vault: SecretVault, private val mcp: McpClient) {
    private val dao = db.dao()
    val servers = dao.mcpServers().map { rows -> rows.map { json.decodeFromString<McpServer>(it.metadata) } }
    suspend fun save(server: McpServer, newKey: String?, headers: Map<String, String>?) {
        TransportPolicy.validate(server.url, server.allowLocalHttp)
        require(server.name.isNotBlank() && server.timeoutSeconds in 10..600)
        withContext(Dispatchers.IO) {
            val old = vault.read(mcpVaultId(server.id))
            vault.write(mcpVaultId(server.id), Credentials(newKey ?: old.key, headers ?: old.headers))
        }
        dao.saveMcpServer(McpServerRow(server.id, json.encodeToString(server)))
    }
    private suspend fun credentials(server: McpServer): Credentials = withContext(Dispatchers.IO) { vault.read(mcpVaultId(server.id)) }
    suspend fun discover(server: McpServer): McpDiscovery = mcp.discover(server, credentials(server))
    suspend fun readResource(server: McpServer, uri: String): String = mcp.readResource(server, credentials(server), uri)
    suspend fun getPrompt(server: McpServer, name: String, arguments: Map<String, String>): List<McpPromptMessage> = mcp.getPrompt(server, credentials(server), name, arguments)
    suspend fun delete(server: McpServer) { withContext(Dispatchers.IO) { vault.delete(mcpVaultId(server.id)) }; dao.deleteMcpServer(server.id) }
}
