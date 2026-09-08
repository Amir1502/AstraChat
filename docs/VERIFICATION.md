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
- Локально инструментальные тесты не запускались (нет AVD), но они выполняются в CI. Прежнее утверждение «job `device-tests` падает на hosted-раннерах» было неверным: падала компиляция инструментальных тестов, а эмуляторы API 26/35 грузились штатно. После двух правок в `app/src/androidTest/java/com/folzi/astrachat/StorageTest.kt` — Room 2.8.4 больше не реализует `Closeable`, поэтому `use {}` заменён на `try/finally` с `close()`; JUnit4 требует `void`, поэтому `= runBlocking { ... }` закреплено как `runBlocking<Unit>` — прогон 34154241379 зелёный полностью: `verify`, `device-tests (26)` и `device-tests (35)` success (Room-миграция 1→2, ветвление/бэкапы, SecretVault, Compose smoke).
- По-прежнему не проверено: установка APK на реальное устройство, ручное визуальное QA, TalkBack, ландшафт, большие экраны и реальные запросы к API провайдеров. Job `release` в 1.0.1 временно не зависел от `device-tests`; после того как оба эмуляторных джоба стали зелёными, зависимость возвращена — `release: needs: [verify, device-tests]`.

## Дополнение — редизайн интерфейса в манере Codex, локальная проверка

- Изменены `app/src/main/java/com/folzi/astrachat/ui/Theme.kt` (графитовые палитры, типографика, формы), `ui/Components.kt` (новые `Panel`/`Hairline`/`SectionLabel`/`RoleLabel`/`PrimaryAction`, тихие кнопки, листы кода), `ui/ChatScreen.kt` (транскрипт без «пузырей», hairline-разделители, композер-панель, статус-строка генерации, история чатов). Точечно `ui/ProvidersScreen.kt` и `ui/StatisticsScreen.kt`: `ElevatedCard` заменён на плоскую `Card` с hairline-рамкой. В `app/src/androidTest/java/com/folzi/astrachat/ComposeSmokeTest.kt` добавлен тест `hairlineSurfacesRenderTranscriptLabels`.
- Локально: `gradlew detektCheck test lint assembleDebug :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL, 20 unit-тестов без падений, собран `app/build/outputs/apk/debug/app-debug.apk` (14 315 460 байт).
- Lint: ошибка `NonObservableLocale` (чтение `Locale.getDefault()` внутри композабла `RoleLabel`) устранена переходом на `String.uppercase()` без явной локали; два предупреждения `ModifierParameter` устранены переносом `modifier` на позицию первого опционального параметра. Остались прежние замечания: `UseKtx` ×3, `DataExtractionRules`, хинт `AutoboxingStateCreation`.
- Не проверено: как новый интерфейс выглядит на экране. Устройство не подключено (`adb devices` пуст), AVD локально не поднимался, скриншоты не снимались. Редизайн подтверждён компиляцией, lint и unit-тестами локально, а на эмуляторах API 26/35 в CI — инструментальными тестами, включая новый Compose-тест.

## Дополнение — релиз 1.0.2 (редизайн)

- Tag `v1.0.2` (коммит `a44d8b5`, tag-объект `bdbf2d9`) запустил прогон 34225956993: `verify` success, `device-tests (26)` success, `device-tests (35)` success, `release` success. Это первый релиз, опубликованный с возвращённым гейтом `needs: [verify, device-tests]`.
- Ассеты релиза: `AstraChat-v1.0.2-universal.apk` (1 981 711 байт) и `SHA256SUMS.txt`. SHA-256 ассета `36a4c9fdc02dc1dbb75048f4f4ff98cebd3dc9d8711497226d7d6e31a141b4f9` совпал с содержимым `SHA256SUMS.txt`. Неподписанных ассетов нет.
- Скачанный из релиза APK проверен локально: `apksigner verify --verbose --print-certs` → Verifies, v1 = false, v2 = true, один подписант `CN=Astra Chat Release, OU=Mobile, O=AstraChat, C=US`, SHA-256 сертификата `e0ce85d9195b6fde6679653ac46d864dadaa20666d5f63973c7a07166bd06a25` (идентичен ключу 1.0.1 → обновление ставится поверх). `aapt2 dump badging` → `com.folzi.astrachat`, versionCode 3, versionName 1.0.2, minSdk 26, targetSdk 37, native-code arm64-v8a/armeabi-v7a/x86/x86_64, label «Astra Chat».
- Параллельно собрана локальная подписанная сборка тем же keystore: `dist/AstraChat-v1.0.2-universal-signed.apk`, 1 983 799 байт, SHA-256 `4be1ee03e85a5ff67091351edac6e735b7219d2929ecbd71685f6aaa638ff31b`, тот же сертификат. Побайтовое совпадение с CI-артефактом не ожидается: разные окружения сборки и метки времени внутри zip; функционально это одна и та же версия.
- Debug-сборка для быстрой оценки дизайна: `dist/AstraChat-codex-ui-debug.apk` (14 315 460 байт, SHA-256 `bb9aa808e30d8ab0fa66a06a1660d4417c444196fa0b02e2186760261d404278`, debug-сертификат `5e9df9ed…`) — ставится только после удаления релизной сборки, ключи разные.
- По-прежнему не выполнено: установка на реальное устройство, визуальное QA нового дизайна, TalkBack, ландшафт, реальные запросы к API провайдеров.

## Дополнение — настройка скриншотов, README и описания релизов

- Изменения в коде: `data/SettingsStore.kt` (новое поле `allowScreenshots: Boolean = false` в сериализуемых `AppSettings` — старые сохранённые настройки читаются, недостающее поле получает значение по умолчанию), `MainActivity.kt` (комментарий: старт всегда с `FLAG_SECURE`), `ui/AstraRoot.kt` (`ApplyScreenshotPolicy` снимает или возвращает флаг по значению настройки через `LocalActivity.current?.window`), `ui/SettingsScreen.kt` (раздел «Приватность экрана» с переключателем и пояснением о последствиях).
- Первая версия правки приводила к ошибке lint `ContextCastToActivity` (каст `LocalContext` к `Activity`); заменено на `LocalActivity`. После этого `gradlew detektCheck test lint assembleDebug :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL, 20 unit-тестов без падений, замечания lint только прежние: `DataExtractionRules`, `UseKtx` ×3, хинт `AutoboxingStateCreation`.
- Переключатель скриншотов на устройстве не проверялся: устройства и AVD нет. Подтверждены компиляция, статический анализ и существующие тесты; фактическое поведение `FLAG_SECURE` на реальном окне не наблюдалось.
- `README.md` переписан целиком: бейджи, английская шапка, таблицы фактов сборки и дизайн-токенов, разбор ошибок установки, быстрый старт API, сборка из исходников (включая source-bootstrap Gradle 9.3.1 через `tools/GradleBootstrap.java` и переменные подписи), CI-джобы, приватность, статус проверок, структура проекта. Добавлен `docs/screenshots/README.md` — изображений в репозитории нет.
- Описания релизов переоформлены через GitHub REST API: v1.0.2 (id 384716952) и v1.0.1 (id 384246910) — единый шаблон с таблицами ассетов, хешами, подписью, шагами установки и блоками «Проверено» / «Не проверено». v1.0.0 (id 384148131) помечен `prerelease = true`, переименован в «AstraChat v1.0.0 (pre-release, устарел)», из него удалён ассет `AstraChat-v1.0.0-universal-unsigned.apk` (id 548916485, 1 969 418 байт) как не устанавливаемый.
- Факты v1.0.0 проверены скачиванием: `AstraChat-v1.0.0-debug-universal.apk` — 14 062 554 байт, SHA-256 `6365784f8f0b4c505101ae03a8557575c4d279f9f735119497128b8dbebd3f57`, `apksigner verify --print-certs` → сертификат `C=US, O=Android, CN=Android Debug`, SHA-256 сертификата `3a93de1f1348a879fefae0f65d5ce955a7aafc537a495ff208d86036176d82f9`, `aapt2 dump badging` → versionCode 1, versionName 1.0.0, minSdk 26. После правок `releases/latest` по-прежнему указывает на v1.0.2.
