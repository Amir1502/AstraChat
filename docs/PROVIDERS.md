# Провайдеры

| Профиль | Base URL | Формат | Ключ |
|---|---|---|---|
| OpenAI | https://api.openai.com/v1 | Chat Completions; можно выбрать Responses | Authorization Bearer |
| Anthropic | https://api.anthropic.com/v1 | Messages | x-api-key, anthropic-version |
| Gemini | https://generativelanguage.googleapis.com/v1beta | generateContent / streamGenerateContent?alt=sse | x-goog-api-key |
| DeepSeek | https://api.deepseek.com/v1 | Chat Completions | Bearer |
| Mistral | https://api.mistral.ai/v1 | Chat Completions | Bearer |
| xAI | https://api.x.ai/v1 | Chat Completions | Bearer |
| OpenRouter | https://openrouter.ai/api/v1 | Chat Completions | Bearer |
| Groq | https://api.groq.com/openai/v1 | Chat Completions | Bearer |
| Ollama | http://127.0.0.1:11434/v1 | OpenAI-compatible local endpoint | необязателен |

Модели не выдаются за доступные без API: добавьте действительный ID вручную либо выполните model discovery. Default endpoint дописывается к Base URL; например `/v1` + `chat/completions`, а не `/v1/v1/...`. Ручной endpoint остаётся на том же origin. Нельзя задавать абсолютный URL, fragment, query или переход `..` в endpoint.

Chat Completions: messages, role/content, stream_options.include_usage (переключаемо), delta.content, финальный usage, [DONE].
Responses: input/instructions/store=false/max_output_tokens; response.output_text.delta и response.completed. response.failed/error — безопасная ошибка, response.incomplete — прерванный ответ.
Anthropic: system вне messages, x-api-key и anthropic-version=2023-06-01; message_start usage + text_delta + message_delta usage + message_stop. Cached input прибавляется к input_tokens ровно один раз.
Gemini: systemInstruction/contents/parts/generationConfig; model в URL, ключ в header; text parts без thought; usageMetadata. candidatesTokenCount + thoughtsTokenCount — output, totalTokenCount сохраняется напрямую. После finishReason поток дочитывается ради финального usage.

Любой протокол поддерживает non-streaming; его тело разбирается соответствующим форматом. Raw provider errors никогда не отображаются. GET models имеет ограниченный exponential backoff для 429/502/503/504; POST generation автоматически не повторяется.

## Параметры моделей
Возможности задаются отдельно для каждой модели. Новые manually-added/fetched модели имеют консервативные capabilities; не отправляются temperature/top_p/stop/reasoning без включения. Для известных префиксов OpenAI reasoning при discovery выбирается max_completion_tokens. Responses не отправляет stop. Anthropic не отправляет top_p одновременно с явно заданной temperature. Gemini переименовывает top_p/stop в topP/stopSequences.

Дополнительный JSON — осознанный escape hatch для документированных API-параметров. Он не может подменять model/messages/input/contents/system/stream или ключ авторизации. Для Gemini содержимое помещается в generationConfig. Невозможно автоматически знать capabilities любого будущего пользовательского API; проверку производите по документации модели.

## Подсчёт и погрешность
Fallback: ceil(Unicode code points / 4), добавлены небольшие служебные поправки. Это не точный BPE/SentencePiece, для многих языков погрешность значительна. Оценки всегда отмечаются. Cached/reasoning показываются только если API их вернул. Цена USD/1M задаётся пользователем и остаётся приблизительной. Тест подключения делает короткий реальный completion, отдельно предупреждает о тарификации и не входит в chat usage.

Пример безопасной конфигурации: `examples/provider.json`. В нём нет секретов. Произвольные headers/query задавайте только в зашифрованном редакторе приложения.
