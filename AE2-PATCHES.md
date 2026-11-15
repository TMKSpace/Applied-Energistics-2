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
| `Registry#addCallback` + `makeRegistry`/`RegisterEvent`/`NewRegistryEvent`/`RegistryBuilder` | `AEKeyTypesInternal`, `AppEngBase`, `InitStructures` | нужен свой реестр (`NfRegistry implements Registry, IRegistryExtension`) — это работа по шиму, не патч |
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

## Мост точек входа

`nfcompat.NfCompat` + `NfCompatServer` / `NfCompatClient`: создают `ModContainer`, вызывают конструктор AE2
(`AppEngServer`/`AppEngClient`), затем вручную проходят этапы, которые на NeoForge делает загрузчик —
`NewRegistryEvent`, `RegisterEvent` по каждому нужному реестру, `RegisterCapabilitiesEvent`,
`RegisterPayloadHandlersEvent`, `RegisterTicketControllersEvent`, `FMLCommonSetupEvent`,
`InterModEnqueueEvent` (на клиенте ещё `RegisterClientPayloadHandlersEvent` и `FMLClientSetupEvent`).

Порядок важен: реестр `AEKeyType` создаётся в `NewRegistryEvent`, и без него одноимённый `RegisterEvent`
нечего обрабатывать.
