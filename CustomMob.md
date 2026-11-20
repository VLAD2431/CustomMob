Ты — опытный Java-разработчик плагинов под Minecraft (Paper/Spigot API). Напиши полноценный, готовый к компиляции плагин для версии **Minecraft 1.21.7** (Java 21), который добавляет двух кастомных мобов для ивента «Волны рок мороза»: **СкелеБас** и **ДенсБомбер**.

Главное требование: **никакого хардкода**. Все параметры (ХП, урон, скорость, куллдауны, дистанции, имена для привязки моделей, видимость имени, материалы, CustomModelData, звуки и партиклы со всеми их параметрами) должны загружаться из `config.yml` и обновляться командой `/custommob reload`.

---

## 1. Общие требования и команды
1. **Ядро и версия:** Paper API 1.21.7.
2. **Команды (с поддержкой TabCompleter и правами `rockfrost.admin`):**
   - `/custommob spawn skelebass [локация/игрок]` — заспавнить СкелеБаса.
   - `/custommob spawn dancebomber [локация/игрок]` — заспавнить ДенсБомбера.
   - `/custommob reload` — перезагрузить `config.yml`.
3. **Привязка текстур/моделей (OptiFine / EMF / CEM):**
   - Текстуры накладываются на мобов за счет их `customName`.
   - Параметр видимости ника над головой (`setCustomNameVisible(boolean)`) должен браться из конфига (по умолчанию `false`, чтобы игрок не видел имя над головой, но ресурспак считывал его).
   - Мобы не должны гореть на солнце и не должны подбирать чужие предметы.

---

## 2. Моб №1: СкелеБас (SkeleBass)
* **Базовая сущность:** Скелет (`EntityType.SKELETON`, настраивается в конфиге). Ванильный лук в руке не нужен (или скрыт/заменен по конфигу), стандартная стрельба стрелами отключена.
* **Имя сущности (для модели):** `"СкелеБас"` (настраивается).
* **Поведение (ИИ):** Держится на расстоянии от игрока (кайтит, например, пытается сохранять дистанцию 10–15 блоков, если игрок подходит ближе — отходит назад) и раз в N тиков стреляет «бассом».
* **Механика выстрела (Прожектайл Басса):**
  - При выстреле проигрывается звук: `quack_mob:skelebass.attack` (категория `HOSTILE`, громкость `1.0`, питч `1.0`).
  - Выпускается кастомный прожектайл (реализованный через `BukkitRunnable` и векторное движение, так как он должен **проходить сквозь стены/блоки** и не исчезать при столкновении с блоками).
  - **Дальность полёта:** `50` блоков.
  - **Скорость полёта:** `1.5` блока в тик.
  - **Попадание по врагу (игроку):** наносит настраиваемый урон и сильно отталкивает по вектору полёта снаряда (сила отталкивания настраивается в конфиге). Пробивает ли он нескольких игроков или исчезает при первом попадании в игрока — вынести булевым параметром `pierce-entities` в конфиг.
  - **Звук летящего прожектайла (каждый тик в точке снаряда для игроков в радиусе 20 блоков):**
    `block.note_block.bass` (категория `HOSTILE`, громкость `0.5`, питч `0.0`).
  - **Партиклы летящего прожектайла (каждый тик в точке снаряда):**
    1. `DUST_COLOR_TRANSITION`: переход цвета из RGB `[1.0, 0.0, 0.0]` (255, 0, 0) в `[0.0, 0.43, 1.0]` (0, 110, 255), размер `scale: 3.0`, смещение `0.1, 0.1, 0.1`, скорость `0`, кол-во `4`.
    2. `NOTE`: смещение `0.35, 0.0, 0.0`, скорость `1.0`, кол-во `0`.
    3. `FLASH`: смещение `0.2, 0.2, 0.2`, скорость `0`, кол-во `1` *(Примечание для 1.21.7: в Paper API `Particle.FLASH` может требовать `Color`, учти это при спавне частицы)*.

---

## 3. Моб №2: ДенсБомбер (DanceBomber)
* **Базовая сущность:** Скелет или Зомби (`EntityType.SKELETON`, настраивается в конфиге).
* **Два состояния (фазы):**

### Фаза 1: С головой (Дальний бой)
* **Имя сущности:** `"ДенсБомбер"` (`CustomNameVisible: false`).
* **Поведение:** Держится на расстоянии от игрока (как скелет) и кидает в него свою голову-бомбу.
* **Бросок головы:**
  - В момент броска на месте головы моба **один раз** проигрываются:
    - Партикл: `CLOUD` (смещение `0.2, 0.2, 0.2`, скорость `0.001`, кол-во `100`).
    - Звук: `minecraft:entity.firework_rocket.launch` (категория `HOSTILE`, громкость `10.0`, питч `1.5`).
  - Вылетает сущность-снаряд (например, `ItemDisplay` или невидимый `ArmorStand` / `Snowball` с привязанным отображением предмета), летящая по баллистической или прямой траектории в сторону цели.
  - **Модель летящей головы:** Предмет `WOODEN_HOE` (деревянная мотыга) с кастомными данными `densbomb` *(Для 1.21.7 поддержи как строковый компонент `CustomModelDataComponent` со строкой `"densbomb"`, так и числовой ID и `item_model` через конфиг)*.
  - **Цикличные эффекты летящей головы (каждый тик в полёте):**
    - Звук: `minecraft:block.lever.click` (категория `HOSTILE`, громкость `1.0`, питч `2.0`).
    - Партиклы (3 вида одновременно):
      1. `DUST_COLOR_TRANSITION`: из `[1.0, 0.0, 0.0]` в `[0.02, 0.0, 1.0]`, `scale: 1.0`, смещение `0.15, 0.15, 0.15`, скорость `0.25`, кол-во `3`.
      2. `DUST_COLOR_TRANSITION`: из `[0.02, 0.0, 1.0]` в `[0.6, 0.0, 1.0]`, `scale: 1.0`, смещение `0.15, 0.15, 0.15`, скорость `0.25`, кол-во `3`.
      3. `DUST_COLOR_TRANSITION`: из `[0.6, 0.0, 1.0]` в `[1.0, 0.0, 0.0]`, `scale: 1.0`, смещение `0.15, 0.15, 0.15`, скорость `0.25`, кол-во `3`.
  - **Взрыв головы (при попадании в блок или игрока):**
    - Наносит урон в настраиваемом радиусе (без разрушения блоков).
    - **Звук взрыва:** проигрывается **одновременно 3 раза** (циклом из 3 итераций за один тик для максимальной громкости) для игроков в радиусе 20 блоков:
      `quack_mob:densbomber.babah` (категория `MASTER`, громкость `10.0`, питч `1.0`).
    - **Партиклы взрыва:**
      1. `DUST_COLOR_TRANSITION`: из `[1.0, 0.0, 0.0]` в `[0.02, 0.0, 1.0]`, `scale: 4.0`, смещение `3, 3, 3`, скорость `0.25`, кол-во `35`.
      2. `DUST_COLOR_TRANSITION`: из `[0.02, 0.0, 1.0]` в `[0.6, 0.0, 1.0]`, `scale: 4.0`, смещение `3, 3, 3`, скорость `0.25`, кол-во `35`.
      3. `DUST_COLOR_TRANSITION`: из `[0.6, 0.0, 1.0]` в `[1.0, 0.0, 0.0]`, `scale: 4.0`, смещение `3, 3, 3`, скорость `0.25`, кол-во `35`.
      4. `EXPLOSION_EMITTER`: координаты `~ ~1 ~`, смещение `0, 0, 0`, скорость `1.0`, кол-во `1`.
      5. `TRIAL_SPAWNER_DETECTION`: смещение `3, 3, 3`, скорость `0`, кол-во `30`.
      6. `TRIAL_SPAWNER_DETECTION_OMINOUS`: смещение `3, 3, 3`, скорость `0`, кол-во `30`.

### Фаза 2: Без головы (Ближний бой на 5 секунд)
* Сразу после броска головы моб переходит в безголовое состояние на **5 секунд (100 тиков)** (настраивается).
* **Имя сущности меняется на:** `"Безголовый ДенсБомбер"` (`CustomNameVisible: false`), чтобы сменилась текстура.
* **Поведение:** Моб получает увеличенную скорость передвижения (настраивается через атрибут `GENERIC_MOVEMENT_SPEED`), перестает убегать, стремительно бежит вплотную к игроку и атакует в ближнем бою с настраиваемым уроном.
* **Возврат головы:** Через 5 секунд имя обратно меняется на `"ДенсБомбер"`, скорость возвращается к стандартной, и он снова начинает держаться на расстоянии и готовить следующий бросок головы.

---

## 4. Архитектура конфигурации (`config.yml`)
Реализуй универсальные парсеры секций конфига для `SoundSettings` и `ParticleSettings`, чтобы администратор мог менять любые партиклы, их цвета, размеры, смещения и звуки прямо в YAML без перекомпиляции плагина.

Используй следующую структуру `config.yml`:

```yaml
mobs:
  skelebass:
    entity-type: SKELETON
    custom-name: "СкелеБас"
    custom-name-visible: false
    max-health: 40.0
    movement-speed: 0.25
    keep-distance: 12.0
    attack-cooldown-ticks: 60
    projectile:
      max-distance: 50.0
      speed-per-tick: 1.5
      hitbox-radius: 0.8
      damage: 8.0
      knockback-strength: 1.8
      pierce-entities: false
    sounds:
      shoot:
        sound: "quack_mob:skelebass.attack"
        category: "HOSTILE"
        volume: 1.0
        pitch: 1.0
        repeat: 1
      projectile-loop:
        sound: "minecraft:block.note_block.bass"
        category: "HOSTILE"
        volume: 0.5
        pitch: 0.0
        max-distance: 20.0
        repeat: 1
    particles:
      projectile-trail:
        - type: "DUST_COLOR_TRANSITION"
          from-color: [255, 0, 0]
          to-color: [0, 110, 255]
          scale: 3.0
          offset: [0.1, 0.1, 0.1]
          extra: 0.0
          count: 4
        - type: "NOTE"
          offset: [0.35, 0.0, 0.0]
          extra: 1.0
          count: 0
        - type: "FLASH"
          color: [255, 255, 255]
          offset: [0.2, 0.2, 0.2]
          extra: 0.0
          count: 1

  dancebomber:
    entity-type: SKELETON
    custom-name-normal: "ДенсБомбер"
    custom-name-headless: "Безголовый ДенсБомбер"
    custom-name-visible: false
    max-health: 50.0
    normal-movement-speed: 0.25
    headless-movement-speed: 0.42
    keep-distance: 10.0
    headless-duration-ticks: 100
    throw-cooldown-ticks: 40
    melee-damage: 6.0
    melee-cooldown-ticks: 20
    bomb-projectile:
      material: "WOODEN_HOE"
      custom-model-data-string: "densbomb"
       speed: 1.1
      gravity: 0.03
      explosion-radius: 4.5
      explosion-damage: 12.0
      explosion-knockback: 1.2
    sounds:
      throw-head:
        sound: "minecraft:entity.firework_rocket.launch"
        category: "HOSTILE"
        volume: 10.0
        pitch: 1.5
        repeat: 1
      flying-head-loop:
        sound: "minecraft:block.lever.click"
        category: "HOSTILE"
        volume: 1.0
        pitch: 2.0
        repeat: 1
      explosion:
        sound: "quack_mob:densbomber.babah"
        category: "MASTER"
        volume: 10.0
        pitch: 1.0
        max-distance: 20.0
        repeat: 3
    particles:
      throw-head:
        - type: "CLOUD"
          offset: [0.2, 0.2, 0.2]
          extra: 0.001
          count: 100
      flying-head-trail:
        - type: "DUST_COLOR_TRANSITION"
          from-color: [255, 0, 0]
          to-color: [5, 0, 255]
          scale: 1.0
          offset: [0.15, 0.15, 0.15]
          extra: 0.25
          count: 3
        - type: "DUST_COLOR_TRANSITION"
          from-color: [5, 0, 255]
          to-color: [153, 0, 255]
          scale: 1.0
          offset: [0.15, 0.15, 0.15]
          extra: 0.25
          count: 3
        - type: "DUST_COLOR_TRANSITION"
          from-color: [153, 0, 255]
          to-color: [255, 0, 0]
          scale: 1.0
          offset: [0.15, 0.15, 0.15]
          extra: 0.25
          count: 3
      explosion:
        - type: "DUST_COLOR_TRANSITION"
          from-color: [255, 0, 0]
          to-color: [5, 0, 255]
          scale: 4.0
          offset: [3.0, 3.0, 3.0]
          extra: 0.25
          count: 35
        - type: "DUST_COLOR_TRANSITION"
          from-color: [5, 0, 255]
          to-color: [153, 0, 255]
          scale: 4.0
          offset: [3.0, 3.0, 3.0]
          extra: 0.25
          count: 35
        - type: "DUST_COLOR_TRANSITION"
          from-color: [153, 0, 255]
          to-color: [255, 0, 0]
          scale: 4.0
          offset: [3.0, 3.0, 3.0]
          extra: 0.25
          count: 35
        - type: "EXPLOSION_EMITTER"
          y-offset: 1.0
          offset: [0.0, 0.0, 0.0]
          extra: 1.0
          count: 1
        - type: "TRIAL_SPAWNER_DETECTION"
          offset: [3.0, 3.0, 3.0]
          extra: 0.0
          count: 30
        - type: "TRIAL_SPAWNER_DETECTION_OMINOUS"
          offset: [3.0, 3.0, 3.0]
          extra: 0.0
          count: 30