# Шим nfcompat: журнал

`compat/java/net/neoforged/**` — вендоренный/адаптированный код NeoForge (LGPL-2.1, заголовки сохранены)
+ `compat/java/nfcompat/bridge/**` — наши мосты. Цель: нетронутые исходники AE2 26.3
+ этот каталог + `src/main/resources/ae2.accesswidener` = сборка.

Проект — `D:\Code\java\ae2-fabric-probe`. Loom 1.19.4, MC 26.3, loader 0.20.1,
Fabric API 0.166.2+26.3, Java 25.

## Цифры

| шаг | ошибок всего | из них в шиме | файлов |
|---|---|---|---|
| голые сурсы AE2 | 5164 | — | 758 |
| + `transfer/**` (обрезанный) | 2201 | 50 | 359 |
| + `capabilities`, `bus`, `fluids` | 1843 | 34 | 315 |
| + скелеты событий (45 пустых классов) | 1756 | 34 | 313 |
| + **реальный event-слой из NeoForge (104 класса)** | 1764 | 145 | 336 |
| + `ICancellableEvent`, `LogicalSide`, утилиты, фиксы | 1756 | 137 | 329 |
| + настоящий `EventHooks` (свой, 3 хука), модельный слой, стабы пакетов | 1531 | 0 | 290 |
| + **сторонние API (JEI, Jade, REI; вырезаны WTHIT и EMI)** | 855 | 0 | 255 |
| + **FML-слой, `Tags`, `DeferredRegister`** | 695 | 0 | 234 |
| + **свой config-слой, `NfCompatInit` (Platform.isServer), AW-заход** | 643 | 0 | 221 |
| + шим `model.data` (вендорен из NF) | 526 | 0 | 188 |
| + **препроцессор + патч-сет маркерами** (`ItemStackTemplate`, `OitPipelineSet`, `EntityType`, `BlockEntityType`, `SavedDataType`) | 378 | 0 | 144 |
| + датаген двумя ветками, квады, тикеты чанков, добор пакетов | **267** | **0** | **119** |

**Шим собирается и проверен тестами.** Все оставшиеся 267 ошибок — в нетронутых исходниках AE2,
кроме мест, закрытых помеченными правками (см. `AE2-PATCHES.md`).

`shimTest` — **85 тестов, 0 упало, 5 пропущено** (пропуски требуют Fabric-рантайма:
`FluidVariant` вне загрузчика не создать). Задача не зависит от `compileJava`, поэтому
гоняется, пока основная сборка красная.

## Сторонние моды: что взяли и почему

`maven.blamejared.com` (JEI, Jade) и `maven.shedaniel.me` (REI) с этой машины не отвечают
(таймаут TLS), поэтому все API тянутся с **Modrinth maven** (`https://api.modrinth.com/maven`).
MC 26.x не обфусцирован, поэтому production-джар мода годится в `compileClasspath` напрямую —
проверено: в JEI 0 ссылок на intermediary `class_XXXX`, все ссылки на MC уже именованные.

| мод | версия | modrinth id | статус |
|---|---|---|---|
| Forge Config API Port | — | — | **отказались** — см. ниже |
| JEI | 31.17.0.113 fabric | `zX75GFgt` | `compileOnly` |
| Jade | 26.3.5+fabric | `71CTWqdE` | `compileOnly` |
| REI | 26.3.824+fabric | `rKsAE6bX` | `compileOnly` |
| EMI | **нет сборок под 26.3** | — | вырезан |
| WTHIT | **нет Fabric** | — | вырезан |

Вырезано гейтом сорсета (`sourceSets.main.java.exclude`):
- `appeng/integration/modules/wthit/**` — снаружи ссылок нет, только javadoc;
- `appeng/client/api/integrations/emi/**` — 2 файла конвертеров, снаружи ссылок нет.

Влияние: минус ~1000 ошибок JEI (было 960) и Jade (160), одной зависимостью каждое.

## Event-слой

- **104 класса событий** скопированы из NeoForge: 42 импортирует AE2 напрямую, остальные
  подтянуты замыканием по `import`/`extends`. Клиентские события в NeoForge 26.3 лежат
  в **отдельном сорсете** `src/client/java/...`.
- **Своя шина**: `IEventBus`, `SimpleEventBus`, `EventListener`, `SubscribeEvent`.
  `EventListener` — `Serializable`-наследник `Consumer`, поэтому тип события вытаскивается
  из `SerializedLambda` (`writeReplace` → `getImplMethodSignature` → первый аргумент).
  Работает и для лямбд, и для ссылок на методы.

## EventHooks — свой, а не вендоренный

В NeoForge это десятки хуков из **патченного ванильного кода**. Ваниль мы не патчим, поэтому
вендоренная версия была мёртвым грузом (64 КБ, 15 ошибок). AE2 зовёт ровно три метода:
`firePlayerCraftingEvent`, `onBlockPlace`, `onExplosionDetonate` — они и написаны, честно постят события.

## FML-слой (`net.neoforged.fml.*`, `api.distmarker`)

Всё написано своё, поверх FabricLoader — не заглушки:

| класс | как сделано |
|---|---|
| `api.distmarker.Dist` | enum CLIENT/DEDICATED_SERVER |
| `loading.FMLEnvironment` | `dist` и `isProduction()` из `FabricLoader` |
| `loading.FMLLoader` | `getCurrent()/getCurrentOrNull()`, `getDist()`, `isProduction()`, `getLoadingModList()` |
| `loading.moddiscovery.ModInfo` | id/имя/версия из `fabric.mod.json` |
| `fml.ModList` | `isLoaded` → `FabricLoader.isModLoaded`, контейнеры из метаданных |
| `fml.ModContainer` | метаданные + шина + `registerConfig` |
| `fml.ModLoader` | `postEvent` на общую шину, упрощённый `dispatchParallelEvent` |
| `fml.ModWorkManager`, `fml.DeferredWorkQueue` | исполнители и очередь отложенной работы |
| `fml.InterModComms` | сообщения складываются в список, никуда не уходят |
| `fml.common.Mod`, `EventBusSubscriber` | аннотации-заглушки (на Fabric точки входа в json) |
| `fml.event.lifecycle.*` | `ParallelDispatchEvent`, `FMLCommonSetupEvent`, `FMLClientSetupEvent`, `FMLDedicatedServerSetupEvent`, `InterModEnqueueEvent` |
| `fml.event.config.ModConfigEvent` | `Loading`/`Reloading`/`Unloading` |
| `fml.util.thread.SidedThreadGroups` | `SERVER` ловится хуком на старте сервера (см. ниже) |
| `neoforge.common.util.FakePlayerFactory` | создаёт `ServerPlayer` по `GameProfile` (TODO: кэш) |
| `nfcompat.NfCompatInit` | точка входа шима: `fabric.mod.json` в `compat/resources` |

### Platform.isServer / isClient — сделано, но с оговоркой

AE2 сравнивает `Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER`, а группу
у живого потока не подменить. Значит её надо поймать: `NfCompatInit` подписывается на
Fabric `ServerLifecycleEvents.SERVER_STARTING` и кладёт текущую группу в `SidedThreadGroups.SERVER`
(плюс хранит сервер для `ServerLifecycleHooks`).

- **Выделенный сервер:** точно. Там главный поток и есть серверный.
- **Интегрированный сервер:** клиентский и серверный потоки могут оказаться в одной `ThreadGroup`
  (обычно это группа `main`), и тогда сравнение не различает стороны — `isServer()` будет
  говорить «сервер» и на клиенте. Лечится только миксином/правкой на стороне AE2; пока это
  записано как известное ограничение, а не спрятано.

Механика добавления `fabric.mod.json`: он лежит в `compat/resources`, подключён отдельным
ресурсным `srcDir`, чтобы шов оставался извлекаемым (в исходниках AE2 ничего не появляется).

## `Tags`

NeoForge'вский `Tags.java` — 154 КБ констант. AE2 использует 33. Они и написаны, с теми же
путями и в том же `c`-namespace, что и в NeoForge, поэтому данные тегов совпадают.

## `DeferredRegister`

Написано семейство: `DeferredRegister` (+ вложенные `Items`, `Blocks`, `DataComponents`,
`Entities`), `DeferredHolder` (наследует `Holder.Reference`), `DeferredItem`, `DeferredBlock`,
`NeoForgeRegistries`, `AttachmentType`, `GameData`, `javax.annotation.Nullable`.

Регистрация **нетерпеливая**: `register(...)` сразу создаёт объект и кладёт его в настоящий
`Registry` через `Registry.registerForHolder`. Порядок внутри одного `DeferredRegister`
совпадает с NF. Плата: межклассовые зависимости должны быть разрешимы на момент статической
инициализации (AE2 под это подходит: блоки регистрируются до предметов).

Не сделано: `makeRegistry`/`RegisterEvent`/`NewRegistryEvent`/`RegistryBuilder` — их зовёт
`AppEngBase` для своего реестра `AEKeyType`. Честно это требует настоящего `MappedRegistry`,
вставленного в `BuiltInRegistries.REGISTRY` до заморозки.

## Миксины

Инфраструктура: `compat/resources/nfcompat.mixins.json`, `"mixins"` в `fabric.mod.json`,
классы в `compat/java/nfcompat/mixin/`. Препроцессор для них **не нужен** — `fabric.mod.json`
читает только Fabric, а `compat/java` в NeoForge-сборку не входит вовсе.

Важно: миксин **не двигает компилятор** (`@Mixin` виден только рантайму). Поэтому порядок такой —
компиляцию добиваем маркерами, а миксинами возвращаем поведение того, что сейчас заглушено:

| заглушка | что даст миксин |
|---|---|
| `ModelDataHooks.get` ходит в `getBlockEntity` каждый раз | кэш как в `ModelDataManager` + инвалидация от `requestModelDataUpdate` |
| `TicketController` без entity-тикетов | настоящие тикеты чанков |
| `IRegistryExtension#addCallback` бросает | коллбеки реестров |
| `TagAppender#add(TagEntry)` и пр. отсутствуют | добавить методами интерфейса |

Первый миксин уже есть: `BlockEntityMixin` делает `BlockEntity` нашим `ModelDataProvider`.

## Датаген: две ветки

Неофорджевскую unbaked-подсистему (`PropertyDispatch#modifyUnbaked`, `UnbakedMutator`,
`CustomBlockStateModelBuilder`) не шимим и не копируем. Вместо этого в одном файле две ветки:
`//#if FABRIC` — на ванильном `net.minecraft.data` + `fabric-data-generation-api-v1`,
`//#else` — исходный неофорджевский код. Тот же препроцессор, что и у патч-сета.

## Препроцессор в исходниках AE2

Java препроцессора не имеет, но NeoForge местами **меняет сигнатуры ванили**, и такое из
`compat/java` не лечится. Поэтому в `build.gradle` есть шаг `preprocessSources`, который
разворачивает в одном файле обе ветки:

```java
//#if FABRIC
        return new ItemStackTemplate(item.asItem(), stackSize);
//#else
        return new ItemStackTemplate(item, stackSize);
//#endif
```

Маркеры — комментарии, поэтому для NeoForge-сборки файл остаётся валидным. Исключённые строки
заменяются пустыми (нумерация не съезжает). Компилируется из `build/nfcompatSrc`.
Подробности и текущий список мест — в `AE2-PATCHES.md`.

Грабли: в `sourceSets` нужен **`srcDirs = [...]`**, а не `srcDir ...` — иначе остаётся
дефолтный `src/main/java` и дерево компилируется дважды (было 1671 «ошибка» из ниоткуда).

## Найденные тестами баги шима

### `SimpleEventBus.eventTypeOf()` — исправлен

Тип события брался из `SerializedLambda.getImplMethodSignature()`. javac дописывает
захваченные переменные (и `this`) **перед** параметром SAM-метода, поэтому первым
аргументом оказывалось захваченное значение: слушатель регистрировался на
`java.util.List` или на класс владельца и **событий не получал вообще, без ошибок в логе**.
В игре это «часть AE2 молча не реагирует».

Исправление: `getInstantiatedMethodType()` (там всегда `(L<event>;)V`) + запасной путь
для нестандартных компиляторов. Заодно:

- `register(Object)` обходит всю иерархию классов (в NeoForge `@SubscribeEvent` в родителе
  подхватывается), перекрытые методы не дублируются;
- анонимные классы-слушатели больше не теряются: тип берётся из generic-параметра
  `EventListener<T>`.

### Что ещё вскрыл отдельный сорсет

`compileJava` не доходил до трёх файлов шима (падал раньше на AE2), и они не компилировались:

1. `SortedReloadListenerEvent` — литерал `\n` из автопатча залип в комментарий, и присваивание
   `lastVanilla` оказалось **внутри** комментария;
2. `CreateFluidSourceEvent` — то же с `vanillaResult`;
3. `DeferredRegister` — поле инициализировалось от поля, которое присваивается в конструкторе.

### Известные расхождения (не исправлены, зафиксированы тестами)

- `StorageFluidHandler.isValid()` возвращает `true` для любого непустого ресурса и игнорирует
  индекс (в NeoForge спрашивает слот);
- `insert`/`extract` на отрицательное количество отдают `0`, а не бросают
  `IllegalArgumentException`; на `null`-ресурс — `NPE`;
- для не-slotted `Storage` индекс игнорируется (`view(99)` отдаёт нулевой слот);
- `ICancellableEvent` в шиме не реализует ничего, поэтому `instanceof ICancellableEvent`
  в коде AE2 всегда `false` (отмена живёт флагом в `Event`).

## Accesswidener

`accessWidener v2 official` + 48 записей:
- 31 AT AE2→AW;
- 6 полей для шима (`BlockEntityType.validBlocks`, `EquipmentSlot.countLimit`, `BucketItem.content`,
  `ItemStackLinkedSet.TYPE_AND_TAG`, `Options.keyMappings` (mutable), `ServerPlayer$RespawnConfig.getDimensionOrDefault`);
- **11 под дрейф ванильного API** (то, что в 26.3 стало final/private):
  - `mutable field` — `AbstractContainerScreen.imageWidth/imageHeight`, `Slot.x/y`;
  - `accessible field` — `Screen.renderables`, `RecipeProvider.items`;
  - `accessible method` — `SimpleParticleType.<init>(Z)`, `ModelTemplates.create/createItem`.

Грабли: namespace `official` (не `named`); classtweaker требует
`accessible method <класс> <имя> <дескриптор>` — тремя токенами; **конструктор пишется как
`accessible method <класс> <init> (дескриптор)`, а не `accessible constructor`** — иначе
`ClassTweakerFormatException: Unsupported type: 'constructor'`.

## Что пришлось адаптировать (ванильные патчи NeoForge)

| патч NeoForge | как закрыли |
|---|---|
| `Options.keyMappings` не-final | AW `mutable field` |
| `RespawnConfig.getDimensionOrDefault` доступный | AW `accessible method` |
| `Container.setItem(int, ItemStack, boolean)` | 2-аргументный ванильный |
| `Container.onTransfer(idx, amount, tx)` | убрали вызов |
| `Level.getCapability(...)` | `BlockCapability.getCapability(level, pos, state, be, side)` |
| `ItemStack.canEquip(EquipmentSlot, Player)` | `super.isValid` + TODO на `Equippable` |
| `CommonHooks.onPlayerTossEvent` | `LivingEntity.drop(stack, around, prediction)` |
| `FluidState.canConvertToSource(ServerLevel, Pos)` | `FluidState.isSource()` |
| `FluidType` (звук/испарение/постановка) | методы добавлены в нашу `FluidType` с ванильными дефолтами |
| `Mob.setSpawnCancelled` | свой флаг в `FinalizeSpawnEvent` |
| `ParticleResources.register` + `SpriteParticleRegistration` | `RegisterParticleProvidersEvent` копит провайдеры у себя |
| `ModelBakery.BakingResult.standaloneModels()` | не пересобираем результат |
| `ReloadableResourceManager.getListeners()` | пустой список + защита от пустоты |
| `LevelEvent.PotentialSpawns` (`WeightedList.Builder`) | обычный `List<Weighted<SpawnerData>>` |
| `ChunkAccess.getLevel()` | `instanceof LevelChunk` |
| `Identifier.compareNamespaced` | `toString().compareTo` |
| `ICondition`, `VanillaServerListeners`, `ReloadListenerSort`, `ClientHooks` | свои минимальные версии / удалено |

`IMenuTypeExtension.create` намеренно **бросает** `UnsupportedOperationException`:
молчаливый null уронил бы регистрацию меню позже и непонятнее. Портировать на
Fabric `ExtendedScreenHandlerType` — этап сети/меню.

## Config-слой: свой, порт не используем

Forge Config API Port **не подошёл**: его `net.neoforged.fml.config` — не NeoForge 26.3, а его
собственная адаптация. Доказательство — сам NeoForge 26.3 в `ModConfigSpec` ссылается на
`ModConfig.Type.SYNCED`, а AE2 пишет `ModConfig.Type.LOCAL`; в порте (26.3.1) нет ни того, ни другого
(там `COMMON/CLIENT/SERVER/STARTUP`). Два класса не подружить: enum расширить нельзя, а свой
`ModConfig` рядом с портом — дубль класса.

Поэтому:

| класс | откуда |
|---|---|
| `neoforge.common.ModConfigSpec` | вендорен из NeoForge (64 КБ, LGPL, заголовки на месте) |
| `fml.config.IConfigSpec` | свой, по тому, что реально вызывает `ModConfigSpec` |
| `fml.config.ModConfig` + `Type` | свой, enum `STARTUP/COMMON/CLIENT/SERVER/SYNCED/LOCAL` |
| `fml.Logging` | свой (`CORE` — маркер log4j) |
| движок | `com.electronwill.night-config:core:3.6.7` (тот же, на чём NF) |

`ModContainer#registerConfig` создаёт `ModConfig`, гоняет `spec.validateSpec(config)`
(ловит значения с недопустимым restart-типом и валидаторы, отвергающие свой же дефолт)
и складывает в список. **TODO:** чтение/запись TOML через nightconfig +
`spec.acceptConfig(...)` + пост `ModConfigEvent.Loading` — пока значения остаются дефолтными.

Итог: 2 ошибки `Type.LOCAL` ушли, `AEConfig` собирается полностью, внешних модов в шиме не осталось.

## Остаток: 643 ошибки в AE2

| зона | ошибок |
|---|---|
| `client/**` (модели, gui, рендер) | ~610 по подпакетам |
| `datagen/**` | ~148 (было 180 — AW снял `RecipeProvider.items`) |
| `net.neoforged.neoforge.model.data` (`ModelData`, `ModelProperty`) | 126 |
| сеть (`client.network`, `network.codec`, `network.connection`) | 80 |

Топ сообщений:

| сообщение | шт | что делать |
|---|---|---|
| `cannot find symbol` | 776 | почти всё — следствие отсутствующих пакетов ниже |
| `does not override ... supertype` | 138 | уйдёт вместе с интерфейсами |
| `neoforge.model.data` | 98 | писать `ModelData`/`ModelProperty` + `getModelData(pos)` через миксин |
| `neoforge.client.network` | 42 | сетевой слой на Fabric `PayloadTypeRegistry` |
| `ModelData` (как тип) | 28 | см. выше |
| `neoforge.client.model.quad` | 26 | квады: своя обёртка над Fabric-овскими |
| `neoforge.network.codec` | 24 | `NeoForgeStreamCodecs` (`enumCodec` и пр.) |
| `neoforge.common.world.chunk` | 10 | стаб `ChunkTicketManager`-подобного |
| `ItemStackTemplate cannot be converted to ItemStack` | 8 | нужен `ItemStackTemplate(Holder,int)` — патч NF, только миксин |
| `Identifier cannot be converted to String` | 8 | дрейф `Identifier.parse` |
| `SavedDataType<>` inference | 6 | дрейф ванильного API |
| `bad type in switch expression` | 6 | дрейф ванильного API |
| `closeContainer() protected in Player` | 2 | AW `accessible method` |

## Что НЕ лечится из `compat/java` (всегда нужен миксин или AW)

1. `ItemStackTemplate(Holder<Item>)`, `(Holder,int)`, `(Holder,DataComponentPatch)` — конструкторы-патчи NF.
2. `BlockEntityType` convenience-конструктор с varargs `Block...`.
3. `EntityType.Builder.setTrackingRange/setUpdateInterval/setShouldReceiveVelocityUpdates`.
4. `AttachmentType`-холдеры: `Entity/BlockEntity/Level.getData/setData` (NeoForge `IAttachmentHolder`).
5. `BlockAndTintGetter.getModelData(BlockPos)` — под `model.data`.
6. `ModConfig.Type.LOCAL`/`SYNCED` — см. выше.

## Рантайм: что уже работает

- **Сеть AE2** — payload'ы через `PayloadRegistrar` (`ClientPlayNetworking`/`ServerPlayNetworking`).
  `Exporting 1 grids`, контроллер создаёт grid, терминал получает `ae2:me_inventory_update`.
- **Тултипы и ёмкость ячейки** — `nfcompat.ServerThread` + маркеры в `appeng/util/Platform.java`
  (`isClient`/`isServer`/`assertServerThread`): AE2 сравнивает ThreadGroup, а в одиночной игре
  на Fabric группы клиента и встроенного сервера совпадают.
- **blockstate-модели** — 0 ошибок (было 30): `BlockStateModelHooks` + `NeoForgeExtraCodecs.dispatchMapOrElse`
  + `@Redirect` в `SimpleModelSelectorsMixin`/`MultipartSelectorMixin`. Поле-интерфейс
  (`BlockStateModel.Unbaked.CODEC`) нельзя `@Shadow @Mutable` — JVM кидает `ClassFormatError`,
  поэтому подменяются только чтения.
- **item-модели** — 0 ошибок (было 96): AW на 5 полей `ID_MAPPER` + постинг `RegisterItemModelsEvent`,
  `ItemTintSources`, `RangeSelectItemModelPropertyEvent`.
- **Клиентские события** — `nfcompat/ClientEventBridge` + 8 миксинов (`Options`, `EntityRenderers`,
  `BlockEntityRenderers`, `LayerDefinitions`, `KeyboardHandler`, `MouseHandler`, `BlockColors`,
  `ParticleResources`): тики, вход/выход, команды, клавиши, BER/слои, ввод, тинты, частицы, пайплайны.
- **Серверные события** — `nfcompat/LifecycleBridge`: тики сервера/измерений,
  `ServerStarting/Started/Stopping/Stopped`, выгрузка измерения и чанков, `RegisterCommandsEvent`.
- **reload-слушатели** — `AddClientReloadListenersEvent` → `ResourceManagerHelper`
  (`ShimReloadListener`/`ReloadListeners`).
- **Экраны меню** — AW на `MenuScreens.register` + постинг `RegisterMenuScreensEvent`.
- **Model data в рендер-регионе** — `RenderChunkRegion.getBlockEntity()` при сборке меша чанка
  всегда отдаёт `null`, поэтому `nfcompat/model/client/ClientModelDataLookup` добирает block entity
  из клиентского мира по позиции.
- **GuideME не поднимаем** — его статическая инициализация требует `ArgumentTypeInfos.registerByClass`
  (патч NeoForge), а Mixin не может добавить non-private static метод. Кнопка «?» погашена `GuidesCommonMixin`.
- **Кабель и все динамические блок-модели** — рисуются. Причина, почему раньше не рисовались:
  ванильный `BlockStateModel.collectParts(RandomSource, List)` не получает ни мира, ни позиции, а
  Indigo (Fabric API) зовёт именно его через `FabricBlockStateModel.emitQuads`. Мир/позиция при этом
  есть в кадре `ModelBlockRenderer.tesselateBlock` и в параметрах `emitQuads`, поэтому поставлены два
  редиректа: `nfcompat.mixin.client.ModelBlockRendererMixin` и `FabricBlockStateModelMixin` — они
  отдают контекст позиционному варианту. В логе это выглядело как вызовы `collectParts` для воздуха
  в `BlockPos.ZERO`.
- **Рецепты на сервере** — `nfcompat/recipes/RecipeMaps` берёт карту из `RecipeManager` через
  `@Accessor("recipes")` (`nfcompat.mixin.RecipeManagerAccessor`). Через реестр нельзя: ключ
  `minecraft:recipe` есть у датапаков, но не в `server.registryAccess()` — падало
  `Missing registry: minecraft:recipe` в тике сервера.
- **Рецепты на клиенте** — ваниль 26.3 не отправляет клиенту рецепты вообще
  (`ClientLevel.recipeAccess()` = `ClientRecipeContainer` без списка). Сервер шлёт их пакетом
  `nfcompat:recipes` (`nfcompat/network/RecipeSync`, порции по 100) при входе и после перезагрузки
  датапаков; клиент собирает `RecipeMap` (`RecipeMaps.createFrom`, приватный конструктор через
  рефлексию) и постит `RecipesReceivedEvent`. Без этого не работали подстановка результата,
  кодирование крафт-шаблонов и JEI-подстановка.
- **Внешние контейнеры (шины, Pattern Provider, интерфейс, P2P)** — `Capabilities.Item.BLOCK` /
  `Fluid.BLOCK` в шиме больше не заканчиваются на NeoForge-провайдерах: после них
  `nfcompat/bridge/FabricCapabilities` спрашивает `ItemStorage.SIDED` / `FluidStorage.SIDED` и
  оборачивает их в `ResourceHandler` (`StorageItemHandler`, для жидкостей такой был).
- **Печь и стороны** — не баг: ванильная печь принимает по сторонам только топливо, вход у неё
  сверху. Пуш руды провайдером сбоку не пройдёт ни на Fabric, ни на NeoForge.
- **Кастомный GUI (`Blitter`)** — в FABRIC-ветке `Blitter.blit` звалась 11-аргументная перегрузка
  `GuiGraphicsExtractor.blit`, а она подставляет размер источника = размеру назначения. Для текстур
  256x256 при dest==src это случайно совпадает, поэтому «нормально» выглядели только табы и
  тулбар-иконки. На 64x64 (чекбокс) и 128x128 (поле ввода) получался зум, а в спрайтах атласа
  (ползунок, маркер pinned-строки автокрафта) в кроп попадали соседние спрайты. Перешли на
  13-аргументную перегрузку с явными `srcRect` и `referenceWidth/referenceHeight` — чинит все
  47 вызовов сразу.
- **JEI** — плагин AE2 не загружался вообще: Fabric-JEI берёт плагины только из entrypoint
  `jei_mod_plugin` в `fabric.mod.json` (никакого сканирования `@JeiPlugin` нет — проверено по
  `mezz.jei.fabric.startup.FabricPluginFinder`). Добавили entrypoint. Плюс жидкостной ингредиент
  переведён на `FabricTypes.FLUID_STACK` + `JeiFluidIngredient` с пересчётом миллибакетов через
  `nfcompat.bridge.FluidUnits` (иначе JEI не знает наш тип и R/U по жидкости молчит), а рендер
  жидкости в Transform-категории на Fabric отдан самому JEI (`setFluidRenderer`).

## Долг: `AEKeyTypes` запекался пустым (пустая сетка терминала)

Симптом: сетка ME-терминала пустая, но связь и сеть живые — `ae2:me_inventory_update` доходит,
`Repo` знает записи с сервера.

Причина: `AEKeyTypesInternal.setRegistry()` в NeoForge вешает `BakeCallback`, который срабатывает
**после всех регистраций**. Наш шим `nfcompat/registry/RegistryCallbacks` вызывал колбэк немедленно —
на `NewRegistryEvent`, когда реестр `ae2:keytypes` ещё пуст (сами типы регистрируются позже, в
`RegisterEvent(AEKeyType.REGISTRY_KEY)`). В результате `AEKeyTypes.getAll()` навсегда оставался пустым
набором: `KeyTypeSelection` у хоста терминала получался пустым, `Repo.addEntriesToView` отсекал все
записи по `typeFilter`, клиент видел `visible=0 total=4` — и ни одна запись не попадала в слот.

Фикс: `RegistryCallbacks` копит колбэки, а `NfCompat.runRegistrationPhases()` вызывает
`RegistryCallbacks.bakeAll()` сразу после блока `RegisterEvent`.

Чем ловили (приём на будущее): одна строка в лог на тип payload'а при первом получении (в
`PayloadRegistrar`), плюс временный миксин на `MEStorageScreen.extractSlot`, печатавший
`entry/visible/total`. По этим числам сразу видно, где рвётся: сеть, фильтр представления или слот.

## Next

1. **Terminal Options**: обрезка переключателей/иконок должна уйти вместе с фиксом `Blitter`
   (см. «Рантайм») — осталось подтвердить глазами.
2. **JEI**: плагин теперь грузится (entrypoint `jei_mod_plugin`), жидкостной ингредиент переведён
   на Fabric-API. Проверить в игре: в логе `Registering ... : ae2:core`, поиск жидкости, R/U по
   жидкости в AE-терминалах, ghost-drag жидкости в фильтр.
3. **Гэп-список**: `Player#getDestroySpeed` (скайстоун ×50), `LivingEntity#onClimbable` (лазание
   по кабелю), `OnDatapackSyncEvent`, `FabricExternalStorageStrategy` (пустой файл — теперь можно
   и удалить, мост живёт в `FabricCapabilities`), `StandaloneModels` (null), чтение TOML в шиме,
   entrypoints REI/Jade.
4. **Наковальня**: `AnvilMenuMixin` AE2 целится в `createResultInternal`, которого в ванили нет
   (метод добавляет NeoForge) — миксин исключён, intrinsic-чары наковальни остаются непокрытыми.
5. **GuideME** — нужен патч джара на сборке (отложено).
6. **Проба моста** — в шиме лежит временная команда `/nfcompat cap|parts|pp|provider`
   (`nfcompat/debug/CapabilityProbeCommand`), полезная для разбора «шина не видит контейнер».
   Удалить, когда надобность отпадёт.

## Инструменты стенда

Скрипты лежат в корне проекта, **логи прогонов — в `.opencode/`** (каталог в `.gitignore`).

- **MCPFabric** — Fabric-мод, встраивающий HTTP-мост в игру (`127.0.0.1:25599`, токен в
  `run/config/mcpfabric.config.json`). Позволяет агенту самому ставить блоки, читать инвентарь,
  гонять команды и снимать скриншоты. Джарник лежит в `libs/` (в `.gitignore`) и подключён как
  `localRuntime` в `build.gradle`; MCP-сервер собран в
  `C:\Users\Kot\.config\opencode\tools\mcpfabric\mcp-server` и прописан в глобальном `opencode.jsonc`.
  Через него же удобно ловить числа: `/nfcompat cap`, `/nfcompat provider`.

## Инструменты стенда

Скрипты лежат в корне проекта, **логи прогонов — в `.opencode/`** (каталог в `.gitignore`).

- `build.ps1` — собрать и сразу разобрать: `powershell -File build.ps1`
  (сам нумерует `build-probeN.log`, кладёт в `.opencode`, гоняет `analyze.ps1` + `compat-summary.ps1`).
  Есть `-Task` и `-NoAnalyze`.
- `analyze.ps1` — уникальные ошибки и топ сообщений;
- `compat-summary.ps1` — ошибки в шиме по файлам;
- `compat-detail.ps1` — детали по ошибкам шима;
- `ae2-detail.ps1` — детали по ошибкам AE2, с `-Match <регэксп>`;
- `missing-symbols.ps1`, `compat-errors.ps1` — разборы;
- `gen-aw.ps1`, `gen-events.ps1`, `gen-fml-shim.py`, `gen-config-shim.py` — генераторы;
- `copy-events.py`, `patch-compat-*.py`, `move-logs.py`, `prune-compat.ps1` — разовые утилиты;
- `refresh-markers.py` — дописывает в `markers.tsv` новые маркеры `//#if FABRIC` (существующие
  строки с заметками не трогает; пути в формате Windows, как в самом файле).

Все скрипты сами ищут лог в `.opencode/`, затем в корне.

Грабли среды:
- PowerShell 5.1 читает UTF-8 без BOM как ANSI — **`.ps1` только ASCII (или с BOM)**; `.java` — UTF-8 без BOM.
- `Tee-Object` в PS 5.1 **режет строки по ширине консоли** — логи портятся. В `build.ps1` пишем через `Set-Content`.
- `.NET` `File.ReadAllText('relative')` не видит PowerShell-овский `cd` — только абсолютные пути.

## Обновление с апстрима (мержабельность)

Все правки в `src/` — блоки `//#if FABRIC`, то есть **комментарии**. Поэтому апстрим-код
остаётся валидным в своей ветке, а git умеет переносить наши вставки сам.

Порядок:

1. `.\sync-upstream.ps1` — тянет `AppliedEnergistics/Applied-Energistics-2:main` (remote `upstream`),
   делает `git rebase`, печатает инвентарь маркеров и запускает сборку;
2. если rebase встал на конфликтах — скрипт перечисляет файлы; в файле оставить **обе** стороны
   (нашу `//#if FABRIC` и код апстрима), затем `git add` + `git rebase --continue`;
3. ошибки сборки после обновления — это и есть список «что изменилось в ваниле/NF»: чинится либо
   шимом, либо новым маркером.

`markers.tsv` — инвентарь: файл, строка, пояснение, FABRIC-код. 216 мест в 121 файле.
Он же служит ответом на вопрос «а что вообще NeoForge патчит».
