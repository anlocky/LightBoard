# Участие в проекте / Contributing

Проект открытый, вклад приветствуется: исправления, переводы, раскладки, словарные инструменты. / The project is open; fixes, translations, layouts and tools are welcome.

- Отправляя вклад, вы соглашаетесь, что он распространяется на условиях Apache License 2.0 (раздел 5), если вы явно не указали иное. / By submitting a contribution you agree that it is licensed under the Apache License 2.0 (section 5) unless you state otherwise explicitly.
- В исходном коде нет комментариев и упоминаний компаний и продуктов; названия API и сборки — исключение. Следуйте этому правилу. / The source has no comments and no mentions of companies or products (API and build names are the exception); please follow this rule.
- Перед отправкой запустите `gradle testDebugUnitTest`. Новое поведение покрывайте тестами для чистых классов (без Android). / Run `gradle testDebugUnitTest` before submitting and cover new pure (non-Android) logic with tests.
- Переводы: строки интерфейса настроек лежат в `L10n.java` (12 языков), строки клавиатуры — в `KbText.java`; в каждой строке должны быть все 12 столбцов, это проверяют тесты. / UI strings live in `L10n.java` (12 languages), keyboard strings in `KbText.java`; every row must have all 12 columns (tests check it).
- Не добавляйте разрешение на доступ в интернет, аналитику и сбор данных. / Do not add the internet permission, analytics or data collection.
