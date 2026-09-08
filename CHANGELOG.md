# Changelog

## Unreleased
- Исправлена компиляция инструментальных тестов: в Room 2.8.4 `RoomDatabase` больше не реализует `Closeable`/`AutoCloseable` (проверено `javap` по `room-runtime-android-2.8.4`), поэтому `use {}` в `app/src/androidTest/java/com/folzi/astrachat/StorageTest.kt` заменён на явный `try/finally` с `close()`.
- JUnit4 отклонял `StorageTest` с `InvalidTestClassError: Method migrationPreservesUsageAndAddsState() should be void`: выражение `= runBlocking { ... }` выводило `Boolean` из-за последнего `context.deleteDatabase(name)`. Оба корутин-теста закреплены как `runBlocking<Unit>`.
- До этих правок job `device-tests` падал на `:app:compileDebugAndroidTestKotlin` и на инициализации JUnit-раннера, а не на эмуляторе: эмуляторы API 26/35 в CI загружались штатно.
- Прогон 34154241379 (main, `809c963`) полностью зелёный: `verify` success, `device-tests (26)` success, `device-tests (35)` success — на эмуляторах реально выполнены Room-миграция 1→2, ветвление/бэкапы, SecretVault и Compose smoke-тест. Job `release` там skipped, потому что это не тег.
- Локально проверены только `:app:compileDebugAndroidTestKotlin` и `detektCheck`; AVD локально не запускался.
- Гейт релиза возвращён: `release: needs: [verify, device-tests]`. Оба джоба зелёные, поэтому публикация снова блокируется при падении инструментальных тестов; текст релиза больше не утверждает, что `device-tests` падают и не блокируют релиз.
- Редизайн интерфейса в нейтральной минималистичной манере Codex (web/IDE): графитовые палитры, hairline-рамки 1 dp, радиусы 6–16 dp вместо пилюль, один приглушённый синий акцент, спокойная типографика (полужирные заголовки с отрицательным трекингом, разрежённые подписи-капсы). В `ui/Theme.kt` добавлены палитры `graphiteDark`/`graphiteBlack`/`graphiteLight`, `codexTypography` и `codexShapes`; переключатель темы (`system`/`light`/`dark`/`amoled`) и Material You сохранены.
- Новые примитивы в `ui/Components.kt`: `Panel` (плоский лист с hairline-рамкой), `Hairline`, `SectionLabel`, `RoleLabel`, `PrimaryAction`. `Action` стал тихой текстовой кнопкой (без фона, радиус 8 dp, высота 44 dp), `Section` собирает содержимое в `Panel`, `TextField`/`Toggle`/`Confirm` приведены к той же гамме; подписи кнопок `Confirm` («Подтвердить»/«Отмена») не менялись.
- Markdown: блок кода теперь лист с hairline-рамкой, подписью языка моноширинным шрифтом и кнопкой «Копировать код»; подсветка дополнена комментариями, цвет строк зависит от яркости темы. Поведение `splitCode`, выделение текста и клики по ссылкам (`SelectableLinkMovementMethod`) не изменились.
- `ui/ChatScreen.kt`: лента стала транскриптом — без «пузырей», с метками ролей (`ВЫ` / `ASTRA`), hairline-разделителями между репликами и колонкой 800 dp с центрированием; топбар и композер отделены hairline-линиями, поле ввода вложено в панель без собственной рамки, статус генерации — моноширинная строка с индикатором и раскрывающимися деталями (TTFT, input/output/reasoning/cache, токены чата и за всё время). История чатов — панель-листы, активный чат выделен рамкой акцентного цвета. Логика (отправка/стоп, ветвление, автопрокрутка, поиск, диалоги) не менялась.
- `ui/ProvidersScreen.kt` и `ui/StatisticsScreen.kt`: `ElevatedCard` заменён на плоскую `Card` с hairline-рамкой — теней в интерфейсе не осталось.
- Добавлен инструментальный тест `hairlineSurfacesRenderTranscriptLabels`: проверяет рендер `Panel`/`RoleLabel`/`SectionLabel`/`Hairline` и клик по `PrimaryAction`. В `androidTest` теперь 5 тестов (3 в `StorageTest`, 2 в `ComposeSmokeTest`).
- Локально проверено: `detektCheck`, 20 unit-тестов (`AstraViewModelTest` 2, `NetworkTest` 7, `ProviderCodecTest` 11), `lint`, `assembleDebug`, `:app:compileDebugAndroidTestKotlin` — BUILD SUCCESSFUL; новых lint-предупреждений нет. Визуально редизайн не проверялся: подключённого устройства и AVD нет (`adb devices` пуст), подтверждение — компиляция, lint и тесты, а не просмотр интерфейса.

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
