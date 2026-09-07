# Changelog

## 1.0.1
- Бамп версии: versionCode 2, versionName 1.0.1.
- Восстановлен оригинальный Components.kt до правок ИИ (revert 521886c).
- APK собирается в Android CI по тегу v1.0.1 и публикуется в GitHub Release.
- Подпись релиза выполняется только при наличии секретов подписи; без них публикуется явно неподписанный APK.

## 1.0.0 — unreleased source candidate
- Добавлен нативный Android-проект com.folzi.astrachat.
- Реализованы четыре адаптера протоколов, SSE, отмена, Room/Keystore, Compose-интерфейс, настройки и локальная статистика.
- Добавлены unit/instrumentation/Compose smoke tests, Android CI и документация.
- Android-сборка и GitHub-публикация заблокированы средой и правами. Это не подтверждённый выпущенный релиз.
