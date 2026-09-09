# Архитектура

## Модули
`core` — JVM: неизменяемые модели, wire-codecs, endpoint/security policy, coroutine Flow gateway, SSE framing и метрики, MCP-клиент Streamable HTTP (JSON-RPC 2.0, эфемерные сессии). Нет Android Context, Room и UI.
`app` — Android composition root, Hilt DI, data (Room/Vault/DataStore), ViewModel orchestration и UI. Слои разделены пакетами без множества пустых модулей.

## Поток запроса
Compose → AstraViewModel (single-send guard) → snapshot настроек/модели/истории → проверка URL/контекста/JSON → транзакционное создание сообщения пользователя и ассистента → ChatGateway → OkHttp Call → SSE parser → ProviderCodec → Chunk(text, usage, terminal). ViewModel объединяет частичный usage и записывает checkpoint в Room не чаще одного раза за 150 мс. При остановке coroutine cancellation вызывает Call.cancel, итоговый checkpoint выполняется в NonCancellable. Уже полученный текст сохраняется. В init прежние generating-поля переводятся в interrupted.

Usage хранится отдельной записью на request ID, upsert предотвращает двойное добавление при checkpoint. Удаление чата не стирает исторический расход; сброс статистики отдельный. TTFT/TPS используют монотонное nanoTime; календарные окна — epoch milliseconds и timezone устройства. Пока API не завершилось нормально, output оценивается по полному тексту, а не числу SSE events. Финальный официальный usage заменяет оценку.

## Данные
Room v3: chats, messages, providers, models, usage, branches, drafts, mcp_servers. FK cascade для chat/messages/drafts и provider/models. BranchRow связывает оригинал и новый чат; сообщения до точки ветвления копируются с новыми ID. Регенерация создаёт ветку перед соответствующим user-turn. История исходного чата не перезаписывается.

Миграция 1→2 добавляет persisted usage.state. На первом успешном KSP-build Room экспортирует JSON schemas в app/schemas. Не используется fallbackToDestructiveMigration. Инструментальный тест создаёт настоящий файл v1, сохраняет usage, повышает до v2 и заставляет Room проверить схему.

Миграция 2→3 добавляет таблицу `mcp_servers` (только публичная JSON-метаданные; секреты — в конверте SecretVault под ключом `mcp-<id>`). Инструментальный тест создаёт v3-файл, понижает его до v2 удалением таблицы и `PRAGMA user_version=2`, затем Room выполняет MIGRATION_2_3 и валидирует схему.

Credentials — отдельный зашифрованный конверт Key + Headers + Query. В таблице providers только публичная JSON-модель метаданных; Base URL не принимает userinfo/query/fragment. DataStore хранит сериализуемые AppSettings без ключей. JSON-backup чатов использует проверку лимитов, schema version и уникальности ID, сохраняет новые ID и не перезаписывает текущие чаты.

## MCP (этап 1)
`core/McpClient.kt` реализует клиента Model Context Protocol, ревизия 2025-06-18, транспорт Streamable HTTP: один endpoint, POST на каждое JSON-RPC-сообщение, `Accept: application/json, text/event-stream`, ответ JSON или SSE; сессия через `Mcp-Session-Id` (404 → один повторный initialize), `MCP-Protocol-Version` после инициализации, завершение — DELETE. Сессии эфемерные: initialize → notifications/initialized → операция → terminate на каждое действие пользователя. Discovery: tools/list, resources/list, prompts/list с пагинацией cursor (≤20 страниц), -32601 деградирует в пустой список; resources/read и prompts/get возвращают текст для вставки в черновик чата. Ошибки — только SafeFailure/FailureKind (добавлен MCP), тела и заголовки сервера не эхо-ируются. Ограничения этапа 1: нет stdio (невозможен на Android), legacy HTTP+SSE 2024-11-05, GET-стрима серверных сообщений, ответов на серверные JSON-RPC-запросы (игнорируются), явного notifications/cancelled при таймауте и вызовов инструментов моделью (этап 2).

## UX
Compose/Material3, Navigation Compose, stateIn + collectAsStateWithLifecycle. LazyColumn со стабильными message IDs; auto-follow отключается при ручной прокрутке вверх. Настройки темы учитывают систему; AMOLED имеет настоящий чёрный canvas. Значение анимаций управляет animateContentSize сообщений; системные motion-настройки Android применяются стандартными компонентами. Drawer заменяется постоянной боковой панелью на 840dp+. SAF не требует широкого доступа к хранилищу. С 1.0.2 интерфейс построен на плоских hairline-панелях без теней и «пузырей»: палитры, типографика и формы объявлены явно в `ui/Theme.kt`, примитивы (`Panel`, `Hairline`, `SectionLabel`, `RoleLabel`, `PrimaryAction`) живут в `ui/Components.kt`, лента чата — транскрипт с метками ролей и разделителями.

## Ограничения
Нет foreground service, полноценного model tokenizer и пагинации длинной Room-истории. Android-сборка, статический анализ и совместимость API больше не являются неопределённостью: `verify` в CI выполняет политику исходников, detekt, unit-тесты, Android Lint и обе сборки APK, а `device-tests` запускает инструментальные тесты на эмуляторах API 26 и 35. Не проверены: производительность на длинных историях, визуальное качество на реальном устройстве, TalkBack и ручное QA. Политика окна задаётся настройкой `allowScreenshots` (с 1.0.3 по умолчанию `true` — скриншоты разрешены); при выключенной настройке применяется `FLAG_SECURE`, а старт Activity всегда защищён до загрузки настроек.
