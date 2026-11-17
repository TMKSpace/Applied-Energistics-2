# Что и как тестить

Документ на два случая: **юнит-тесты AE2** (их 43 класса, они уже написаны) и **ручной прогон в игре**.

---

## 1. Юнит-тесты: почти бесплатно

AE2 тестируется **обычным JUnit 5**, не геймтестами. `src/test/java` уже перенесён в стенд
(55 файлов, 43 тестовых класса), сорсет `test` подключён, а `net.neoforged.testframework.junit`
шимится в `compat/java` — тесты гоняются без правок AE2. Покрытие прицельно серверное:

| группа | классы |
|---|---|
| сетка | `GridNode`, `GridConnection`, `GridServices`, `GridNodeTicking`, `ManagedGridNode`, `AbstractGridNode`, `EnergyService` |
| крафт | `CraftingSimulation`, `NetworkCraftingProviders`, `AECraftingPattern`, `AEProcessingPattern` |
| хранилище | `BasicInventory`, `InternalInventory`, `ConfigInventory`, `GenericStackInv`, `CondenserMEStorage`, `AppEngInternalInventoryTransaction` |
| ключи/стеки | `AEItemKey`, `AEKey`, `AEKeyType`, `GenericStack`, `KeyCounter` |
| прочее | `AECodecs`, `FuzzySearch`, `MathExpressionParser`, `P2PHelper`, `ServerCompassService`, `CubeBuilder`, `BlockOrientation` |

Это ровно то, что нужно, чтобы проверить порт **без запуска игры**: ME-сеть, ячейки, автокрафт
и NBT-кодеки. Пользовательский сценарий «3 подтверждено: серверная часть тестируется
геймтестами без рендера» закрывается дешевле, чем геймтестами.

### Как запускать

```
.\gradlew.bat test                     # 459 тестов: 458 passed, 1 падение
```

Один падающий — `InternalInventoryTest`: «Ephemeral test server did not start within 120
seconds». Это подъём настоящего `MinecraftServer` внутри теста; чинить не планировали,
остальные тесты от него не зависят.

Часть тестов шима (5 штук) помечена `SKIPPED`: им нужен Fabric-рантайм (`FluidVariant` вне
загрузчика не создать). Отдельная задача `shimTest` гоняет только шим и не зависит от
`compileJava`.

### Геймтесты (нужен настоящий мир)

```
.\gradlew.bat runGameTest "-PgtFilter=ae2:tiny_tnt*"
```

В 26.3 геймтесты — это датапак-реестры `TEST_ENVIRONMENT`/`TEST_INSTANCE`, а Fabric-раннер
поднимает обычный `GameTestServer` (порт не слушается). Шим постит `RegisterGameTestsEvent`
и эмулирует `ModList.getAllScanData` (без него `TestPlots` не находит ни одного плота).
`TEST_INSTANCE` = 81: 80 плотов AE2 + `minecraft:always_pass`. Фильтр — resource selector,
не регэксп (`*:*` — все, `ae2:tiny_tnt*` — группа). Полный набор пока падает на NPE
в `InscriberTestPlots` (`expectedBottomSlot` = null) — это пробел порта.

---

## 2. Ручной прогон в игре

Порядок — от того, что должно работать точно, к тому, что заведомо сырое.

### 2.1 Загрузка (дым)

- мод стартует, в логе нет `UnsupportedOperationException` из шима;
- главное меню, создание мира, вход в мир;
- `Platform.isServer()`/`isClient()` не врут на **выделенном** сервере (на интегрированном
  они могут врать — см. ограничение в `PROGRESS.md`).

### 2.2 ME-сеть (ядро)

1. `ME Controller` + `ME Drive` + `1k Storage Cell` → сеть собралась, каналы посчитались.
2. Положить предмет в ячейку, вынуть, перезайти в мир — содержимое на месте
   (проверяет `SavedData`/`SavedDataType` и наш маркер с `DataFixTypes`).
3. `Terminal` → поиск, сортировка, вывод стека.
4. `Crafting Terminal` → крафт по рецепту из сетки.
5. `Pattern Provider` + `Molecular Assembler` → автокрафт простого предмета.
6. `Interface` + `Storage Bus` → внешнее хранилище видно в терминале.
7. `P2P Tunnel` (item/fluid/energy) → туннель пропускает.
8. `Energy` — `Energy Acceptor` + любой генератор, сеть не голодает
   (проверяет мост `EnergyHandler`).

### 2.3 Жидкости (наш мост)

- `Fluid Storage Cell` или ведро → интерфейс: залить/вылить жидкость;
- **единицы**: 1 ведро должно стать 1000 mB в AE2, а не 81 000 — это место, где
  NeoForge (1000/ведро) и Fabric (81000/ведро) расходятся, мост делит на 81.

### 2.4 Пространственное хранилище

- `Spatial Pylon` + `Spatial IO Port` → сборка/разбор пространства;
- в логе не должно быть падений на `ChunkLoadingService` (`TicketController` наш, без
  entity-тикетов — если что-то не форсится, это оно).

### 2.5 Клиент/рендер — ожидаемо сырое

Проверять можно, но чудес не ждать:

| что | ожидание |
|---|---|
| кабели, фасады | должны рисоваться; **тинты граней могут пропасть** — в ванильном `BakedQuad` нет вершинных цветов, `CubeBuilder`-тинт теряется |
| `Paint Splotches`, `QNB`, `Drive` | модель собирается через `ModelDataHooks` — данные есть, но **без кэша**: на каждый чанк-слой лишние аллокации, возможны просадки FPS |
| `FacadeBuilder`, `MEChestRenderer` | заведомо не собираются/не работают до правок из `AE2-PATCHES.md` |
| JEI | интеграция использует `mezz.jei.api.neoforge` — на Fabric-сборке JEI её не увидит |

### 2.6 Чего НЕ тестить

- **Датаген.** Не запустится: модельный датаген AE2 построен на неофорджевской unbaked-подсистеме
  (`PropertyDispatch.modifyUnbaked`, `UnbakedMutator`, `CustomBlockStateModelBuilder`), которой
  в ванили/Fabric нет. Переписывание — отдельная задача.
- **Квады с тинтами** — см. таблицу выше.
- **WTHIT/EMI** — вырезаны из сборки (нет Fabric-версий под 26.3).

---

## 3. Порядок работ

1. Добить компиляцию (остаток — см. `AE2-PATCHES.md`).
2. Перенести `src/test/java` + шим тест-фреймворка → `gradlew test`. **Это первый реальный
   сигнал, что порт живой.**
3. Только потом — запуск клиента и ручной прогон по 2.1–2.3.
