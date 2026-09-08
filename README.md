<div align="center">

# Astra Chat

### Свой AI-чат на Android: ваши провайдеры, ваши ключи, ваша история

Нативный клиент для OpenAI, Anthropic, Gemini, DeepSeek, Mistral, xAI, OpenRouter, Groq и Ollama.
Kotlin 2.3 · Jetpack Compose · Material 3 · Room · OkHttp · Hilt · MIT

[![Android CI](https://github.com/Amir1502/AstraChat/actions/workflows/android.yml/badge.svg)](https://github.com/Amir1502/AstraChat/actions/workflows/android.yml)
[![Последний релиз](https://img.shields.io/github/v/release/Amir1502/AstraChat?label=релиз)](https://github.com/Amir1502/AstraChat/releases/latest)
[![Лицензия MIT](https://img.shields.io/badge/лицензия-MIT-3b6ea8)](LICENSE)
![minSdk 26](https://img.shields.io/badge/minSdk-26%20·%20Android%208.0-555555)
![targetSdk 37](https://img.shields.io/badge/targetSdk-37-555555)
![APK](https://img.shields.io/badge/APK-1.98%20МБ%20universal-555555)

<a id="nav"></a>
[Установка](#install) · [Подключение API](#start) · [Возможности](#features) · [Дизайн](#design) · [Сборка](#build) · [Тесты и CI](#ci) · [Приватность](#security) · [Статус проверок](#status)

</div>

---

> **In English.** Astra Chat is a native Android client for the LLM APIs you already pay for. Bring your own keys — OpenAI, Anthropic, Gemini, DeepSeek, Mistral, xAI, OpenRouter, Groq or a local Ollama — stream answers over SSE, branch conversations, and keep everything on the device: keys in Android Keystore, history in a private Room database, no telemetry, no cloud sync, no bundled inference engine. Kotlin and Jetpack Compose, MIT licensed, Android 8.0+ (minSdk 26). Signed universal APK: [Releases](https://github.com/Amir1502/AstraChat/releases/latest). The rest of this document is in Russian.

---

<a id="install"></a>
## Установка

1. Скачайте `AstraChat-v1.0.3-universal.apk` со страницы [последнего релиза](https://github.com/Amir1502/AstraChat/releases/latest).
2. Сверьте хеш со строкой в `SHA256SUMS.txt` из того же релиза — значения должны совпасть (хеш продублирован в описании релиза и в [CHANGELOG.md](CHANGELOG.md)):
   ```powershell
   # Windows PowerShell
   Get-FileHash AstraChat-v1.0.3-universal.apk -Algorithm SHA256
   ```
   ```bash
   # Linux / macOS
   sha256sum AstraChat-v1.0.3-universal.apk
   ```
3. Откройте файл на устройстве, разрешите установку из неизвестных источников. Если Play Protect предупреждает — «Подробнее → Установить всё равно»: приложение не публиковалось в Google Play.

| Параметр | Значение |
|---|---|
| Версия | 1.0.3 (versionCode 4) |
| Android | 8.0 и выше (minSdk 26), targetSdk 37 |
| ABI | universal: arm64-v8a, armeabi-v7a, x86, x86_64 |
| Размер | 1.98 МБ (R8-минификация и сжатие ресурсов включены) |
| Подпись | APK Signature Scheme v2, сертификат `CN=Astra Chat Release, OU=Mobile, O=AstraChat, C=US` |
| Отпечаток сертификата | `e0ce85d9195b6fde6679653ac46d864dadaa20666d5f63973c7a07166bd06a25` |
| Телеметрия, аналитика, облако | отсутствуют |
| Лицензия | MIT |

**Обновление и типичные ошибки установки**

Обновление между 1.0.1, 1.0.2 и 1.0.3 ставится поверх: ключ подписи один и тот же, удалять приложение и терять историю не нужно.

| Симптом | Причина | Что делать |
|---|---|---|
| «Файл повреждён», установка прерывается | скачан неподписанный APK (ассет `*-universal-unsigned.apk` в релизе v1.0.0) | берите `*-universal.apk` из v1.0.1 и новее — он подписан |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | прежняя сборка подписана другим ключом (v1.0.0 или debug) | удалите старое приложение, затем установите новое; история при этом теряется |
| «Неизвестный источник» | политика Android для файлов из браузера | разрешите установку из браузера или файлового менеджера |

Проверить подпись установленного файла можно и на компьютере: `apksigner verify --print-certs AstraChat-v1.0.3-universal.apk` (build-tools Android SDK).

<a id="start"></a>
## Подключение API за две минуты

1. **Меню → Провайдеры.** Выберите готовый профиль или «Добавить».
2. **Base URL — с префиксом версии**, например `https://api.openai.com/v1`. Укажите протокол и API-ключ. Относительный endpoint адаптер подставит сам.
3. **«Получить модели»** или «Добавить модель»: реальный model ID, context window, при желании цена USD за 1M токенов.
4. **Включайте возможности осознанно.** У новых моделей temperature, top_p, stop и reasoning_effort выключены по умолчанию — включайте только то, что документировано для вашей модели. Для reasoning-моделей Chat Completions может понадобиться `max_completion_tokens`.
5. **«Использовать по умолчанию»**, затем в чате кнопка «Модель» — и можно отправлять сообщение.

| Профиль | Base URL | Формат | Авторизация |
|---|---|---|---|
| OpenAI | `https://api.openai.com/v1` | Chat Completions (или Responses) | `Authorization: Bearer` |
| Anthropic | `https://api.anthropic.com/v1` | Messages | `x-api-key` + `anthropic-version` |
| Gemini | `https://generativelanguage.googleapis.com/v1beta` | generateContent / streamGenerateContent | `x-goog-api-key` |
| DeepSeek | `https://api.deepseek.com/v1` | Chat Completions | Bearer |
| Mistral | `https://api.mistral.ai/v1` | Chat Completions | Bearer |
| xAI | `https://api.x.ai/v1` | Chat Completions | Bearer |
| OpenRouter | `https://openrouter.ai/api/v1` | Chat Completions | Bearer |
| Groq | `https://api.groq.com/openai/v1` | Chat Completions | Bearer |
| Ollama | `http://127.0.0.1:11434/v1` | OpenAI-совместимый локальный | ключ не обязателен |

Полное описание протоколов, обработки ошибок и параметров моделей — в [docs/PROVIDERS.md](docs/PROVIDERS.md).

**Ollama.** `127.0.0.1` на телефоне — это само устройство. Для эмулятора используйте `http://10.0.2.2:11434/v1`, для телефона — частный IPv4-адрес компьютера в той же сети. Локальный HTTP включайте только в доверенной сети: публичный HTTP и непроверенные TLS-сертификаты приложение запрещает.

**«Проверить подключение»** отправляет реальный короткий запрос и отдельно спрашивает подтверждение: он может тарифицироваться провайдером и не попадает в статистику чатов. Ответ `/models` иногда имеет другие права или неполную пагинацию — модель всегда можно добавить вручную.

<a id="features"></a>
## Возможности

**Провайдеры и протоколы**
- Девять профилей и четыре отдельных wire-формата: Chat Completions, Responses, Anthropic Messages, Gemini.
- Создание, редактирование, копирование профиля без секретов, удаление; относительный endpoint с проверкой origin; зашифрованные заголовки и query-параметры.
- Проверка подключения реальным запросом с явным подтверждением.

**Чат и генерация**
- Постепенная выдача по SSE, отмена HTTP-вызова, сохранение частичного ответа, защита от двойной отправки.
- Живая телеметрия запроса: TTFT, длительность, tokens/sec, input/output/reasoning/cached/total.
- Оценка токенов контекста прямо в композере до отправки.

**История и ветки**
- Название, закрепление, удаление чатов, поиск по названию, группировка по дням.
- Ветки и регенерация без перезаписи оригинала; сообщения до точки ветвления копируются с новыми ID.
- Черновики, восстановление незавершённых ответов после перезапуска (помечаются как interrupted).

**Markdown и код**
- Markdown, таблицы, зачёркивание, ссылки только `http(s)`, выделение текста и клики по ссылкам одновременно.
- Fenced-блоки кода отдельными листами: подпись языка, базовая лексическая подсветка (ключевые слова, строки, комментарии), горизонтальная прокрутка, копирование.

**Данные и статистика**
- Room: чаты, сообщения, ветки, провайдеры, модели, usage, черновики; миграция 1→2 без destructive fallback.
- Статистика за сегодня / 7 / 30 дней / всё время, в разрезе провайдера и модели; приблизительная стоимость в USD.
- Экспорт переписки в Markdown и резервной копии в JSON через системный выбор файла; импорт JSON с новыми ID и без перезаписи существующих чатов.

**Оформление**
- System / light / dark / AMOLED (настоящий чёрный), динамические цвета Android 12+, масштаб текста сообщений 0.9–1.5×, отключение анимаций.
- Адаптивная раскладка: постоянная боковая панель истории на экранах от 840 dp, drawer на узких.
- Скриншоты разрешены по умолчанию; переключатель «Приватность экрана» возвращает защиту окна флагом `FLAG_SECURE` (см. [Скриншоты](#screens)).

<a id="design"></a>
## Дизайн

Интерфейс начиная с 1.0.2 — нейтральный минимализм в духе инструментов для кода: плоские графитовые поверхности, hairline-рамки 1 dp вместо теней, один приглушённый синий акцент, никакого «пузырного» чата.

| Токен | Тёмная тема | Светлая тема | AMOLED |
|---|---|---|---|
| Фон и поверхность | `#141518` | `#FBFBFA` / `#FFFFFF` | `#000000` |
| Контейнеры | `#1A1C1F` · `#202226` | `#F5F5F4` · `#EFEFEE` | `#101114` · `#16181B` |
| Текст | `#E8E9EA` | `#1A1B1E` | `#E8E9EA` |
| Приглушённый текст | `#A2A6AC` | `#5F6368` | `#A2A6AC` |
| Hairline-линии | `#26282D` | `#E6E7E9` | `#1E2024` |
| Акцент | `#7FA8DC` | `#3B6EA8` | `#7FA8DC` |

- **Формы:** радиусы 6 / 8 / 10 / 12 / 16 dp, пилюль нет; карточки и панели — листы с hairline-рамкой.
- **Типографика:** полужирные заголовки с трекингом −0.01…−0.02 em, подписи-капсы с разрежением 0.06–0.08 em, моноширинный шрифт для кода и телеметрии.
- **Чат как транскрипт:** метки ролей `ВЫ` / `ASTRA`, hairline-разделители между репликами, центрированная колонка 800 dp, композер в панели без собственной рамки.
- **Примитивы:** `Panel`, `Hairline`, `SectionLabel`, `RoleLabel`, `PrimaryAction` и тихая текстовая `Action` — в `app/src/main/java/com/folzi/astrachat/ui/Components.kt`.

<a id="screens"></a>
### Скриншоты

Снимков в репозитории пока нет, и «нарисованных» макетов здесь не будет — только реальные кадры.

- Начиная с **1.0.3** скриншоты разрешены по умолчанию: окно снимается штатными средствами Android, содержимое видно в недавних приложениях.
- Переключатель **Настройки → Приватность экрана → «Разрешить скриншоты и превью в недавних»** возвращает защиту `FLAG_SECURE`, если его выключить; применяется сразу, без перезапуска.
- В APK **1.0.2 и старше** окно защищено постоянно — снять экран нельзя.
- Чтобы добавить снимки в этот раздел: сделайте кадры на устройстве, положите PNG в [`docs/screenshots/`](docs/screenshots/README.md) и вставьте ссылки сюда. Публикуйте только пустые или вымышленные чаты — не выкладывайте настоящую переписку и ключи.

<a id="build"></a>
## Сборка из исходников

**Нужно:** JDK 17 и Android SDK (cmdline-tools, platform-tools, `platforms;android-37.0`, `build-tools;36.0.0`). Gradle ставить не требуется: `gradlew` — это source-bootstrap (`tools/GradleBootstrap.java`), который однократно скачивает закреплённый Gradle 9.3.1 из `gradle/wrapper/gradle-wrapper.properties` в `~/.gradle/astra-bootstrap`. Официального wrapper-JAR в репозитории нет намеренно.

```powershell
# Windows
$env:ANDROID_HOME = "C:\Android\Sdk"
.\gradlew.bat assembleDebug          # app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat assembleRelease        # подписывается, если заданы переменные ниже
.\gradlew.bat detektCheck test lint  # статический анализ, 20 unit-тестов, Android Lint
```

```bash
# Linux / macOS
export ANDROID_HOME="$HOME/Android/Sdk"
./gradlew assembleDebug assembleRelease
./gradlew detektCheck test lint
python3 tools/check_source.py        # политика исходников
bash tools/offline-checks.sh         # офлайн-проверки wire-логики
```

**Инструментальные тесты** требуют эмулятор или устройство (adb) и выполняются так: `./gradlew connectedDebugAndroidTest`. В CI они идут на эмуляторах API 26 и 35.

**Подписанный release.** Без переменных signing собирается `app-release-unsigned.apk` — он не устанавливается. Для подписи нужны:

| Переменная | Содержимое |
|---|---|
| `ANDROID_KEYSTORE_PATH` | путь к файлу `.jks` / `.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | пароль хранилища |
| `ANDROID_KEY_ALIAS` | алиас ключа |
| `ANDROID_KEY_PASSWORD` | пароль ключа |

В GitHub Actions те же четыре значения лежат в секретах репозитория (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), поэтому релизные теги подписываются автоматически. Keystore и пароли в репозиторий не коммитятся; потеря ключа означает, что обновления перестанут устанавливаться поверх текущей версии.

**Если сборка не находит платформу.** `compileSdk = 37`, а `sdkmanager` может поставить каталог `platforms/android-37.0` вместо ожидаемого `platforms/android-37`. Помогает ссылка:
```powershell
cmd /c mklink /J "C:\Android\Sdk\platforms\android-37" "C:\Android\Sdk\platforms\android-37.0"
```
```bash
ln -s "$ANDROID_HOME/platforms/android-37.0" "$ANDROID_HOME/platforms/android-37"
```

<a id="ci"></a>
## Тесты и CI

Workflow [`Android`](.github/workflows/android.yml) запускается на каждый push, pull request и тег `v*`.

| Job | Когда | Что делает |
|---|---|---|
| `verify` | всегда | `tools/check_source.py`, `tools/offline-checks.sh`, `detektCheck`, unit-тесты, Android Lint, `assembleDebug` + `assembleRelease`, артефакты APK и отчёты |
| `device-tests` (API 26, 35) | после `verify` | эмулятор x86_64 с KVM, `connectedDebugAndroidTest`, отчёты инструментальных тестов |
| `release` | только теги `v*` | universal APK, подпись из секретов `ANDROID_KEYSTORE_*`, публикация GitHub Release с `SHA256SUMS.txt` и удаление устаревшего unsigned-ассета |

Релиз публикуется только если `verify` **и** `device-tests` зелёные.

| Тесты | Состав |
|---|---|
| Unit (JVM), 20 | `AstraViewModelTest` — 2, `NetworkTest` — 7, `ProviderCodecTest` — 11 |
| Инструментальные, 5 | `StorageTest` — миграция Room 1→2, ветвление и бэкапы, шифр SecretVault; `ComposeSmokeTest` — диалог подтверждения и рендер новых UI-примитивов |

<a id="security"></a>
## Безопасность и приватность

- API-ключи, произвольные заголовки и query-параметры: AES-GCM с уникальным IV и AAD из provider ID, ключ в Android Keystore. В Room и обычном DataStore секретов нет.
- Чаты лежат в приватной Room-базе, но **отдельным ключом не зашифрованы** — защита зависит от шифрования диска Android. Root или скомпрометированное устройство полностью не защищены.
- Android backup выключен. Экспорт переписки не шифруется: выбирайте место сохранения осознанно. Буфер обмена при явном копировании доступен другим приложениям.
- Скриншоты с 1.0.3 разрешены по умолчанию. Защита окна `FLAG_SECURE` (блокировка системных снимков и миниатюр в недавних приложениях) включается переключателем «Настройки → Приватность экрана»; в 1.0.2 и старше она постоянная. Помните, что разрешённые снимки попадают в галерею и доступны другим приложениям.
- Нет телеметрии, сетевых логов, перехвата TLS и автоматического повтора оплачиваемой генерации. Backoff применяется только к `GET /models` (до трёх попыток).
- Ссылки в ответах открываются только для `http` и `https`.

**Известные ограничения**

- Оценка токенов — грубая длина Unicode / 4 со служебными поправками, а не токенизатор модели; расчёт context window тоже приблизительный. Оценки всегда помечены.
- Usage из API заменяет оценки только после нормального завершения запроса; reasoning учитывается без двойного сложения; цена приблизительная, без скидок на cached tokens и налогов.
- Отправка при уходе приложения с экрана не гарантируется Android: foreground service не реализован. Сохранённые частичные ответы переживают перезапуск, незавершённые помечаются interrupted.
- Импорт/экспорт JSON переносит чаты, сообщения и ветки, но не ключи, настройки, черновики и биллинговую статистику. Вложений, tool calls и голоса нет.

<a id="status"></a>
## Статус проверок

| Что | Как проверено |
|---|---|
| Сборка и статический анализ | `verify` в CI: detekt, 20 unit-тестов, Android Lint, `assembleDebug` + `assembleRelease` — зелёные |
| Поведение на эмуляторе | `device-tests` API 26 и 35: миграция Room 1→2, ветвление и бэкапы, SecretVault, Compose-тесты — зелёные |
| Релизные артефакты | `apksigner verify --print-certs`, `aapt2 dump badging`, сверка SHA-256 с `SHA256SUMS.txt` |
| **Не проверено** | установка на реальное устройство, ручное визуальное QA, TalkBack, ландшафт и планшет, длинные истории, реальные запросы к платным API |

Подробный журнал проверок, хеши и отпечатки сертификатов — в [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Структура проекта

```
app/       Android-модуль: Compose UI, ViewModel, Hilt DI, Room, DataStore, Keystore
core/      JVM-модуль без Android: модели, wire-codecs, SSE, endpoint-политика, метрики
docs/      ARCHITECTURE.md · PROVIDERS.md · VERIFICATION.md · screenshots/
tools/     check_source.py · offline-checks.sh · GradleBootstrap.java
.github/   workflow Android · шаблон pull request · шаблон bug report
```

| Документ | О чём |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | модули, поток запроса, схема Room и миграции, UX-решения, ограничения |
| [docs/PROVIDERS.md](docs/PROVIDERS.md) | профили, wire-форматы, обработка ошибок, параметры моделей, подсчёт токенов |
| [docs/VERIFICATION.md](docs/VERIFICATION.md) | что и как проверялось локально и в CI, хеши артефактов, отпечатки сертификатов |
| [CHANGELOG.md](CHANGELOG.md) | изменения по версиям |
| [SECURITY.md](SECURITY.md) | модель защиты, шифрование секретов, куда сообщать об уязвимости |
| [CONTRIBUTING.md](CONTRIBUTING.md) | сборка, обязательные проверки перед PR, конвенции коммитов |
| [docs/screenshots/](docs/screenshots/README.md) | куда класть снимки экрана и как их делать |

Ошибки и предложения — через [issues](https://github.com/Amir1502/AstraChat/issues/new/choose), есть шаблон bug report. Не публикуйте в issue API-ключи, заголовки провайдеров и переписку.

## Дорожная карта

Сделано: воспроизводимая зелёная сборка в CI, подписанные релизы, инструментальные тесты на эмуляторах API 26 и 35, редизайн интерфейса, настраиваемая защита окна (скриншоты разрешены по умолчанию).

Дальше: полное ручное QA на устройстве (установка, снимки экрана, TalkBack, ландшафт, планшет, длинные истории) и публикация скриншотов, бенчмарк большой истории, зашифрованное резервное копирование, пагинация model discovery у разных провайдеров, вложения и tool calls. Эти задачи не выдаются за выполненные.

## Лицензия

MIT, см. [LICENSE](LICENSE). Зависимости распространяются по собственным лицензиям. Приложение не связано с OpenAI, Anthropic, Google, DeepSeek, Mistral, xAI, OpenRouter, Groq или Ollama и не публикует их ключи.
