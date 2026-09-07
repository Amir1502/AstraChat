# Changelog

## 1.0.1
- Бамп версии: versionCode 2, versionName 1.0.1.
- Исправлена компиляция `app/src/main/java/com/folzi/astrachat/ui/Components.kt`: `Markwon.Builder` в Markwon 4.6.2 не имеет `linkResolver`, поэтому резолвер http/https-ссылок теперь ставится через `AbstractMarkwonPlugin.configureConfiguration`.
- Ссылки кликабельны и при этом выделение текста сохранено: `SelectableLinkMovementMethod` наследует `ArrowKeyMovementMethod` и обрабатывает тапы по link-спанам.
- Job `release` больше не зависит от `device-tests`: эмуляторные прогоны (API 26/35) падают на hosted-раннерах и не блокируют публикацию собранных APK.
- Проверено локально: detekt, 20 unit-тестов, lint, assembleDebug и assembleRelease — зелёные; релизный APK без подписи.

## 1.0.0 — unreleased source candidate
- Добавлен нативный Android-проект com.folzi.astrachat.
- Реализованы четыре адаптера протоколов, SSE, отмена, Room/Keystore, Compose-интерфейс, настройки и локальная статистика.
- Добавлены unit/instrumentation/Compose smoke tests, Android CI и документация.
- Android-сборка и GitHub-публикация заблокированы средой и правами. Это не подтверждённый выпущенный релиз.
