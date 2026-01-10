# NightPact (каркас плагина)

**Сон как договор**: игроки голосуют *кроватью*, а после пропуска ночи сервер применяет «послевкусие сна»
(умеренные эффекты/мини-события). Включены фишки:
- роли «спящие/дежурные»,
- «комбо-сон» (поощряет сон рядом),
- «тревожный сон» (враждебные мобы рядом / недавний бой),
- полоса прогресса подготовки,
- настраиваемые веса эффектов.

## Сборка
Требования: Java 21 + Maven.

```bash
mvn -DskipTests package
```

Готовый JAR: `target/nightpact-0.1.0-SNAPSHOT.jar`

## Установка
1. Положите JAR в папку `plugins/`
2. Запустите сервер (создастся `plugins/NightPact/config.yml`)
3. Настройте `enabled_worlds`, `required_ratio`, эффекты и веса

## Команды
- `/nightpact status [world]` — статус по миру (сколько спят/сколько нужно, идёт ли подготовка)
- `/nightpact reload` — перезагрузка config.yml
- `/nightpact force [world]` — принудительно запустить пропуск (админам)
- `/nightpact debug on|off` — включить/выключить внутренний дебаг

## Новые настройки
Ключевые блоки в `config.yml`:
- `settings.reset_phantom_timer.mode` — сброс таймера фантомов (SLEEPERS/ALL/NONE).
- `settings.anxious_sleep.mode` и `settings.anxious_sleep.hostile_entity_types` — контроль тревожного сна.
- `effects.prophetic_dream.cooldown_minutes` и `effects.prophetic_dream.cache_minutes` — защита от частых поисков.
- `effects.night_deal.cooldown_minutes` — кулдаун на раздачу лута.
- `effects.bad_dream.disable_in_peaceful`, `spawn_protection_radius`, `require_surface` — защита от абьюза.

## Примечание
Это каркас: логика уже работает, но эффекты и баланс рассчитаны как «безопасные по умолчанию».
Под ваш сервер их стоит подстроить (особенно `bad_dream` и лут).
