# MCP Stage 2: Model Tool Calling Design

**Дата:** 2026-09-09  
**Статус:** утверждено пользователем  
**Проект:** AstraChat Android

## Цель

Добавить управляемые моделью вызовы MCP-инструментов для провайдеров с протоколом `CHAT_COMPLETIONS`: передавать объявления функций в запрос, собирать потоковые `tool_calls`, выполнять MCP `tools/call`, возвращать результаты модели, сохранять раунды в истории и отображать их в чате.

## Границы этапа

В Этап 2 входят:

- `tools/call` в MCP-клиенте;
- удерживаемые MCP-сессии на время одной генерации;
- `tools` и tool-сообщения в запросах `CHAT_COMPLETIONS`;
- потоковая сборка `delta.tool_calls` и обработка непотоковых `message.tool_calls`;
- выбор MCP-серверов отдельно для каждого чата;
- ограниченный цикл «модель → инструменты → модель»;
- персистенция и отображение assistant/tool-раундов;
- миграция Room с версии 3 на версию 4;
- тесты и документация.

Не входят:

- tool calling для Responses, Anthropic и Gemini;
- глобальный пул долгоживущих MCP-сессий;
- параллельное исполнение tool calls;
- подтверждение каждого вызова пользователем;
- проверка с реальным MCP-сервером или провайдером.

## Архитектура

Для каждого выбранного MCP-сервера приложение открывает сессию на время одной отправки: `initialize` → `notifications/initialized` → `tools/list` → один или несколько `tools/call` → `DELETE`. Сессии закрываются после успешного ответа, ошибки или отмены.

Это сохраняет серверное состояние внутри tool-цикла и не вводит сложность глобального пула.

## Core-модели

Добавляются сериализуемый `ToolCall(id, name, arguments)`, потоковый `ToolCallFragment(index, id, name, argumentsDelta)` и `McpToolResult(text, isError)`. `Chunk` получает `toolFragments` и `toolCalls`; `Turn` — `toolCalls` и `toolCallId`; `ChatRequest` — `tools: List<McpTool>`. Новые поля имеют безопасные значения по умолчанию.

Имена MCP-инструментов приводятся к `^[a-zA-Z0-9_-]{1,64}$`: недопустимые символы заменяются `_`, длина ограничивается 64 символами, пустое имя заменяется `tool`, коллизии получают детерминированный суффикс. Для исполнения хранится связь exposed name с `serverId` и исходным MCP-именем.

## ProviderCodec и ProviderClient

Изменения wire-формата ограничены `CHAT_COMPLETIONS`.

Исходящий запрос получает стандартный массив `tools` с `type: function`, именем, описанием и `inputSchema` как `parameters`. Assistant turns с вызовами сериализуются в `tool_calls`; tool turns — как `role: tool`, `tool_call_id`, `content`. Ключ `tools` остаётся запрещённым в пользовательском `extraJson`.

Потоковый ответ читает `choices[].delta.tool_calls`. Фрагменты объединяются по `index`, аргументы конкатенируются в порядке SSE-событий, готовые вызовы выдаются в терминальном `Chunk` после `[DONE]`. Непотоковый ответ читает `choices[].message.tool_calls`; пустой текст допустим, если есть валидные вызовы.

Протоколы RESPONSES, ANTHROPIC и GEMINI сохраняют текущую работу без инструментов — их tool calling запланирован на Этап 3. Провайдеры на `CHAT_COMPLETIONS` (включая DeepSeek, OpenRouter, Groq, Ollama) получают поддержку инструментов.

## MCP tools/call

`McpClient` предоставляет:

```kotlin
suspend fun openSession(server: McpServer, credentials: Credentials): McpToolSession

class McpToolSession {
    suspend fun tools(): List<McpTool>
    suspend fun callTool(name: String, arguments: JsonObject?): McpToolResult
    suspend fun close()
}
```

`callTool` отправляет JSON-RPC `tools/call` с исходным MCP-именем и объектом arguments. Текстовые блоки объединяются; embedded resource использует текст; image/audio/resource link получают безопасные маркеры; при пустом content используется `structuredContent`.

`isError=true` возвращается как результат инструмента. JSON-RPC и транспортные ошибки становятся `SafeFailure`; `-32601` — `FailureKind.MCP`. HTTP 404 вызывает один повтор `initialize` и повтор только текущей операции. Отмена отменяет текущий OkHttp call. `close()` выполняется в `NonCancellable` с лимитом две секунды.

Stage 1 discovery/resources/prompts и их тесты сохраняют прежнюю семантику.

## Цикл генерации

ViewModel:

1. определяет чат, модель и провайдера;
2. для `CHAT_COMPLETIONS` открывает выбранные серверы и получает инструменты;
3. отправляет первый модельный запрос;
4. при tool calls сохраняет assistant tool-call turn;
5. последовательно выполняет вызовы;
6. сохраняет tool result rows;
7. добавляет assistant/tool turns в следующий запрос;
8. повторяет до обычного ответа или лимита.

Допускается максимум 8 tool-раундов. Следующий запрос инструментов завершает генерацию через `SafeFailure(FailureKind.TOOL_LOOP)`. Контекстный лимит проверяется перед каждым модельным раундом.

## Ошибки и ограничения

- Транспортная ошибка MCP прерывает генерацию через `SafeFailure`.
- `isError=true` возвращается модели с префиксом `[Ошибка инструмента]`.
- Неизвестное exposed name и невалидный JSON arguments не вызывают сервер, а возвращают модели безопасный tool result.
- Пустой результат становится `(пустой ответ инструмента)`.
- Результат ограничивается 32 000 символами с явным маркером обрезки.
- Сырые ответы ошибок сервера не показываются пользователю.
- Отмена помечает текущий assistant-row как `stopped` и закрывает все сессии.

## Room v4 и резервные копии

`ChatRow` получает `mcpServerIds: String = ""` с `@ColumnInfo(defaultValue = "")`.

`MessageRow` получает `toolCalls`, `toolCallId`, `toolName`, все `String = ""` с `@ColumnInfo(defaultValue = "")`.

`MIGRATION_3_4` добавляет четыре `TEXT NOT NULL DEFAULT ''` столбца. Экспортируется `4.json`; каталог схем подключается к androidTest assets. Новые backup используют версию 2, импорт принимает версии 1 и 2 и роли `user`, `assistant`, `tool`. Markdown подписывает tool rows как `Инструмент · <имя>`.

## UI

В композере появляется `MCP` или `MCP · N`. Диалог с переключателями сохраняет выбор серверов на текущем чате. Для не-`CHAT_COMPLETIONS` показывается пояснение, что выбор сохранён, но инструменты не отправляются.

Tool row отображается как `Инструмент · <имя>`. Assistant tool-call row показывает `Вызваны инструменты: ...`. UI-копирайт остаётся русским и следует текущему Material 3 стилю.

## Тестовые швы

Тесты пишутся вертикальными RED → GREEN через публичные границы:

- `ProviderCodecTest`: tools, assistant/tool messages, streaming fragments, non-stream calls, запрет tools для других протоколов;
- `NetworkTest` + MockWebServer: сборка SSE tool calls и non-stream response без текста;
- `McpClientTest` + MockWebServer: удерживаемая сессия, tools/list, tools/call, isError, structuredContent, placeholder-блоки, -32601, 404 retry, DELETE;
- `AstraViewModelTest`: успешный tool round, персистенция, невалидные arguments, transport failure, лимит раундов, отключение tools для других протоколов;
- `StorageTest`: migration 3→4 и полные цепочки старых миграций до v4.

Mockito не использует nullable `any()` для Kotlin non-null параметров: точные значения, hand-written fake или non-null dummy matcher. `RecordedRequest.body` читается один раз перед несколькими утверждениями.

## Безопасность и совместимость

- Новых зависимостей нет.
- MCP-секреты остаются в `SecretVault`/Android Keystore.
- Ключи и пользовательские заголовки не логируются.
- Redirect и automatic retry OkHttp остаются отключены.
- Destructive migration запрещена.
- Лимиты JSON/SSE/session-id из Этапа 1 сохраняются.
- Prompt `blockText` не изменяется.
- UI получает только нормализованные `SafeFailure`.

## Критерии готовности

- tool calling end-to-end покрыт тестами для `CHAT_COMPLETIONS`;
- остальные протоколы проходят regression без tools;
- серверы выбираются и сохраняются на чат;
- tool rounds сохраняются и восстанавливаются;
- Room v4 migration и `4.json` добавлены;
- проходят `python tools/check_source.py` и `gradlew detektCheck test lint assembleDebug :app:compileDebugAndroidTestKotlin`;
- README, CHANGELOG и ARCHITECTURE обновлены;
- отдельно отмечены непроверенные AVD, живой MCP-сервер, реальный провайдер и визуальная QA.
