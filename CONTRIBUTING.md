# Участие

Работайте в отдельной ветке. Перед изменениями изучите README, SECURITY, docs/ARCHITECTURE и имеющиеся PR/Issues. Не изменяйте main напрямую и не объединяйте PR без review владельца.

Требуются JDK 17, SDK 37, сеть к репозиториям зависимостей. Запускайте test, lint, detektCheck, assembleDebug, assembleRelease и instrumentation tests. При изменении Room увеличивайте version, добавляйте недеструктивную Migration и тест с сохранением реальных старых данных; сохраняйте сгенерированные Room schema JSON после первой успешной сборки. Не заявляйте непроведённые проверки успешными.

Commit conventions: chore, feat, fix, test, ci, docs. Используйте .github/PULL_REQUEST_TEMPLATE.md. Новый адаптер обязан проверять headers, request shape, SSE lifecycle, terminal events, usage, отмену и безопасные ошибки. Fixtures только в test, никогда не в production. Не добавляйте реальные секреты, APK, local.properties и signing keystore. Релиз допустим только после зелёных обязательных проверок.
