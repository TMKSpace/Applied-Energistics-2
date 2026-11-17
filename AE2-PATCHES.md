# Патч-сет для исходников AE2

## Зачем он есть

Идея стенда: **исходники AE2 не трогаем**, весь NeoForge подменяем шимом в `compat/java`.
Это работает, пока NeoForge не *меняет сигнатуры ванильных методов и конструкторов*.
Такое из `compat/java` не лечится вообще: accesswidener не умеет добавлять члены и менять
сигнатуры, а миксин виден только рантайму, не компилятору.

## Как оформлено: препроцессор, а не «правка на выброс»

В `build.gradle` есть шаг `preprocessSources`: он разворачивает в одном файле оба варианта.

```java
//#if FABRIC
        return new ItemStackTemplate(item.asItem(), stackSize);
//#else
        return new ItemStackTemplate(item, stackSize);
//#endif
```

- Маркеры — обычные комментарии, поэтому **для NeoForge-сборки файл остаётся валидным**:
  ничего «порченного» в репозитории нет.
- Исключённые строки заменяются **пустыми**, а не выкидываются — нумерация строк не съезжает,
  разбор логов продолжает работать.
- Поддержаны `FABRIC` / `NEOFORGE` / `!FABRIC` / `!NEOFORGE` и вложенность.
- Исходники компилируются из `build/nfcompatSrc`, а не напрямую — см. `sourceSets`.

То есть это не «мы испортили апстрим», а мультилоадерная разметка в том же духе, что
`//#if MC >= 1.20` в старых проектах.

## Уже применено (маркерами)

| что | где | суть |
|---|---|---|
| `ItemStackTemplate(Holder<Item>, int)` | `core/definitions/ItemDefinition` | ваниль: `(Item,int)` → `item.asItem()` |
| `OitPipelineSet.builder(Identifier, Builder)` | `client/render/AERenderPipelines` (4 места) | ваниль ждёт `String` → `.toString()` |
| `EntityType.Builder.setTrackingRange/setUpdateInterval/setShouldReceiveVelocityUpdates` | `core/definitions/AEEntities` | ваниль: `clientTrackingRange`/`updateInterval`; «получать скорость» — поведение по умолчанию |
| `BlockEntityType(..., Block...)` | `core/definitions/AEBlockEntities` | ваниль: `Set<Block>` |
| `SavedDataType(Identifier, Supplier, Codec)` и `(…, Function<ServerLevel,Codec>)` | `api/features/PlayerRegistryInternal`, `server/services/ChunkLoadState`, `server/services/compass/CompassRegion` | ваниль: 4 аргумента, `DataFixTypes`; уровнезависимость — через `ServerLifecycleHooks.getCurrentServer()` |

**Известное расхождение:** у `ChunkLoadState` ванильный кодек не получает уровень, поэтому
в FABRIC-ветке берётся `overworld`. Для состояния форс-лоада чанков это неверно, если данные
принадлежат другому измерению. Помечено TODO в коде.

## В работе / известно, но ещё не оформлено

| что | где | почему не в шиме |
|---|---|---|
| `getModelData()` / `getModelData(BlockPos)` / `requestModelDataUpdate()` | 15 файлов | ваниль не знает такого понятия; правится на `ModelDataHooks` |
| `RegistryFriendlyByteBuf(ByteBuf, RegistryAccess, ConnectionType)` | `AEBaseBlockEntity`, `MEInventoryUpdatePacket` | конструктор добавлен NeoForge |
| `Registry#addCallback` + `makeRegistry`/`RegisterEvent`/`NewRegistryEvent`/`RegistryBuilder` | `AEKeyTypesInternal`, `AppEngBase`, `InitStructures` | сделано: `NfRegistry` + `RegistryCallbacks` + `ModRegistries.expose` публикует `ae2:keytypes` в `BuiltInRegistries.REGISTRY`; семантика соблюдена — `bakeAll()` вызывается после блока `RegisterEvent`, а не на `NewRegistryEvent` |
| `ItemStack.isSameItemSameComponents(ItemStack, ItemStackTemplate)` | `InscriberTestPlots` | NeoForge расширяет `ItemStackTemplate` |
| `KeyConflictContext` вместо `InputConstants.Type` в `KeyMapping` | `AppEngClient` | сигнатура `KeyMapping` — патч NeoForge |
| `CreativeModeTab.builder(...)` | `MainCreativeTab`/`FacadeCreativeTab` | перегрузка NeoForge |
| `mezz.jei.api.neoforge` | `client/integrations/jei/**` | JEI-интеграция AE2 использует NeoForge-специфичный пакет JEI; на Fabric его нет — либо гейт сорсета, либо правка интеграции |
| `RecipeOutput.accept(key, recipe, null, IConsumableCondition)` | `recipes/mattercannon/MatterCannonAmmo:103` | 4-аргументный `accept` — патч NeoForge; на Fabric выкинуть аргумент и писать `neoforge:conditions` в JSON руками |
| модельный датаген: `PropertyDispatch#modifyUnbaked`, `MultiVariantGenerator#withUnbaked`, `MultiVariant.of(CustomBlockStateModelBuilder)`, `TextureMapping#copy()/extend()` | `datagen/providers/models/{ModelSubProvider,BlockModelProvider}` | это не отдельные методы, а неофорджевская unbaked-подсистема поверх ванильных классов. **Не патч-сет, а вторая ветка препроцессора**: `#if FABRIC` пишет те же json'ы на ванильном `ModelProvider`/`BlockModelGenerators`, `#else` оставляет оригинал |

`DifferenceIngredient` **не** попал сюда: написан свой (`compat/.../common/crafting/`),
считает разницу на месте и отдаёт обычный ванильный `Ingredient` (в датагене выйдет
развёрнутым списком, а не ссылкой на тег).

`OrHolderSet` тоже свой: NeoForge'вский наследует `CompositeHolderSet` и опирается на патчи
`HolderSet` (сериализация/инвалидация), поэтому написан честный `implements HolderSet`
с объединением компонентов. Несериализуем — для рецептов AE2 хватает.

## Что патчем НЕ является

Всё остальное закрыто шимом или accesswidener'ом:

- шим: события и шина, `DeferredRegister`, `Tags`, `ModelData`/`ModelProperty`, конфиги,
  FML-слой, `transfer/**`, `capabilities/**`, мост жидкостей;
- accesswidener (14 записей под дрейф ванили): `RecipeProvider.items`,
  `AbstractContainerScreen.imageWidth/imageHeight`, `Slot.x/y`, `Screen.renderables`,
  `SimpleParticleType.<init>`, `ModelTemplates.create/createItem`, `Player.closeContainer()`,
  `Options.keyMappings`, `RespawnConfig.getDimensionOrDefault`, `BlockEntityType.validBlocks`,
  `EquipmentSlot.countLimit`, `BucketItem.content`, `ItemStackLinkedSet.TYPE_AND_TAG`.

## Найдено при первом запуске сервера (не видно при компиляции)

| что | как закрыли |
|---|---|
| `neoforge:custom_{clouds,skybox,weather_effects}` — атрибуты окружения, которых нет в ванильном реестре `environment_attribute` | убрали из `InitBiomes` (маркер) и из сгенерированного `data/ae2/worldgen/biome/spatial_storage.json` |
| 64 тега `c:nuggets/*` не существуют на Fabric | сгенерировали пустые теги в `compat/resources/data/c/tags/item/nuggets/*.json` (таких металлов нет — пустой тег семантически верен) |
| 5 рецептов используют `neoforge:ingredient_type: neoforge:difference` | развернули в явный список предметов (ровно то, что сделал бы наш `DifferenceIngredient` на датагене) |
| `RecipeMap` **есть в ванили** (в common-джарнике), а наш одноимённый класс в `net.minecraft.*` ломал миксин Fabric recipe API | свой класс удалён; `RecipeMaps.of(level/RegistryAccess)` использует `RecipeMap.create(lookup)` |
| собственные реестры (`NewRegistryEvent`) не видны `RegisterEvent` | `NewRegistryEvent` запоминает созданные реестры, `RegisterEvent.getRegistry` сначала смотрит туда |
| `RecipeType.register(...)` дублировал запись своего `DeferredRegister` | в FABRIC-ветке тип регистрируется только через `DR`, объект берётся из `DeferredHolder.get()` |
| `CauldronInteraction.Dispatcher.put` пакетно-приватный | AW `accessible method` |

## Найдено в рантайме клиента (компилируется, но не работает)

| симптом | причина | как закрыли |
|---|---|---|
| пустая сетка ME-терминала: сеть живая, `ae2:me_inventory_update` доходит, `Repo` знает записи | `AEKeyTypesInternal.setRegistry()` вешает `BakeCallback`, который на NeoForge срабатывает **после всех регистраций**; наш `RegistryCallbacks` вызывал его немедленно — на `NewRegistryEvent`, когда `ae2:keytypes` ещё пуст (типы приходят позже, в `RegisterEvent(AEKeyType.REGISTRY_KEY)`). Пустой `AEKeyTypes.getAll()` → пустой `KeyTypeSelection` → `Repo.addEntriesToView` отсекает все записи по `typeFilter` | `RegistryCallbacks` копит колбэки, `NfCompat.runRegistrationPhases()` вызывает `bakeAll()` сразу после блока `RegisterEvent` |
| блок-модели получали пустое model data при сборке меша чанка | `RenderChunkRegion.getBlockEntity()` в этом контексте всегда `null` | снапшотом служит сам `RenderSectionRegion` (у него `getBlockEntity` рабочий); временный `nfcompat/model/client/ClientModelDataLookup` удалён вместе с диагностикой |
| `Platform.isClient()/isServer()` врали в одиночной игре | AE2 сравнивает `ThreadGroup`, а на Fabric группы клиента и встроенного сервера совпадают | `nfcompat.ServerThread` + маркеры в `appeng/util/Platform` |
| все динамические блок-модели (кабель, привод, крафт-куб, пилон, кварц, краска, QNB) не рисовались | ванильный `BlockStateModel.collectParts(RandomSource, List)` не имеет ни мира, ни позиции, а наш дефолт подставлял `EMPTY`-мир, `BlockPos.ZERO` и воздух. Indigo из Fabric API зовёт именно его через `FabricBlockStateModel.emitQuads` | два редиректа: `ModelBlockRendererMixin` (в `tesselateBlock` контекст есть) и `FabricBlockStateModelMixin` (в `emitQuads` контекст есть) — оба отдают его позиционному `collectParts` |
| `Missing registry: minecraft:recipe` в тике сервера (дропнутый предмет, апгрейд крафт-юнита) | `Registries.RECIPE` есть у датапаков, но не в `server.registryAccess()`; `RecipeMap.create(lookupOrThrow(...))` падал | карта берётся из `RecipeManager` через `@Accessor("recipes")` (`RecipeManagerAccessor`), `RecipeMaps.of(...)` |
| на клиенте не работали подстановка результата, кодирование крафт-шаблонов и JEI-подстановка | ваниль 26.3 не отправляет клиенту рецепты (`ClientLevel.recipeAccess()` = `ClientRecipeContainer` без списка), а AE2 берёт их из `RecipesReceivedEvent` — событие никто не постил | пакет `nfcompat:recipes` (`RecipeSync`, порции по 100) при входе и после перезагрузки датапаков; клиент собирает `RecipeMap` и постит `RecipesReceivedEvent` |
| шины, Pattern Provider, интерфейс и P2P «не видели» соседние сундуки/печи | `Capabilities.Item.BLOCK`/`Fluid.BLOCK` в шиме заканчивались на NeoForge-провайдерах, которых на Fabric нет; `FabricExternalStorageStrategy` был пустым файлом | `nfcompat/bridge/FabricCapabilities` мостит в `ItemStorage.SIDED`/`FluidStorage.SIDED` прямо из `BlockCapability.getCapability`; адаптер — `StorageItemHandler` (для жидкостей уже был) |
| кастомный GUI: зум в чекбоксах/полях, куски атласа в ползунках и в маркере pinned-строки | в FABRIC-ветке `Blitter.blit` звалась 11-аргументная `GuiGraphicsExtractor.blit`, которая подставляет размер источника = размеру назначения (для 256x256 при dest==src совпадает случайно) | 13-аргументная перегрузка с явными `srcRect` и `referenceWidth/referenceHeight`; отражение (`TextureTransform`) не используется в AE2 и в Fabric-ветке не поддержано |
| JEI: плагин AE2 отсутствовал в списке JEI без единой ошибки | Fabric-JEI берёт плагины только из entrypoint `jei_mod_plugin` в `fabric.mod.json`, сканирования `@JeiPlugin` нет | entrypoint `appeng.client.integrations.jei.JEIPlugin` в `compat/resources/fabric.mod.json` |
| JEI: жидкости не находились, R/U по ним молчал | AP2 полагалась на `mezz.jei.api.neoforge.NeoForgeTypes`, которого в Fabric-JEI нет; была своя заглушка, но JEI её тип не знает | FABRIC-ветка: `FabricTypes.FLUID_STACK` + `JeiFluidIngredient`, пересчёт через `nfcompat.bridge.FluidUnits` (1000↔81000), рендер — `setFluidRenderer`; заглушка `NeoForgeTypes` удалена |
| энергия наружу/извне не ходила | `Capabilities.Energy.BLOCK` в шиме никуда не мостился: чужие генераторы AE2 не видели, AE2 не отдавала энергию (FE-P2P мёртв) | `nfcompat.bridge.FabricEnergy` → `EnergyBridge` (Team Reborn `EnergyStorage.SIDED` ↔ наш `EnergyHandler`, 1 E ≡ 1 FE). Библиотеку **не поставляем** (`compileOnly` + `localRuntime`, `recommends`), без неё мост выключен по `isModLoaded` |

## Исключённые миксины AE2
| миксин | почему |
|---|---|
| `AnvilMenuMixin` | целится в `createResultInternal`, которого в ванили нет — метод добавляет NeoForge. Mixin не может добавить public static член, значит intrinsic-чары наковальни остаются непокрытыми |

## Временная диагностика, спрятанная за `//#if FABRIC`

Раньше тут были строки в `CableBusModel`/`CableBusRenderer` — кабель уже рисуется, они удалены.
Из диагностики остались только `nfcompat/Diagnostics` (model data при пустом block entity) и
временная команда-проба `/nfcompat cap|parts|pp|provider` в `nfcompat/debug` — она полезна, когда
надо за один прогон понять, видит ли AE2 соседний контейнер и куда именно провайдер целится.

## Мост точек входа

`nfcompat.NfCompat` + `NfCompatServer` / `NfCompatClient`: создают `ModContainer`, вызывают конструктор AE2
(`AppEngServer`/`AppEngClient`), затем вручную проходят этапы, которые на NeoForge делает загрузчик —
`NewRegistryEvent`, `RegisterEvent` по каждому нужному реестру, `RegisterCapabilitiesEvent`,
`RegisterPayloadHandlersEvent`, `RegisterTicketControllersEvent`, `FMLCommonSetupEvent`,
`InterModEnqueueEvent` (на клиенте ещё `RegisterClientPayloadHandlersEvent` и `FMLClientSetupEvent`).

Порядок важен: реестр `AEKeyType` создаётся в `NewRegistryEvent`, и без него одноимённый `RegisterEvent`
нечего обрабатывать.
