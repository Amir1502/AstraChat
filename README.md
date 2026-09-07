# Astra Chat

Нативный Android AI-чат: Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore и OkHttp.
Package: `com.folzi.astrachat`. Android 8.0 / API 26 и выше. Версия исходников: `1.0.0`.

> **Статус: исходный кандидат, не подтверждённый релиз.** Android-сборка, запуск на устройстве и live API-вызовы не подтверждены. В исходной среде отсутствовали Android SDK и Gradle, сетевые загрузки блокировались DNS. GitHub MCP авторизовался как Amir1502, но создание AstraChat вернуло 403. Репозиторий, PR, CI Artifact и Release не созданы. Фактические проверки описаны в `docs/VERIFICATION.md`.

## Возможности в исходниках
- Профили OpenAI, Anthropic Claude, Gemini, DeepSeek, Mistral, xAI, OpenRouter, Groq и Ollama.
- Четыре отдельных wire-формата: Chat Completions, Responses, Anthropic Messages и Gemini.
- Постепенный SSE, отмена HTTP-вызова, сохранение частичного ответа, защита от двойного нажатия.
- Провайдеры: создание, редактирование, копирование без секретов, удаление, относительный endpoint, зашифрованные headers/query, модели, context window, цены и проверка подключения.
- Room: история, сообщения, ветки, метаданные провайдеров, модели, usage и черновики; миграция 1→2 без destructive fallback.
- System/light/dark/AMOLED, динамические цвета Android 12+, масштаб текста, настройки анимаций, адаптивная боковая панель от 840 dp.
- Markdown, таблицы, ссылки только http(s), выделение, fenced-code с базовой лексической подсветкой и копированием.
- Название/закрепление/удаление чатов, поиск по названию, группировка по дням, создание веток и регенерация без перезаписи оригинала.
- TTFT, длительность, tokens/sec, input/output/reasoning/cached/total; статистика сегодня/7/30 дней/всё время, по провайдеру и модели.
- JSON-импорт с новыми ID и без перезаписи, экспорт в Markdown через системный выбор файла.

## Скриншоты
**Место для проверенных скриншотов:** главный чат, редактор провайдера, статистика, AMOLED и планшет. Изображения не добавлены: эмулятор и устройство не были доступны. `FLAG_SECURE` намеренно запрещает системные снимки переписки; для документации снимайте debug-вариант только после осознанного локального изменения этой настройки без настоящих данных.

## Локальная сборка
1. Установите JDK 17 и Android Studio/Android SDK с `platforms;android-37`, Build Tools `36.0.0`.
2. Укажите `ANDROID_HOME` или создайте локальный `local.properties` с `sdk.dir=...` — файл игнорируется Git.
3. Обеспечьте доступ к Google Maven, Maven Central, Gradle Plugin Portal и downloads.gradle.org.
4. Запустите из корня:

```sh
chmod +x gradlew
./gradlew test
./gradlew lint
./gradlew detektCheck
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew connectedDebugAndroidTest
```

Windows: `gradlew.bat` с теми же аргументами.

Здесь `gradlew` — **прозрачный source-only bootstrap**, а не выданный за официальный Gradle Wrapper бинарник. Он запускает `tools/GradleBootstrap.java` через JDK 17+, скачивает официальный Gradle 9.3.1 и сверяет закреплённый SHA-256. JAR не подменён и не сгенерирован фиктивно. Для стандартного Wrapper после успешного скачивания выполните `./gradlew wrapper --gradle-version 9.3.1 --gradle-distribution-sha256-sum b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`, затем проверьте JAR по официальной таблице контрольных сумм.

Ожидаемые выходы **после успешной сборки**:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`.
- Release без подписи: `app/build/outputs/apk/release/app-release-unsigned.apk`.
- Подписанный release: `app/build/outputs/apk/release/app-release.apk`.

Пока эти APK не созданы. Unsigned APK нельзя установить без подписи. Debug можно установить через `adb install -r app/build/outputs/apk/debug/app-debug.apk`; на телефоне разрешите установку из выбранного источника только для доверенного APK.

## Добавление API и модели
Откройте Меню → Провайдеры. Выберите профиль или «Добавить». Укажите HTTPS Base URL **с префиксом версии**, например `https://api.openai.com/v1`, нужный протокол и API-ключ. Относительный endpoint по умолчанию выбирается адаптером. Секреты из сохранённого профиля не возвращаются в поля редактирования; пустое поле сохраняет текущее значение, кнопка удаления удаляет их.

Нажмите «Получить модели» либо «Добавить модель». Укажите реальный model ID, context window, при желании USD/1M. Возможности новых моделей по умолчанию выключены: включите temperature, top_p, stop, reasoning_effort только согласно документации модели. Для новых reasoning-моделей Chat Completions может понадобиться `max_completion_tokens`. Выберите «Использовать по умолчанию».

«Проверить подключение» отправляет реальный короткий запрос с отдельным подтверждением: он может тарифицироваться, но не включается в статистику чатов. У /models могут быть другие разрешения или неполная пагинация; модель всегда можно добавить вручную.

Ollama: по умолчанию `127.0.0.1` означает само Android-устройство. Для эмулятора используйте `http://10.0.2.2:11434/v1`; для телефона — частный IPv4 компьютера. Отдельно включите локальный HTTP только в доверенной сети. Публичный HTTP и непроверенные TLS-сертификаты не разрешаются.

## GitHub Actions, APK и Releases
После фактической публикации `.github/workflows/android.yml` запускается на push/PR и вручную. CI: JDK 17, SDK 37, Gradle caching, source policy, unit tests, Detekt, Android Lint, debug APK, затем emulator tests API 26/35.

Artifacts: репозиторий → **Actions → Android → завершённый запуск → Artifacts → `AstraChat-debug-<номер запуска>`**. Внутри ожидается `AstraChat-v1.0.0-debug-universal.apk`. Отчёты: `AstraChat-reports-<номер запуска>`. Это имена настроенных выходов, не утверждение об уже опубликованных файлах.

Для release установите через GitHub Settings → Secrets and variables → Actions:
- `ANDROID_KEYSTORE_BASE64`: base64 signing keystore;
- `ANDROID_KEYSTORE_PASSWORD`;
- `ANDROID_KEY_ALIAS`;
- `ANDROID_KEY_PASSWORD`.

Не вставляйте значения секретов в PR, README или исходники. Локальное подписание использует `ANDROID_KEYSTORE_PATH` и остальные три переменные. При отсутствии полной конфигурации release остаётся unsigned. Временный keystore в CI удаляется даже после ошибки.

После успешных обязательных проверок и проверки версии создайте тег, совпадающий с `versionName`, например `v1.0.0`, на проверенном коммите. Workflow проверит совпадение версии, соберёт release и создаст GitHub Release с APK и SHA256SUMS. Скачать: репозиторий → **Releases → версия → Assets**. Подписанный файл: `AstraChat-v1.0.0-universal.apk`; неподписанный: `AstraChat-v1.0.0-universal-unsigned.apk`. APK не ограничены ABI и не содержат локального inference engine. Бинарники не коммитятся в Git.

## Безопасность и ограничения
- API-ключи и произвольные headers/query: AES-GCM, уникальный IV, AAD provider ID, ключ Android Keystore; Room и обычный DataStore их не содержат.
- Чаты находятся в приватной Room-базе, но **не зашифрованы отдельным ключом**. Шифрование диска зависит от Android. Root/скомпрометированное устройство не защищено полностью.
- Android backup выключен. Экспорт переписки не зашифрован; выбирайте место сохранения осознанно. Буфер обмена по явному копированию может быть доступен другим приложениям.
- Нет телеметрии, сетевых логов, перехвата TLS и автоматического повтора оплачиваемой генерации. Backoff применяется только к GET /models до трёх попыток.
- Оценка токенов — грубая длина Unicode / 4 плюс служебные поправки, а не токенизатор модели; расчёт context window также приблизительный.
- Usage API заменяет оценки только после нормального завершения. Reasoning учитывается без двойного сложения. Цена приблизительна, без cache скидок/налогов.
- Отправка при уходе приложения с экрана не гарантируется Android: foreground service не реализован. Сохранённые частичные ответы переживают перезапуск, незавершённые помечаются interrupted.
- Импорт/экспорт JSON переносит чаты, сообщения и ветки; не является полным резервированием всех настроек и статистики. Нет вложений, tool calls и voice.
- Функциональная и визуальная проверка на Android, TalkBack и больших историях ещё необходима. Код не заявляется прошедшим сборку. См. `docs/VERIFICATION.md`.

## Документы и тесты
- [Архитектура](docs/ARCHITECTURE.md)
- [Провайдеры и протоколы](docs/PROVIDERS.md)
- [Безопасность](SECURITY.md)
- [Проверки](docs/VERIFICATION.md)
- [Вклад](CONTRIBUTING.md)

Без Android/Gradle можно выполнить `bash tools/offline-checks.sh`: он компилирует **production** SSE/TokenMath через javac и выполняет реальные assertions. Это не замена Android-сборке. `python3 tools/check_source.py` проверяет структуру, XML, конфигурацию и запрещённые файлы, но не компилирует Kotlin.

## Roadmap
После устранения блокировок: воспроизводимая зелёная сборка и live-provider матрица, полная ручная проверка TalkBack/ландшафта/планшета, benchmark длинной истории, полный зашифрованный backup и provider-specific model discovery pagination. Эти задачи не выдаются за выполненные.

## Лицензия
MIT, см. [LICENSE](LICENSE). Зависимости распространяются по собственным лицензиям.
