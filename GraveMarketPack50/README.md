# GraveMarket — могилы, экономика TeleportMarket (Paper 1.21.x)

GraveMarket сохраняет лут игрока в могиле (физической или виртуальной), ведёт аудит, поддерживает доверенных и возвращение предметов.  
Плагин рассчитан на Java 21, Paper/Spigot API 1.21.x, без NMS.

## Возможности

- **Смерть → могила**: лут не разлетается, сохраняется в могиле; опыт хранится отдельно.
- **Физическая могила**: контейнер из конфига (BARREL/CHEST/…); голограмма над могилой; лёгкие частицы.
- **Виртуальная могила**: если место опасно/не найдено — хранится в data-файле и переживает рестарт.
- **PvP‑политика**: часть предметов падает в мир (partialDropChance), остальное в могилу; извлечение дороже.
- **Доверенные**: /grave trust|untrust|trustlist, предметы помечаются как “чужие”, есть /grave return.
- **Анти‑абьюз**: нельзя класть предметы в могильный контейнер.
- **Экономика**: общий баланс TeleportMarket через ServicesManager, fallback на рефлексию; при отсутствии — внутренняя экономика.
- **Страховка**: /grave insure (скидка на извлечение, +10 минут жизни могилы).
- **Админ‑инструменты**: список/удаление могил, алтарь, reload.
- **Логи**: grave-audit.log со всеми ключевыми событиями.

## Установка

1. Скопируйте JAR в `plugins/`.
2. (Опционально) Установите TeleportMarket для общей экономики.
3. Перезапустите сервер.
4. Настройте `config.yml`.

## Сборка

Wrapper включён в репозиторий (Maven не требуется).

Windows (PowerShell, путь может содержать пробелы и скобки):
```powershell
cd "C:\Path With (Brackets)\GraveMarketPack50"
.\mvnw.cmd -DskipTests package
```

Linux/macOS:
```bash
cd GraveMarketPack50
chmod +x mvnw
./mvnw -DskipTests package
```

## Команды

**Игрок**
- `/grave info` — информация о могиле
- `/grave mark` — повтор координат и таймера
- `/grave pay` — оплатить извлечение
- `/grave compass` — компас на могилу (платно)
- `/grave beacon` — луч/частицы (платно)
- `/grave recall` — возврат вещей (платно, с кулдауном)
- `/grave tp` — телепорт к могиле (платно)
- `/grave trust <ник>` — доверить доступ
- `/grave untrust <ник>` — убрать доверие
- `/grave trustlist` — список доверенных
- `/grave return <ник>` — вернуть чужие предметы владельцу
- `/grave claim` — получить возвращённые предметы
- `/grave insure` — купить страховку

**Админ**
- `/graveadmin list [player]`
- `/graveadmin remove <graveId>`
- `/graveadmin give <graveId> <player>`
- `/graveadmin purge`
- `/graveadmin altar set`
- `/graveadmin altar info`
- `/graveadmin reload`

## Конфиг (основное)

```yml
graves:
  containerMaterial: BARREL
  safeSearchRadius: 8
  lifetimeSeconds: 1800
  expiredRetentionHours: 24
  maxActiveGravesPerPlayer: 1
  spawnParticlesOnCreate: true
  debug: false

pvp:
  enabled: true
  partialDropChance: 0.30
  extractCostMultiplier: 2.0

economy:
  prices:
    extract: 480
    compass: 960
    beacon: 2500
    recall: 7500
    tp: 9000
  recallCooldownSeconds: 1800
  recallVirtualMultiplier: 1.5
  tpCombatLockSeconds: 10
  tpSurgeWindowSeconds: 600
  tpSurgeStep: 0.25

insurance:
  price: 1500
  durationSeconds: 86400
  extractDiscountMultiplier: 0.7
  lifetimeBonusSeconds: 600
```

## Логи

Файл `plugins/GraveMarket/grave-audit.log` пишет:
`CREATE`, `OPEN`, `PAY_EXTRACT`, `TAKE_ITEM`, `RETURN`, `EXPIRE`, `VIRTUALIZE`, `DELETE`.
