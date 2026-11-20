# Harry Mono Implementation Plan

> **For agentic workers:** Use executing-plans to implement task-by-task. Track progress here.

**Goal:** Добавить Гарри Моно с четырьмя взвешенными случайными атаками и ближним боем между ними.
**Architecture:** Чистая модель выбора/фаз/таймеров HarryCombat и отдельный Paper-контроллер HarryMonoController; минимальные точки интеграции в RockFrostPlugin.
**Tech Stack:** Java 21, Paper 1.21.7, JUnit 4, Maven.
**Spec:** ../specs/2026-11-17-harry-mono-design.md (одобрен пользователем).

## Global Constraints

- Adventure для нового текста; UUID вместо ссылок на сущности в коллекциях.
- Не создавать ресурс-пак, хранилище или физические блоки огня/масла.
- Значения времени 20/100/300/20/200 тиков для подготовки тарана, следа, лужи, подготовки вращения и вращения.
- Обязательны mvn test и mvn compile; без Git не создавать коммиты/worktree.

## Review Focus

- Смерть/выгрузка владельца очищает временные зоны и предметы.
- Высокая скорость не пропускает стены/игроков; чанки не загружаются для атак.
- Reload не меняет параметры уже начавшейся атаки.
- Некорректные веса/таймеры не создают бесконечные состояния.
- Оглушение снимается при телепортации/выходе/смерти.

### Task 1: Модель боя

**Files:** create HarryCombat.java, HarryCombatTest.java в src/main/java/com/example и src/test/java/com/example.
**Interfaces:** enum Attack; static Attack choose(double[] weights, double roll); phase/cooldown instance, tick(boolean hasTarget), begin(Attack,int), advance(int), finish(int); HitWindow для однократных и периодических попаданий.
- [ ] Написать тесты нулевых/неконечных весов, границ вероятностей, повторов, паузы интервала, переходов фаз, однократного урона и истечения таймеров.
- [ ] Запустить mvn test, увидеть отсутствие новых интерфейсов.
- [ ] Реализовать модель и проверить mvn test.

### Task 2: Контроллер и интеграция

**Files:** create HarryMonoController.java, HarrySettings.java; modify RockFrostPlugin.java; create HarrySettingsTest.java, HarryIntegrationTest.java.
**Interfaces:** HarryMonoController(plugin), register(UUID), remove(UUID), tick(Mob), close(); Listener событий защиты/оглушения. HarrySettings(ConfigurationSection) immutable snapshot via get accessors. Пакетные методы существующего плагина для движения/эффектов.
- [ ] Тестами задать значения по умолчанию, ограничения, снимок настроек и распознавание нового ID; выполнить RED.
- [ ] Реализовать состояния тарана/масла/вращения/прыжка, зоны/снаряд и оглушение, связать lifecycle/spawn/command.
- [ ] Проверить mvn test и mvn compile; добавить проверки геометрии и lifecycle при необходимости.

### Task 3: Конфигурация, документация, проверка

**Files:** config.yml, README.md, HarrySettingsTest.java.
- [ ] Добавить полные независимые настройки босса и примеры весов, README команду и описание.
- [ ] Проверить загрузку bundled config тестом; mvn test, mvn compile, mvn package.
- [ ] Независимый обзор кода; исправить существенные замечания с регрессионными тестами.
- [ ] Зафиксировать реальные результаты и ограничения проверки на сервере.

## Ledger

- Ruling: выполнять в текущей папке, без Git-worktree/коммитов и Git-скриптов навыка — проект не является Git-репозиторием; прогресс хранится в этом файле.
- Pre-flight: Task 2 использует Attack/таймеры Task 1; Task 3 использует ключи HarrySettings Task 2. Имена и единицы сверяются с конфигом тестом.
- Task 1: complete — RED отсутствующий HarryCombat, GREEN mvn test (24 теста).
- Task 2: реализация подключена, RED отсутствующие HarrySettings/HarryMonoController, GREEN mvn test (28 тестов). Снимок настроек кэшируется до reload, чтобы исключить обход конфига каждый тик.
