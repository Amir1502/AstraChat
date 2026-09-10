package com.folzi.astrachat.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

const val MCP_CLIENT_NAME = "Astra Chat"
const val MCP_CLIENT_VERSION = "1.0"
const val MCP_PROTOCOL_LATEST = "2025-06-18"
val MCP_PROTOCOL_VERSIONS = setOf("2025-06-18", "2025-03-26", "2024-11-05")

@Serializable
data class McpServer(
    val id: String, val name: String, val url: String,
    val timeoutSeconds: Int = 60, val allowLocalHttp: Boolean = false,
)
@Serializable
data class McpTool(val name: String, val title: String = "", val description: String = "", val inputSchema: JsonObject = JsonObject(emptyMap()))
@Serializable
data class McpResource(val uri: String, val name: String = "", val description: String = "", val mimeType: String = "")
@Serializable
data class McpPromptArgument(val name: String, val description: String = "", val required: Boolean = false)
@Serializable
data class McpPrompt(val name: String, val title: String = "", val description: String = "", val arguments: List<McpPromptArgument> = emptyList())

data class McpServerInfo(val name: String, val title: String = "", val version: String = "")
data class McpCapabilities(val tools: Boolean, val resources: Boolean, val prompts: Boolean)
data class McpSession(val info: McpServerInfo, val protocolVersion: String, val capabilities: McpCapabilities, val instructions: String = "")
data class McpDiscovery(val serverId: String, val session: McpSession, val tools: List<McpTool>, val resources: List<McpResource>, val prompts: List<McpPrompt>)
data class McpPromptMessage(val role: String, val text: String)

/** Prompt templates arrive as role-tagged messages; the chat draft receives the user-visible text. */
fun promptInsertText(messages: List<McpPromptMessage>): String =
    messages.filter { it.role == "user" }.ifEmpty { messages }.joinToString("\n\n") { it.text }.trim()

data class McpToolResult(val text: String, val isError: Boolean)
data class ExposedTool(val name: String, val serverId: String, val tool: McpTool)

/** CHAT_COMPLETIONS function names must match ^[a-zA-Z0-9_-]{1,64}$. */
fun sanitizeToolName(name: String): String =
    name.map { c -> if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_' || c == '-') c else '_' }
        .joinToString("").take(64).ifBlank { "tool" }

/** Deterministic exposed names across servers; collisions get a stable server-scoped suffix. */
fun exposeTools(toolsByServer: Map<String, List<McpTool>>): List<ExposedTool> {
    val out = LinkedHashMap<String, ExposedTool>()
    toolsByServer.forEach { (serverId, tools) ->
        tools.forEach { tool ->
            val base = sanitizeToolName(tool.name)
            var exposed = base
            var suffix = 2
            while (exposed in out) {
                exposed = (base.take(52) + "_" + serverId.take(4) + "_" + suffix).take(64)
                suffix++
            }
            out[exposed] = ExposedTool(exposed, serverId, tool)
        }
    }
    return out.values.toList()
}
