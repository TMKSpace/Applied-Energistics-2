# Что и как тестить

Документ на два случая: **юнит-тесты AE2** (их 43 класса, они уже написаны) и **ручной прогон в игре**.

---

## 1. Юнит-тесты: почти бесплатно

AE2 тестируется **обычным JUnit 5**, не геймтестами. В `src/test/java` (живёт в клоне AE2,
в стенд ещё не перенесён) — 55 файлов, 43 тестовых класса. Покрытие прицельно серверное:

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

### Что мешает запустить их сейчас

Тесты помечены `@BootstrapMinecraft`, а это:

```java
@ExtendWith({ EphemeralTestServerProvider.class, BootstrapMinecraftExtension.class })
```

`EphemeralTestServerProvider` — из **NeoForge testframework** (`net.neoforged.testframework.junit`),
на Fabric его нет. Значит для запуска нужно:

1. Перенести `src/test/java` в стенд как отдельный `test` sourceset.
2. Добавить в `build.gradle`: `testImplementation junit-jupiter`, `assertj-core`,
   `testImplementation sourceSets.main.output`.
3. **Написать шим тест-фреймворка**: `net.neoforged.testframework.junit.EphemeralTestServerProvider`
   (JUnit-расширение, поднимающее настоящий `MinecraftServer` в процессе: `Bootstrap.bootStrap()`
   + `MinecraftServer.spin(...)`) и свой `BootstrapMinecraftExtension`. Это тот же приём, что и
   везде: подменяем NeoForge, а не правим AE2.
4. `gradlew test`.

Оценка: перенос — механический; шим тест-фреймворка — самая содержательная часть (нужно
аккуратно поднять сервер и корректно его гасить между тестами).

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
