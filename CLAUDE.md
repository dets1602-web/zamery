# Замеры — для Claude

- Kotlin + Jetpack Compose, один модуль `app`. Без БД: `Store` в Data.kt пишет JSON в filesDir.
- Сборка: push в main → GitHub Actions → релиз `latest`, постоянная ссылка
  https://github.com/dets1602-web/zamery/releases/download/latest/Zamery.apk
- Репозиторий публичный (бесплатная сборка). Личных данных в коде нет.
- `app/debug.keystore` — постоянный ключ подписи, не менять (иначе обновление не встанет поверх).
- В сессии Claude нет Android SDK — компилируется только на GitHub; ошибки читать через
  `gh api repos/dets1602-web/zamery/check-runs/<job>/annotations` (только REST, GraphQL запрещён).
- Перед правкой интерфейса читать PRODUCT.md и DESIGN.md.
- Датчик для «Уклада»: UkladSensor.kt, action `ru.uklad.SENSOR`, ответ JSON в resultData (сфера «Здоровье»).
  Договор — docs/sensors.md в репозитории uklad. Переписка — «Почта между чатами», раздел «Замерам».
