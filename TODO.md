# TODO: где шим ещё тонкий

Список дыр NeoForge-шима, отсортирован по «насколько больно будет в игре». Ведём здесь,
чтобы не искать по чатам. Статусы: `[ ]` не сделано, `[~]` в работе, `[x]` сделано и проверено.

## 1. Наружу из AE2 ничего не выставляется
`[x]` Сделано: `nfcompat/bridge/StorageBridge` регистрирует `ItemStorage.SIDED`/`FluidStorage.SIDED`
по `Capabilities.*.providerBlocks()` + `getCapabilityDirect`; адаптеры `ItemHandlerStorage`
(SlottedStorage) и переписанный `FluidHandlerStorage` (через `HandlerTransactions` + `FluidUnits`).
Сверено с 1.20.1 `InitApiLookup` — потерянных блоков нет.

## 2. Item-capabilities вообще не смостены
`[~]` Сделано в коде: `FabricItemCapabilities` (inbound: `ItemStorage.ITEM`/`FluidStorage.ITEM`/
`EnergyStorage.ITEM` → наши хендлеры, контекст через `ItemAccessContainerContext`) и
`ItemCapabilityBridge` (outbound по `ItemCapability.providerItems()` + `getCapabilityDirect`).
Главное попутное: `HandlerTransactions.runOntoFabric` — Fabric-ная транзакция привязывается к
NeoForge-ному root, из-за чего симуляция больше не опустошает вёдра (`StorageFluidHandler`,
`StorageItemHandler`, `EnergyStorageHandler` переведены на него).
Проверять в игре: ведро воды в жидкостном терминале (`/nfcompat cap held`), пустое ведро через
`EmptyBucketStorage`, R/U по жидкости, P2P-аттюнмент ведром/энергоячейкой.
Outbound: 16 энерго-предметов AE2; fluid/item-в-предмете наружу пока 0 (AE2 таких провайдеров
не регистрирует), inbound работает через Fabric-провайдеры ванили.

## 3. Масштаб жидкостей ×81
`[ ]` Граница NeoForge 1000/ведро ↔ Fabric 81000 (`nfcompat.bridge.FluidUnits`). Уже дважды
стреляло в разные стороны, края стоит перепроверить.
Тест: ведро → бак → ячейка → терминал, и обратно; сравнить объёмы в тултипах.

## 4. Spatial storage
`[~]` Ядро цело: уровень создаётся миксином `spatial.MinecraftServerMixin` (в логе видно, что
`ae2:spatial_storage` жил), `SavedData` плотов, обмен блоками/BlockEntity, телепорт через ванильный
`TeleportTransition`, генератор чанков — всё на ванили.
`[x]` Чанк-лоадер: владельцы тикетов в персистентном `TicketOwnershipData` (SavedData в overworld),
форс снимается только последним владельцем, `validateTickets` на `ServerLevelEvents.LOAD`,
`LevelEvent.Load`/`ChunkEvent.Load` теперь постятся.
`[ ]` Скайбокс spatial-измерения: `RegisterCustomEnvironmentEffectRendererEvent` никто не постит,
`neoforge:custom_skybox` вырезан из `InitBiomes` → в ячейке обычное небо. В 1.20.1 это закрывал
`mixins/spatial/SkyRenderMixin` — можно повторить целиком в compat (client-миксин на рендер неба).
`[ ]` `MatrixFrameBlock`: в FABRIC-ветке вырезаны `onBlockExploded`/`canEntityDestroy` → стенку
плота можно пробить взрывом. Либо вернуть оверрайды в FABRIC-ветке, либо миксин на
`Explosion#finalizeExplosion`.
`[ ]` `ChunkLoadingService.isChunkForced()` читает `appeng.server.services.ChunkLoadState`, который
в 26.3 никто не наполняет (класс package-private) → `appeng/mixins/chunkloading/ChunkMapMixin`
(рандом-тики в форс-лоаднутых чанках) не работает. Нужен доступ к `src` либо перевод
`isChunkForced` на наш `TicketOwnershipData`.
Тест: spatial IO порт + пилоны + ячейка, редстоун → блоки уезжают в ячейку и возвращаются;
`/ae2 spatial info|tp|tpback`; перезапуск сервера сохраняет плот и форс чанков.

## 5. Инструменты на неофорджевских патчах MC
`[x]` `PlayerEvent.BreakSpeed` теперь постится: `EventHooks.getBreakSpeed` + `PlayerDestroySpeedMixin`
(хвост `Player.getDestroySpeed`). Живой слушатель Ae2 `SkyStoneBreakSpeed` снова даёт ×10 для
инструмента лучше железного (алмаз ~19 тиков вместо ~188; железо — без изменений).
`[x]` Лазание по кабелю: `LivingEntityClimbableMixin` на HEAD `onClimbable` спрашивает
`CableBusBlock.isLadder` для `getInBlockState()` (в 26.3 `getFeetBlockState` переименован);
цепочка `CableBusContainer → IPart → CableAnchorPart` ожила. Без анкера не лезет.
Тест: замер копания скайстоуна; кабель + `ae2:cable_anchor` на горизонтальную грань, зажать пробел.

## 6. Крафт-машины (molecular assembler, inscriber)
`[~]` Идут через `AECapabilities.CRAFTING_MACHINE` (наш шим, не патч NeoForge) — должно жить,
но автокрафт «через ассемблер» в игре не проверялся.
Тест: автокрафт крафт-шаблона с ассемблером рядом; автокрафт через inscriber.

## 7. P2P-туннели
`[~]` Item/fluid должны ожить от inbound-моста (проверить), FE — push-based ограничение Team
Reborn: AE2-источник сам наружу не толкает, работают только pull-моды.
Тест: item-P2P между двумя сетями; FE-P2P → внешний потребитель.

## 8. Интеграции
`[ ]` JEI: плагин теперь грузится (entrypoint), но падает в `registerP2PAttunement` (п. 2).
`[ ]` REI — не проверялся вовсе.
`[ ]` Jade — плагин грузится, реальный вывод не смотрели.
`[x]` GuideME — сознательно выключен (`ArgumentTypeInfos.registerByClass` недоступен миксином).

## 9. Мелочи, замеченные по пути
`[ ]` `FluidBlitter`: цвет жидкости на Fabric принудительно `-1` (белый) — вместо тинта.
Можно взять `FluidVariantAttributes.getAssociatedColor`.
`[ ]` `Blitter.copy()` не копирует `blending`/`transform` (сейчас нигде не стреляет — все
использования задают флаги после `copy()`).
`[ ]` `Blitter.GUI_TEXTURED_OPAQUE` на Fabric = обычный `GUI_TEXTURED` (блендинг включён).
Ванильный `GUI_OPAQUE_TEXTURED_BACKGROUND` — не общий пайплайн (используется только в
`LevelLoadingScreen`), так что нужен свой пайплайн, если понадобится настоящая непрозрачность.

## Стратегия покрытия: что значит «весь проект»

Чтобы «покрыть AE2» не превратилось в бесконечный чек-лист, делим остаток на три категории.

### A. Закрываемо кодом (этим и занимаемся)
Стабы, потерянные при переезде миксины, вырезанные FABRIC-ветки. Цикл замкнут: аудит →
фиксер → сборка → проверка в игре через MCP. 1.20.1-порт служит референсом, поэтому
«как было» всегда известно.

### B. Закрываемо, но это решение, а не код
- **Датаген.** В `build.gradle` задачи датагена нет: `src/generated/resources` — JSON'ы,
  закоммиченные апстримом. Мы их наследуем, но перегенерировать не можем. Если апстрим
  поменяет генератор, наши JSON'ы тихо устареют. Решение: либо не трогать (живём на
  апстримных данных), либо портировать датаген-провайдеры (большая работа).
- **Геймтесты.** `src/test/java` пуст, остался только `GameTestPlotAdapter` без потребителя,
  а `RegisterGameTestsEvent` никто не постит. Портирование 55 тестов на Fabric Gametest API —
  отдельный проект, не про шим.
- **Интеграции.** GuideME требует патча джара на сборке; EMI и WTHIT вырезаны осознанно;
  REI не проверялся вовсе.

### C. «Покрыть» нельзя — можно только честно задокументировать
Работает, но не так, как NeoForge. Это не баги, а границы шима:
- транзакции через границу Fabric↔NeoForge: привязаны журналом, но составной откат через
  два хранилища не гарантирован;
- инвалидация capability — no-op (`BlockCapabilityCache` не получает уведомлений);
- model data — без кэша, каждый запрос читает block entity (дороже, корректно);
- рецепты на клиенте — полный снапшот пакетом, а не инкрементальный синк;
- отсутствующие события NeoForge (каждое — тихая деградация у модов, не падение);
- ванильные хуки, которых в 26.3 нет вообще (`isLadder`, `getDestroySpeed` с игроком,
  `onItemUseFirst`, `onBlockExploded`), обслуживаются миксинами и могут отвалиться при
  обновлении ванили — следить при каждом rebase.

### Definition of done
1. Все стабы из аудитов закрыты или явно помечены «осознанно пусто»;
2. каждая подсистема AE2 проверена в игре хотя бы раз (список ниже в этом файле);
3. `classes` + `shimTest` зелёные, `sync-upstream.ps1` проходит без конфликтов;
4. собранный джар запускается (сейчас проверялся только dev-run);
5. известные семантические расхождения из п. C перечислены в README/TODO, чтобы это был
   осознанный выбор, а не сюрприз.

## Политика: что чинить в шиме, а что — гейтом в src

Правило простое: **апстрим мы не патчим**. Любая «универсализация» живёт в `compat/java`
или в `//#if FABRIC`-блоке, иначе мы перестаём мержаться с апстримом.

Порядок выбора (сверху вниз, первый подходящий):

1. **Шим** — если расхождение закрывается тем, что мы *добавляем* в `compat/java` поведение
   NeoForge-API. Тогда `src` не меняется вообще. Примеры, уже сделанные так:
   `PlayerEvent.BreakSpeed` (постим из миксина), `PlayerInteractEvent.RightClickBlock`
   (`UseBlockCallback`), `onItemUseFirst`/`doesSneakBypassUse` (диспетчер в шиме),
   `getCloneItemStack` с игроком (редирект), `Capabilities.*` (мосты), `RegisterStandalone`
   (пост + бейк).
2. **Миксин в `compat/java`** — если в ванили 26.3 нет хука вообще (`LivingEntity#onClimbable`,
   `Explosion`, `ChunkMap`). `src` тоже не меняется.
3. **Гейт в `src`** — только когда нужно вызвать наш код из AE2 (например, заменить
   неофорджевский вызов на `nfcompat.*`). Тогда: `//#if FABRIC` — наш вызов,
   `//#else` — апстримный, посимвольно нетронутый. Добавляем строки, не переписываем.
4. **Принять расхождение** — если цена универсализации выше пользы (см. п. C выше).

Чего **не** делаем: не переписываем общий код `src` так, чтобы он работал «в обе стороны».
Любая такая правка (а) ломает компиляцию апстрима без `compat/java`, если ссылается на
`nfcompat.*`, (б) превращает каждый rebase в конфликт, (в) меняет поведение NeoForge-сборки,
за которое отвечает апстрим. Итог: универсализация = перенос в шим, а не правка `src`.

Проверка перед правкой: «смогу ли я сделать это, не трогая `src`?» Если да — делаем в шиме.
Если нет — гейт, и в коммите пишем, почему без `src` не вышло.

## Аудит стабов: результаты (три разведчика)

Аудит `compat/java/net/neoforged`, `compat/java/nfcompat` и FABRIC-веток в `src`.
Ниже только то, что влияет на игру; «осознанно пустое» не перечисляем.

### P0 — краш
`[x]` **Standalone-модели** — закрыто. `StandaloneModels` держит карту из
`ModelEvent.RegisterStandalone` и отдаёт её Fabric-плагину `ModelLoadingPlugin` (`ExtraModelKey`),
`get(...)` читает запечённое из `ModelManager`. Кривошип и ME-сундук с ячейкой больше не падают.

### P1 — ключевая механика мертва
`[x]` **`onItemUseFirst`** — закрыто без правок `src`: в шиме появился `IItemExtension` +
миксин `@Mixin(Item.class) implements IItemExtension`, поэтому все 11 AE2-переопределений стали
настоящими override; диспетчер — `ItemUseFirstBridge` на `UseBlockCallback` (регистрируется до
`RightClickBlockBridge`). Включая `doesSneakBypassUse` для карты памяти.
`[x]` **Инвалидация capability** — закрыто иначе: кэш в шиме убран целиком
(`BlockCapabilityCache.getCapability()` всегда спрашивает провайдеры), поэтому устаревших
хендлеров не бывает. Цена — лишние запросы на тик у шин/провайдеров; если профилировка покажет
горячую точку, вернёмся к кэшу, но уже с настоящей инвалидацией по смене блока.
`[ ]` **`AECapabilities` наружу не выставлены:** `ME_STORAGE`, `CRAFTING_MACHINE`,
`GENERIC_INTERNAL_INV`, `IN_WORLD_GRID_NODE_HOST`, `CRANKABLE` видны только внутри шима.
В 1.20.1 все были публичными Fabric-lookup (`MEStorage.SIDED`, `GenericInternalInventory.SIDED`,
`ICraftingMachine.SIDED`, `ICrankable.LOOKUP`) — переносится в шим без правок `src`.

### P2 — заметно, но не ломает
`[ ]` `onNeighborChange` кабеля мёртв (нет `INeighborChangeSensitive` + миксина на
`Level#updateNeighbourForOutputSignal`): шина хранения не получает пинг соседа — миксин, `src` не трогаем.
`[ ]` `onChunkUnloaded` не вызывается, `setRemoved` срабатывает до сохранения чанка — риск
потерять данные ноды. Портировать `DeferredBlockEntityUnloader` в шим.
`[ ]` `ExtractBlockOutlineRenderStateEvent` не постится: нет превью установки деталей и
per-part обводки (светится весь блок).
`[~]` `RegisterPictureInPictureRenderersEvent` постится и рендерер регистрируется, но состояния
никто не отправляет: `FluidBlockRendering` нужен доступ к `GuiGraphicsExtractor` (AW на
`scissorStack`/`guiRenderState` либо интерфейс-миксин). Пока 3D-иконки жидкости в JEI/REI не рисуются.
`[x]` `BuildCreativeModeTabContentsEvent` постится из миксина на `CreativeModeTab#buildContents` —
~19 предметов вернулись в ванильные вкладки.
`[x]` Model data: снапшотом служит сам `RenderSectionRegion` (у него рабочий `getBlockEntity`,
проверено javap + логами), клиентский фолбэк `ClientModelDataLookup` удалён вместе с диагностикой.
`[x]` Скайбокс spatial-измерения: событие постится, `EnvironmentRenderers` связывает измерение с
id, `LevelRendererEnvironmentMixin` перехватывает небо/облака/погоду (в обычном мире — ваниль).
`[ ]` `ExtractLevelRenderStateEvent` + `SubmitCustomGeometryEvent` не постятся — area overlay
не рисуется вообще.
`[ ]` Фасады без ambient occlusion (`FacadeBuilder` жёстко `false`) — выглядят плоскими;
в 26.3 поля в `BlockStateModelPart` нет, значит нужен свой флаг в шиме либо принять.

### P3 — косметика, мёртвый код, дев-инфраструктура
`[ ]` `RegisterGameTestsEvent` + `RegisterEvent.register(...)` для `TEST_INSTANCE_TYPE` мертвы —
геймтесты не запускаются вовсе.
`[ ]` Мелочи: `TinyTNTBlock.onCaughtFire` (огонь не поджигает мини-ТНТ),
`SkyStoneChestRenderer.getRenderBoundingBox` (сундук моргает на краю фрустума), пайплайны
`LINES_BEHIND_BLOCK`/`STORAGE_CELL_LEDS` без depth-state, `ModConfigEvent.Reloading` не постится,
`RecipeSync.CLIENT_BUFFER` не чистится на disconnect, `ServerThread` не сбрасывает поток при
новом мире, `FakePlayerFactory` без кэша, `InterModComms` в никуда, пустой экран конфига,
нет entrypoint ModMenu, мост `Item.ITEM` мёртв с обеих сторон.
`[ ]` Удалить временное перед релизом: `/nfcompat cap|parts|pp|provider`, мёртвые `ofSlot()`.

## Уже закрыто (чтобы не искали заново)

`[x]` Кабель и все динамические блок-модели — позиционный `collectParts` через
`ModelBlockRendererMixin` + `FabricBlockStateModelMixin`.
`[x]` Пустой `AEKeyTypes` — отложенный bake в `RegistryCallbacks`.
`[x]` `Platform.isClient/isServer` — `nfcompat.ServerThread`.
`[x]` Рецепты: сервер (`RecipeManagerAccessor`), клиент (`nfcompat:recipes` + `RecipesReceivedEvent`),
шаблоны крафта и JEI-подстановка.
`[x]` Inbound item/fluid: `FabricCapabilities` + `StorageItemHandler` — шины, Pattern Provider,
интерфейс, P2P.
`[x]` Кастомный GUI (кроп в `Blitter`), JEI entrypoint + жидкостной ингредиент.
`[x]` Энергия inbound/outbound (Team Reborn), библиотеку не поставляем — см. README.
