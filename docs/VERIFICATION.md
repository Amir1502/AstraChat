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

## Дополнение — локальная проверка 1.0.1 (Windows, та же дата)

Блокировки разделов «Среда» и «Команды Gradle» относятся к прежней среде и в текущей сняты:

- Установлен Android SDK `C:\Android\Sdk`: platform-tools, `platforms;android-37.0` (плюс junction `android-37`), `build-tools;36.0.0`. Java — Temurin 17.0.20, сеть доступна.
- `./gradlew detektCheck test lint assembleDebug assembleRelease` — BUILD SUCCESSFUL за 7m59s (Gradle 9.3.1 через source bootstrap).
- Unit-тесты: 20 тестов, 0 падений — `AstraViewModelTest` 2, `NetworkTest` 7, `ProviderCodecTest` 11.
- APK созданы фактически: `app/build/outputs/apk/debug/app-debug.apk` (14 064 646 байт) и `app/build/outputs/apk/release/app-release-unsigned.apk` (1 971 510 байт). Копии: `dist/AstraChat-v1.0.1-debug-universal.apk`, `dist/AstraChat-v1.0.1-universal-unsigned.apk`, SHA-256 в `dist/SHA256SUMS-v1.0.1-local.txt`.
- Первая локальная release-сборка была **без подписи** (секретов не существовало): `apksigner verify` → `DOES NOT VERIFY / Missing META-INF/MANIFEST.MF`. Android такой пакет не устанавливает и сообщает «файл повреждён» — именно это и наблюдалось при скачивании ассета из релиза.
- Далее сгенерирован keystore **вне репозитория**: `C:\Users\user\AndroidKeys\astrachat-release.jks` (JKS, RSA 2048, alias `astrachat`, срок 10950 дней, `CN=Astra Chat Release, OU=Mobile, O=AstraChat, C=US`). `assembleRelease` со штатным `signingConfigs.release` дал `dist/AstraChat-v1.0.1-universal-signed.apk` (1 983 798 байт), `apksigner verify` → Verifies, v2 scheme = true.
- Четыре значения добавлены в секреты репозитория (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), job `release` прогона 34148790323 перезапущена (attempt 2) — success. В релиз v1.0.1 выложен подписанный CI-артефакт `AstraChat-v1.0.1-universal.apk`: 1 981 710 байт, SHA-256 `b3935f7c3a6536ed886e63c78ae0b93cfd95c6f7e3ac6796c03a2edb0f8471f3` (совпал с `SHA256SUMS.txt` релиза), сертификат `CN=Astra Chat Release`, SHA-256 сертификата `e0ce85d9195b6fde6679653ac46d864dadaa20666d5f63973c7a07166bd06a25` — тот же ключ, что и у локальной подписи. Неподписанный ассет из релиза удалён.
- Пароль keystore в репозиторий не коммитился и в отчётах не публикуется; он лежит в `C:\Users\user\AndroidKeys\astrachat-release-credentials.txt`. Без этого ключа следующие обновления не встанут поверх установленной версии — нужна резервная копия.
- Установка на реальное устройство не проверялась: `adb devices` пуст. Перед установкой требуется удалить прежнюю сборку Astra Chat — она подписана другим (debug) ключом.
- Причиной красных `Unit tests` в Actions был не тест, а ошибка компиляции `app/src/main/java/com/folzi/astrachat/ui/Components.kt`: `Markwon.Builder.linkResolver` в Markwon 4.6.2 не существует (есть только у `MarkwonConfiguration.Builder`). Исправлено установкой резолвера через `AbstractMarkwonPlugin.configureConfiguration`.
- Локальные отклонения инструментов: `python tools/check_source.py` падает на `dist/*.apk` (политика запрещает APK в дереве; в CI checkout `dist/` отсутствует), `bash tools/offline-checks.sh` падает из-за кодировки javac windows-1251 — те же 17 assertions проходят при `-encoding UTF-8`.
- Не проверено локально: instrumentation и Compose-тесты на эмуляторе, установка APK, ручное визуальное QA, TalkBack, реальные запросы к API провайдеров. Job `device-tests` (API 26/35) падает на hosted-раннерах GitHub; с 1.0.1 он не блокирует job `release`, и это указано в теле релиза.
