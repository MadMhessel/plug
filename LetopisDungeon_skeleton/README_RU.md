# LetopisDungeon — сессионный данж с «режиссёром» (Paper 1.21.x)

Каркас под Paper‑плагин: забеги по данжу в отдельном мире + «режиссёр» (напряжение, анти‑стойка, развилки, волны, мини‑боссы).

## Почему плагин, а не чистый датапак
Датапак хорош для контента (структуры комнат), но сессии/участники/таймеры/защита/интерфейс/диагностика стабильнее и удобнее в плагине.  
Рекомендация: комнаты хранить как структуры датапака (.nbt), а плагин — «режиссировать» и вызывать их размещение.

## Быстрый старт
1) Установи Java 21  
2) Если нет gradlew.* — создай wrapper:
   `gradle wrapper --gradle-version 8.10.2`
3) Сборка: `scripts\build.bat` (или `./gradlew.bat clean build`)
4) JAR: `build/libs/LetopisDungeon-0.1.0.jar` → `plugins/`
5) (опционально) Датапак комнат: `datapack/LetopisDungeonRooms` → `world/datapacks/` → `/reload`

## Команды (MVP)
- `/letodungeon help`
- `/letodungeon join`
- `/letodungeon leave`
- `/letodungeon status`
- `/letodungeon guide` (книга‑инструкция)
- Админ: `/letodungeon admin start|stop|reload|set`

Полное ТЗ на доведение — в `PROMPT_CODEX_RU.md`.
