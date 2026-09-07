# Фактическая проверка — 7 сентября 2026

## Итог

Это исходный кандидат Astra Chat, а не подтверждённо работоспособный Android-релиз. Полные критерии готовности пользователя НЕ достигнуты. Не выдавайте архив исходников за APK или результат успешного CI.

## GitHub MCP

- Подключение GitHub MCP выполнено после повторной авторизации.
- get_me подтвердил пользователя Amir1502 (Folzi).
- Поиск `user:Amir1502 AstraChat in:name`: 0 доступных результатов.
- Чтение `Amir1502/AstraChat`: 404 Not Found. Существующее содержимое и основную ветку прочитать не удалось.
- create_repository с name=AstraChat, public, autoInit: **403 Resource not accessible by personal access token**.
- Создание репозитория не подтверждено; никаких последующих GitHub-записей не выполнялось.
- Основная ветка не определена. Рабочая ветка `feat/initial-astra-chat` только запланирована, НЕ создана.
- Коммитов в GitHub: 0. Pull Request: не создан. Actions workflow на GitHub: не опубликован и не запущен. Jobs/Artifacts/Release: отсутствуют в результате этой работы.
- Реальные ссылки на репозиторий/PR/Release/Artifact отсутствуют. Нельзя заменять их предполагаемыми URL.
- Чтобы продолжить, нужен MCP-доступ к созданию репозитория либо заранее созданный владельцем AstraChat с доступом к исходникам, PR и workflow. Для проверки CI также нужны права чтения Actions/checks. Права и возможности MCP проверяются повторно перед следующими операциями.

## Среда

- Java: OpenJDK Corretto 25.0.4, с модулем jdk.compiler, но без команды javac в PATH.
- Android SDK, sdkmanager, android.jar, установленный Gradle и Kotlin compiler не найдены.
- Сетевые пробы downloads.gradle.org и dl.google.com завершились ошибкой DNS.
- Gradle 9.3.1 bootstrap компилировался и запускался через Java source launcher, но не смог скачать дистрибутив.

## Выполнено успешно

1. `python3 tools/check_source.py` — exit 0: структура, XML/TOML/JSON, обязательные файлы, набор протоколов, правила запрета секретных/сборочных файлов и небезопасных сетевых конструкций. Это узкая эвристическая проверка, не доказательство отсутствия всех секретов или дефектов.
2. `bash tools/offline-checks.sh` — exit 0: production SseReader.java и TokenMath.java скомпилированы с `--release 17` через `java -m jdk.compiler/com.sun.tools.javac.Main`; выполнены **17 реальных assertions**. Проверены SSE BOM/CRLF/CR/comments/multiline/EOF/limit, Unicode-оценка токенов, TPS и TTFT.
3. YAML workflow прочитан PyYAML: jobs verify/device-tests/release существуют, release зависит от verify и device-tests. Это структурная проверка YAML, не запуск GitHub Actions.
4. Реальная строка SQL миграции извлечена из Database.kt и выполнена в SQLite: добавлено state='complete', сохранён total=42. Это **не** Android Room schema validation.

## Команды Gradle, запущенные фактически

| Команда | Exit code | Результат |
|---|---:|---|
| `./gradlew test` | 1 | Блокировка загрузки Gradle, тесты Kotlin/JUnit не начались |
| `./gradlew lint` | 1 | Блокировка загрузки Gradle, Android Lint не начался |
| `./gradlew detektCheck` | 1 | Блокировка загрузки Gradle, Detekt не начался |
| `./gradlew assembleDebug` | 1 | Блокировка загрузки Gradle, APK не создан |
| `./gradlew assembleRelease` | 1 | Блокировка загрузки Gradle, APK не создан |

Общая причина: `java.net.UnknownHostException: downloads.gradle.org`. Не было смысла повторять эти команды без изменения доступа к сети. Полные журналы и машинный results.json находятся в каталоге verification архива. Логи не содержат API-ключей или заголовков авторизации.

## Не выполнено

- Полная компиляция Kotlin, KSP, Hilt, Room и Compose; разрешение зависимостей и проверка совместимости всей матрицы версий.
- Android Lint, Detekt и Gradle unit tests.
- Room instrumentation tests, ViewModel suite и Compose UI smoke test на Android.
- Установка/запуск приложения, реальные запросы к официальным API, визуальное QA, TalkBack, ландшафт, большие экраны и нагрузочная проверка длинных чатов.
- Debug/release APK, подпись, Actions Artifacts, GitHub Release, ветка, коммиты и PR.

## Файлы сборки

Корень локального проекта: `/data/AstraChat`.
**Фактического пути к готовому APK нет: APK не создан.** После успешной будущей сборки ожидается `app/build/outputs/apk/debug/app-debug.apk`. Не считать этот ожидаемый путь существующим артефактом.

Имена, настроенные в workflow, но ещё не созданные на GitHub:
- Artifact `AstraChat-debug-<run_number>` → `AstraChat-v1.0.0-debug-universal.apk`.
- Artifact `AstraChat-release-v1.0.0` → подписанный universal или явно unsigned APK.
- Отчёты `AstraChat-reports-<run_number>` и отдельные device-test artifacts.

## Известные отклонения от полного задания

- Недоступна фактическая GitHub-публикация из-за 403; недоступна Android-сборка из-за сети и отсутствия SDK.
- Нельзя утверждать, что приложение компилируется или запускается: Android-specific ошибки могут оставаться до первого полноценного build/lint.
- UI не проходил обязательное визуальное QA на устройстве; скриншоты не сфабрикованы.
- Bootstrap прозрачно исходный, а не стандартный бинарный Gradle Wrapper. Официальный Wrapper предлагается сгенерировать после восстановления загрузок.
- Поиск чатов реализован по названию, не полнотекстовый по сообщениям.
- JSON-backup переносит чаты, сообщения и ветки, но не настройки, статистику, черновики или секреты.
- Подсветка кода базовая лексическая, не полноценный языковой парсер.
- Нет foreground service; генерация при завершении процесса прерывается с сохранением checkpoints.
- Нет пагинации истории Room; полноценная производительность длинных чатов не доказана.
- Model discovery ограниченно поддерживает пагинацию Gemini; context window и capabilities требуют настройки по модели. Автоматическая совместимость произвольного API не гарантируется.
- Встроенные модели не выдаются за доступные без учётной записи: действительный ID нужно получить через API или добавить вручную.

Обязательные непроверенные функции не заменены фиктивными успешными Issues или Release. Проект следует продолжать проверять после устранения указанных блокировок.
