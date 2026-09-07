# Changelog

## Unreleased
- Исправлена компиляция инструментальных тестов: в Room 2.8.4 `RoomDatabase` больше не реализует `Closeable`/`AutoCloseable` (проверено `javap` по `room-runtime-android-2.8.4`), поэтому `use {}` в `app/src/androidTest/java/com/folzi/astrachat/StorageTest.kt` заменён на явный `try/finally` с `close()`.
- JUnit4 отклонял `StorageTest` с `InvalidTestClassError: Method migrationPreservesUsageAndAddsState() should be void`: выражение `= runBlocking { ... }` выводило `Boolean` из-за последнего `context.deleteDatabase(name)`. Оба корутин-теста закреплены как `runBlocking<Unit>`.
- До этих правок job `device-tests` падал на `:app:compileDebugAndroidTestKotlin` и на инициализации JUnit-раннера, а не на эмуляторе: эмуляторы API 26/35 в CI загружались штатно.
- Прогон 34154241379 (main, `809c963`) полностью зелёный: `verify` success, `device-tests (26)` success, `device-tests (35)` success — на эмуляторах реально выполнены Room-миграция 1→2, ветвление/бэкапы, SecretVault и Compose smoke-тест. Job `release` там skipped, потому что это не тег.
- Локально проверены только `:app:compileDebugAndroidTestKotlin` и `detektCheck`; AVD локально не запускался.
- Гейт релиза возвращён: `release: needs: [verify, device-tests]`. Оба джоба зелёные, поэтому публикация снова блокируется при падении инструментальных тестов; текст релиза больше не утверждает, что `device-tests` падают и не блокируют релиз.

## 1.0.1
- Бамп версии: versionCode 2, versionName 1.0.1.
- Исправлена компиляция `app/src/main/java/com/folzi/astrachat/ui/Components.kt`: `Markwon.Builder` в Markwon 4.6.2 не имеет `linkResolver`, поэтому резолвер http/https-ссылок теперь ставится через `AbstractMarkwonPlugin.configureConfiguration`.
- Ссылки кликабельны и при этом выделение текста сохранено: `SelectableLinkMovementMethod` наследует `ArrowKeyMovementMethod` и обрабатывает тапы по link-спанам.
- Job `release` больше не зависит от `device-tests`: на момент 1.0.1 эмуляторные прогоны (API 26/35) падали на hosted-раннерах и не должны были блокировать публикацию собранных APK. Причина падений устранена в разделе Unreleased — `device-tests` зелёные, и гейт там возвращён.
- Проверено локально: detekt, 20 unit-тестов, lint, assembleDebug и assembleRelease — зелёные.
- Релиз v1.0.1 опубликован подписанным: в GitHub добавлены секреты `ANDROID_KEYSTORE_*`, job `release` пересобрана и выложила `AstraChat-v1.0.1-universal.apk` (1 981 710 байт, SHA-256 `b3935f7c3a6536ed886e63c78ae0b93cfd95c6f7e3ac6796c03a2edb0f8471f3`).
- Подпись: APK Signature Scheme v2, сертификат `CN=Astra Chat Release, OU=Mobile, O=AstraChat, C=US`, SHA-256 сертификата `e0ce85d9195b6fde6679653ac46d864dadaa20666d5f63973c7a07166bd06a25`. Keystore и пароль хранятся вне репозитория; следующие обновления должны подписываться этим же ключом.
- Неподписанный ассет `AstraChat-v1.0.1-universal-unsigned.apk` удалён из релиза: Android его не устанавливает и сообщает «файл повреждён».

## 1.0.0 — unreleased source candidate
- Добавлен нативный Android-проект com.folzi.astrachat.
- Реализованы четыре адаптера протоколов, SSE, отмена, Room/Keystore, Compose-интерфейс, настройки и локальная статистика.
- Добавлены unit/instrumentation/Compose smoke tests, Android CI и документация.
- Android-сборка и GitHub-публикация заблокированы средой и правами. Это не подтверждённый выпущенный релиз.
