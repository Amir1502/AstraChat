# Архитектура

## Модули
`core` — JVM: неизменяемые модели, wire-codecs, endpoint/security policy, coroutine Flow gateway, SSE framing и метрики. Нет Android Context, Room и UI.
`app` — Android composition root, Hilt DI, data (Room/Vault/DataStore), ViewModel orchestration и UI. Слои разделены пакетами без множества пустых модулей.

## Поток запроса
Compose → AstraViewModel (single-send guard) → snapshot настроек/модели/истории → проверка URL/контекста/JSON → транзакционное создание сообщения пользователя и ассистента → ChatGateway → OkHttp Call → SSE parser → ProviderCodec → Chunk(text, usage, terminal). ViewModel объединяет частичный usage и записывает checkpoint в Room не чаще одного раза за 150 мс. При остановке coroutine cancellation вызывает Call.cancel, итоговый checkpoint выполняется в NonCancellable. Уже полученный текст сохраняется. В init прежние generating-поля переводятся в interrupted.

Usage хранится отдельной записью на request ID, upsert предотвращает двойное добавление при checkpoint. Удаление чата не стирает исторический расход; сброс статистики отдельный. TTFT/TPS используют монотонное nanoTime; календарные окна — epoch milliseconds и timezone устройства. Пока API не завершилось нормально, output оценивается по полному тексту, а не числу SSE events. Финальный официальный usage заменяет оценку.

## Данные
Room v2: chats, messages, providers, models, usage, branches, drafts. FK cascade для chat/messages/drafts и provider/models. BranchRow связывает оригинал и новый чат; сообщения до точки ветвления копируются с новыми ID. Регенерация создаёт ветку перед соответствующим user-turn. История исходного чата не перезаписывается.

Миграция 1→2 добавляет persisted usage.state. На первом успешном KSP-build Room экспортирует JSON schemas в app/schemas. Не используется fallbackToDestructiveMigration. Инструментальный тест создаёт настоящий файл v1, сохраняет usage, повышает до v2 и заставляет Room проверить схему.

Credentials — отдельный зашифрованный конверт Key + Headers + Query. В таблице providers только публичная JSON-модель метаданных; Base URL не принимает userinfo/query/fragment. DataStore хранит сериализуемые AppSettings без ключей. JSON-backup чатов использует проверку лимитов, schema version и уникальности ID, сохраняет новые ID и не перезаписывает текущие чаты.

## UX
Compose/Material3, Navigation Compose, stateIn + collectAsStateWithLifecycle. LazyColumn со стабильными message IDs; auto-follow отключается при ручной прокрутке вверх. Настройки темы учитывают систему; AMOLED имеет настоящий чёрный canvas. Значение анимаций управляет animateContentSize сообщений; системные motion-настройки Android применяются стандартными компонентами. Drawer заменяется постоянной боковой панелью на 840dp+. SAF не требует широкого доступа к хранилищу.

## Ограничения
Нет foreground service, полноценного model tokenizer и пагинации длинной Room-истории. Фактические производительность, Android API-совместимость, визуальное качество и TalkBack ещё не проверены. Проверенные локальные Java assertions не заменяют Android-сборку. Android-specific ошибки компиляции/линта нельзя исключить без Gradle dependencies/SDK.
