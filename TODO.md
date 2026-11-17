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
`[x]` Ядро проверено в игре (MCP): пилоны 3×3×3, порт + контроллер + ячейка, редстоун → блоки
уезжают в ячейку. `[ ]` **Spatial-измерение полностью тёмное** (`skyLight=0`, `blockLight=0`,
хоть глаз выколи). Это **не наш порт**: у измерения `has_skylight=false`, а в 26.3 яркость без
неба задаётся атрибутом `minecraft:gameplay/sky_light_level` (Незер ставит 4.0) — у AE2 его нет,
у них в `InitDimensionTypes` стоит `// TODO 1.21.11: environmental effects`. То есть на NeoForge
картина та же. Лечится одной строкой в `dimension_type/spatial_storage.json` + `InitDimensionTypes`
(15.0 даёт полную яркость), но решено **оставить как в апстриме**; проверить правку на живом мире
не удалось (dimension_type читается при загрузке мира, `/reload` его не перечитывает).
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
`[x]` JEI: плагин грузится (entrypoint `jei_mod_plugin`), `FabricTypes.FLUID_STACK` +
`setFluidRenderer`, P2P-аттюнмент больше не падает на пустых списках. Реальный вывод
в игре не смотрели.
`[ ]` REI — не проверялся вовсе.
`[ ]` Jade — плагин грузится, реальный вывод не смотрели.
`[x]` GuideME — **работает, проверено в игре**: страницы (текст, спрайты, ссылки), 3D-сцены,
хоткей «зажми G у предмета» (через постинг `ItemTooltipEvent`), иконки жидкостей, поиск/навигация.
Всё GuideME-специфичное изолировано в `nfcompat/compat/guideme` и `nfcompat/mixin/guideme`
(каталоги удаляются целиком, без них шим живёт).
Закрытые неофорджевские точки: `ArgumentTypeInfos.registerByClass`, `ModContainer.registerExtensionPoint`
(void), `RenderPipeline.toBuilder()` (+ перенос shader defines), `KeyMapping` 5-арг конструктор,
`SimpleJsonResourceReloadListener.scanDirectory`, `GuiGraphicsExtractor.{peekScissorStack,
submitGuiElementRenderState,submitPictureInPictureRenderState}`, `KeyMapping.getKey`,
`ItemStackTemplate` ctor'ы, `FluidModel.{customRenderer,fluidTintSource}`, `hasMaterialFlag(4)`,
`CommonListenerCookie` 15-арг, `Entity.onAddedToLevel`, `LevelAccessor.*`, `Lazy` (класс!),
`NeoForgeClientConfig` (с загрузкой конфига), `ModelDataManager`, `RenderPhaseKeys`/`submitSpecial`,
`RegisterFeatureRenderersEvent`, `TextureAtlasStitchedEvent`, `ScreenEvent`-стаб, `ItemTooltipEvent`.
Известные ограничения: `/guideme export` (site-export) мёртв, датаген GuideME не подключён.
`ScreenEvent.Opening` теперь **постится** (`client.MinecraftScreenOpeningMixin` → `@ModifyVariable`
в `Minecraft#setScreen`), так что фича «показать гайд при входе» живая.
Открыто: dev-страховка рендера страницы (`-Dnfcompat.guide.debugCatch`) — перед релизом
проверить, что она выключена.

## 9. Мелочи, замеченные по пути
`[x]` `FluidBlitter`: тинт жидкости закрыт. `FluidTint` берёт цвет из
`FluidVariantAttributes.getAssociatedColor` (плюс `FluidModelData`), цвет доезжает до
`FluidBlitter`/`FluidKeyRenderer`/`SkyStoneTankRenderer`; `-1` остался только фолбэком.
Проверено в гайде: иконка воды рисуется текстурой `water_still` с тинтом (видна светлая
кромка сверху — UV верные). Другой жидкости в рецептах гайда нет, так что кросс-проверки не
будет: «ровный синий» — это сама текстура воды (16×16, почти однородная).
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
- **Датаген.** Fabric-датаген подключён (`configureDataGeneration`), но registry-записи
  (рецепты/лут/адвансменты/ворлдген) он не выгружает: прогон дал 899 совпавших файлов и 1087
  отсутствующих. Источник правды — `src/generated` апстрима; если апстрим поменяет генератор,
  наши JSON'ы тихо устареют. Решение: жить на апстримных данных (или портировать провайдеры).
- **Геймтесты.** Харнесс есть и работает: `runGameTest` (-PgtFilter) поднимает обычный
  `GameTestServer` на ванильных реестрах `TEST_ENVIRONMENT`/`TEST_INSTANCE`, шим постит
  `RegisterGameTestsEvent` + эмулирует `ModList.getAllScanData` (без него `TestPlots` не находит
  плоты). `TEST_INSTANCE` = 81 (`ae2:tiny_tnt*` — 3/3 passed). Полный набор падает на NPE
  в `InscriberTestPlots` (`expectedBottomSlot` = null) — это пробел порта, а не харнесса.
  `InternalInventoryTest` не стартует (эфемерный сервер, таймаут 120 с) — не чиним.
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

### Итоги трёх аудитов: что оказалось дублем, а что реализацией

- **Дубли ванили/Fabric (можно сводить, риск разный):** `BlockStateModelHooks` +
  `NeoForgeExtraCodecs` — Fabric уже умеет type-dispatch блокстейт-моделей
  (`CustomUnbakedBlockStateModel.register`); `GameData` — одна строка `Block.getId/stateById`;
  `fml/loading/FMLLoader` дублирует обход `FabricLoader.getAllMods()` из `ModList`.
- **Мёртвое (0 вызовов, удаляемо):** `registries/callback/*` + `IRegistryExtension` (AE2 ходит
  через `nfcompat.registry.RegistryCallbacks`), ~150 строк мёртвого API `DeferredRegister`,
  `FMLDedicatedServerSetupEvent`, `ModelEvent.{ModifyBakingResult,BakingCompleted,RegisterLoaders}`,
  `UnbakedModelLoader`, `CustomBlockModelDefinition`, `BakedColors`, мёртвые члены
  `DeferredItem/DeferredBlock.toStack()`, `RegistryBuilder.isSynced()/maxId()`, `InterModComms`.
  Часть уже вычищена (78 файлов, +328/−4130).
- **Настоящая реализация (не дубль, не трогать):** `MutableQuad`/`QuadTransforms`/`UVTransform`
  (ваниль не умеет собирать квад из 4 вершин), `fml/config/*` + `ModContainer.registerConfig`
  (TOML через night-config), `RegisterEvent`/`ModLoader`/`ModWorkManager`/`ParallelDispatchEvent`,
  `OrHolderSet`, `datamaps/*`, `testframework/junit/EphemeralTestServerProvider` (на нём все
  юнит-тесты), `SidedThreadGroups` + `nfcompat.ServerThread`.
- **Правило, которое из этого следует:** шим = **адаптер** там, где в ванили/Fabric есть
  эквивалент, и **реализация** только там, где эквивалента нет (quads, конфиги, MOD-шина,
  капабилити AE2). «Сначала искать существующее», иначе получаем `BlockStateModelHooks`
  поверх готового Fabric API.

### `Registry is already frozen` — риск и как он закрыт

`ModRegistries.expose` вызывается из `NewRegistryEvent`, то есть во время mod-init. Это
заставляет `BuiltInRegistries` инициализироваться и заморозиться раньше, чем
`fabric-registry-sync` вызовет `BuiltInRegistries.bootStrap()` в `Main.afterModInit`.
Шим снимает флаг `frozen` (`MappedRegistry.frozen` через reflection) и перевязывает holder'ы
в значения блоков. Риск остаётся один: ваниль переименует/унесёт поле `frozen` — тогда
падение будет громким и на старте, чинится одной строкой. Проверять при каждом rebase.

### P0 — краш
`[x]` **Standalone-модели** — закрыто. `StandaloneModels` держит карту из
`ModelEvent.RegisterStandalone` и отдаёт её Fabric-плагину `ModelLoadingPlugin` (`ExtraModelKey`),
`get(...)` читает запечённое из `ModelManager`. Кривошип и ME-сундук с ячейкой больше не падают.

### P1 — ключевая механика мертва
`[x]` **`onItemUseFirst`** — закрыто без правок `src`: в шиме появился `IItemExtension` +
миксин `@Mixin(Item.class) implements IItemExtension`, поэтому все 11 AE2-переопределений стали
настоящими override; диспетчер — `ItemUseFirstBridge` на `UseBlockCallback` (регистрируется до
`RightClickBlockBridge`). Включая `doesSneakBypassUse` для карты памяти.
`[x]` **Инвалидация capability** — закрыто в два шага. Сначала убрали кэш целиком
(`BlockCapabilityCache.getCapability()` всегда спрашивает провайдеры), чтобы не было
устаревших хендлеров. Затем выяснилось, что сам **сигнал** всё равно нужен: без него
`StorageBusPart.updateTarget` просыпается один раз и, если цель ещё не готова, ставит
`NO_UPDATE` навсегда (это валило `ae2:subnet`). Теперь `nfcompat.capabilities.CapabilityInvalidation`
— настоящий реестр `(уровень, позиция) → слабые ссылки на `ICapabilityInvalidationListener``,
`registerListener` пишет, `invalidate(holder)` доставляет; слушатель, вернувший `false`,
выбрасывается. Цена — лишние запросы к провайдерам на сигнал; кэш не возвращаем.
`[ ]` **`AECapabilities` наружу не выставлены:** `ME_STORAGE`, `CRAFTING_MACHINE`,
`GENERIC_INTERNAL_INV`, `IN_WORLD_GRID_NODE_HOST`, `CRANKABLE` видны только внутри шима.
В 1.20.1 все были публичными Fabric-lookup (`MEStorage.SIDED`, `GenericInternalInventory.SIDED`,
`ICraftingMachine.SIDED`, `ICrankable.LOOKUP`) — переносится в шим без правок `src`.
Поправка аудита transfer/capabilities: у этих пяти типов Fabric-аналога нет вообще, и наружу
их видно только через собственные `BlockApiLookup` шима. Ценность — только для сторонних
аддонов к AE2 (у нас таких нет), своих потребителей у AE2 внутри достаточно. Вывод: **не
блокер**, делать только если появится аддон; пометить как «осознанно внутри».

### P2 — заметно, но не ломает
`[x]` `onNeighborChange` кабеля — **закрыто**: `IBlockExtension.onNeighborChange` в шиме,
`Block implements IBlockExtension` и вызов из `Level#updateNeighbourForOutputSignal` по шести
направлениям (как в NeoForge); `CableBusBlock` в FABRIC-ветке снова компилирует этот метод,
так что части получают смену соседа (в т.ч. когда рядом поставили/убрали чужой контейнер).
`[ ]` `onChunkUnloaded` не вызывается, `setRemoved` срабатывает до сохранения чанка — риск
потерять данные ноды. Портировать `DeferredBlockEntityUnloader` в шим.
`[ ]` `ExtractBlockOutlineRenderStateEvent` не постится: нет превью установки деталей и
per-part обводки (светится весь блок).
`[~]` `RegisterPictureInPictureRenderersEvent` постится и рендерер регистрируется, но состояния
никто не отправляет: `FluidBlockRendering` нужен доступ к `GuiGraphicsExtractor` (AW на
`scissorStack`/`guiRenderState` либо интерфейс-миксин). Пока 3D-иконки жидкости в JEI/REI не рисуются.
`[ ]` **После ресайза окна 3D-сцена в гайде растягивается по ширине** (и переоткрытие гайда
НЕ помогает, то есть это не стейл, а рассинхрон единиц). Наши методы экстрактора сверены с патчем
NeoForge 26.3 (`patches/net/minecraft/client/gui/GuiGraphicsExtractor.java.patch`): всё совпадает
дословно, подмена не виновата. Ванильный `PictureInPictureRenderer.prepare` считает размер текстуры
как `(x1-x0)*guiScale`, а блит рисует `(x0..x1)` через `state.pose()`, то есть GuideME обязан
отдавать `x0..y1` и `pose` в согласованных единицах. Вероятная причина: `scene.getCameraSettings()
.setViewportSize(prefSize)` в `LytGuidebookScene.Viewport` использует размер в других единицах, чем
`bounds` после смены `effectiveScale` — камера рендерит один аспект, а блитится в другой.
Смотреть: `LytGuidebookScene.Viewport.onLayoutMoved/render` (`prefSize`, `bounds`,
`setViewportSize`), `ScenePictureInPictureRenderer.State` и `IndepentScaleScreen.calculateEffectiveScale`.
Наблюдение: растяжение ловится **в момент ресайза**, а в settled-состоянии страница рисуется
правильно (сверено с исходником страницы: `GameScene zoom="4"` + `crafting_cpus.snbt` +
`IsometricCamera yaw=195 pitch=30`, то есть широкий блок — это сама сборка из трёх CPU, а не
растяжение; `BlockImage scale="4"` для четырёх блоков тоже совпадает с тем, что видно в мире,
включая белый экран пустого Crafting Monitor). Понижено до косметики: если поймается стабильно —
копать те же места.
`[x]` `BuildCreativeModeTabContentsEvent` постится из миксина на `CreativeModeTab#buildContents` —
~19 предметов вернулись в ванильные вкладки.
`[x]` Model data: снапшотом служит сам `RenderSectionRegion` (у него рабочий `getBlockEntity`,
проверено javap + логами), клиентский фолбэк `ClientModelDataLookup` удалён вместе с диагностикой.
`[x]` Скайбокс spatial-измерения: событие постится, `EnvironmentRenderers` связывает измерение с
id, `LevelRendererEnvironmentMixin` перехватывает небо/облака/погоду (в обычном мире — ваниль).
`[x]` `ExtractLevelRenderStateEvent` + `SubmitCustomGeometryEvent` — **работает, проверено в игре**
(MCP): у `Spatial Anchor` включается overlay-режим, и вокруг его чанков рисуется подсветка.
Миксины: `client.LevelExtractorMixin` (TAIL `LevelExtractor#extract(...)`) и
`client.LevelRendererSubmitGeometryMixin` (после `ParticlesRenderState.submit(...)` в
`LevelRenderer#submitFeatures`); свой рендер-пас не понадобился. Хрупкое место — ванильные
имена `extract`/`submitFeatures`/`ParticlesRenderState.submit` при обновлении MC.
`[x]` `MutableQuad.recalculateWinding()` — **закрыто**: делегирует в ванильный
`FaceBakery#recalculateWinding(Vector3fc[], long[], Direction)` (метод есть в непатченной 26.3,
открыт AW). `recomputeNormals()` после него не нужен: метод только циклически сдвигает вершины
вместе с UV. В `src` по-прежнему не зовётся — это гигиена API. Комментарий в шиме исправлен
(раньше утверждал, что метода в ванили нет).
`[x]` `MutableQuad.itemGlintRenderType()` — **закрыто**: возвращал `itemRenderType` вместо
`itemGlintRenderType` (copy-paste). Сейчас никем не зовётся, но геттер больше не врёт.
`[ ]` Тинт `MutableQuad` → `BakedQuad` — **принято как граница шима** (категория C): ванильный
`BakedQuad` в 26.3 — record без цветов и нормалей, шим честно их дропает. Теряются: `QuadTinter`
(фасады тинтованных блоков, `FacadeBuilder:248`) и точечные `setColorRGB` (канальные точки
смарт-кабелей `CableBuilder:314-337`, крафт-монитор, карта памяти); сами текстуры кабеля цветные,
поэтому белеют не кабели, а индикаторы. Повторять NF-путь = интерфейс-миксин на ванильный record
плюс патч паковщика вершин — дорого и хрупко. Опционально потом: точечный `tintIndex`-вариант
для канальных точек через ванильный `BlockTintSource`, если в игре будет выглядеть сломанно.
`[ ]` Фасады без ambient occlusion (`FacadeBuilder` жёстко `false`) — выглядят плоскими;
в 26.3 поля в `BlockStateModelPart` нет, значит нужен свой флаг в шиме либо принять.

### P3 — косметика, мёртвый код, дев-инфраструктура
`[x]` `RegisterGameTestsEvent` + `RegisterEvent.register(...)` для `TEST_INSTANCE_TYPE` — ожили:
`runGameTest` находит 81 тест, `ae2:tiny_tnt*` проходит 3/3. `ModFileScanData` больше не заглушка
(`nfcompat.gametest.ModAnnotationScanner` сканирует `appeng/**` через ASM).
`[x]` Геймтесты: **81/81 passed** (`runGameTest -PgtFilter=*:*`, ~3.7 с). По дороге закрыто:
`InscriberTestPlots` (null-шаблоны), `ae2:subnet` (мёртвый сигнал инвалидации capability),
`ae2:memcard_pattern_provider` (у фейкплеера не было соединения).
`[ ]` Устаревший комментарий: `attachment/AttachmentType.java:8` утверждает, что attachment-API
у Fabric нет — на самом деле есть `fabric-data-attachment-api-v1` (`AttachmentRegistry`,
`AttachmentType`, `AttachmentTarget`, `AttachmentSyncPredicate`). Ключ реестра сейчас важнее API.
`[ ]` В `debug.log` при старте мелькает
`Class version 69 required is higher than the class version supported by the current version of Mixin`
для `PickItemFromBlockMixin` и `gametest/RegistryDataLoaderMixin`. Оба при этом применяются
(геймтесты регистрируются, pick-block проверен в игре), то есть это предупреждение на этапе
pre-apply, а не отказ. Держать в голове при обновлении Mixin/ASM: если начнут молча не
применяться, симптом — «мод грузится, но фича мертва без единой ошибки».
`[x]` Мелочи: `TinyTNTBlock.onCaughtFire` (огонь не поджигает мини-ТНТ),
`SkyStoneChestRenderer.getRenderBoundingBox` (сундук моргает на краю фрустума), пайплайны
`LINES_BEHIND_BLOCK`/`STORAGE_CELL_LEDS` без depth-state, `InterModComms` в никуда, пустой экран
конфига, нет entrypoint ModMenu, мост `Item.ITEM` мёртв с обеих сторон.
Закрыто отдельным заходом: `ModConfigEvent.Reloading` постится (+ команда `/ae2config reload`,
файловый вотчер **не** делаем — как в NeoForge вне dev), `RecipeSync.CLIENT_BUFFER` чистится на
disconnect, `ServerThread` сбрасывается на `SERVER_STOPPED`, `FakePlayerFactory` кэширует
`(уровень, профиль)` и чистится на выгрузке/остановке.
Проверено в игре по логу: 4 сессии (`test` -> `test2` -> ...), каждая остановка идёт
`Saving players` -> `Saving worlds` -> чанки всех 4 измерений (включая `ae2:spatial_storage`),
без исключений; новый мир поднимается за 300-900 мс, AE2 заново раздаёт ME player id и гоняет
payload'ы — то есть после смены мира `isServer` и кэш фейкплееров не врут.
`[ ]` Удалить временное перед релизом: `/nfcompat cap|parts|pp|provider`, мёртвые `ofSlot()`.

## Позже: разложить код на три слоя (идея пользователя)

Сейчас у нас две пачки: `compat/java/net/neoforged/**` (NeoForge-совместимый API, под который
компилируется AE2) и `compat/java/nfcompat/**` (наша склейка). Проблема: в `net/neoforged/**`
уже есть прямые вызовы Fabric (`BlockCapability` → `FabricCapabilities`, `ItemCapability` →
`FabricItemCapabilities`), то есть «извлекаемый шим» протёк в сторону Fabric.

Предлагаемая раскладка:

- `net/neoforged/**` — **только** NeoForge-API. Обращений к Fabric минимум и только через
  **закрытый список** бриджей в `fabric/**`: `FabricCapabilities`, `FabricItemCapabilities`,
  `FabricEnergy`, `ModelDataLookup`. Никаких «SPI», реестров провайдеров и reflection — список
  фиксированный, проверяется грепом: если в `net/neoforged/**` появился `import net.fabricmc.*`,
  кроме этих четырёх классов, тест в `shimTest` падает.
  Если однажды понадобится вынимать `net/neoforged` в отдельный артефакт — тогда и только тогда
  вводим один статический хук (`static volatile Hook`, сеттер), а не абстракцию заранее.
- `compat/**` — **шов** между Fabric и NeoForge: entrypoints, постинг событий, миксины, реестры.
- `fabric/**` — **Fabric-специфика**: адаптеры `ItemStorage`/`FluidStorage`/`EnergyStorage`,
  `UseBlockCallback`-диспетчеры, `ModelLoadingPlugin`, датаген-вход.

Правила зависимостей: `net.neoforged` не знает про Fabric; `compat` знает про оба; `fabric`
знает про Fabric + ваниль + типы `net.neoforged`.
Проверка: grep/AST-тест «в `net/neoforged/**` нет `net.fabricmc`» в `shimTest`.

Цена: переименование пакетов `nfcompat.*` → `compat.*`/`fabric.*` заденет ссылки в `src/`,
которые лежат в `//#if FABRIC`-блоках (`nfcompat.ServerThread`, `nfcompat.bridge.FluidTint` и т.п.) —
правки механические, но их надо делать одним заходом, когда все лейны молчат.
Пока не начинаем: сначала добить функционал, потом рефакторинг.

## Правило: сначала искать существующее, шим = адаптер

Урок (дорого выученный на gametest): прежде чем писать реализацию NeoForge-API, надо проверить,
нет ли уже готового **в ванили** или в Fabric API. Пример: в 26.3 геймтесты — это ванильные
реестры `TEST_ENVIRONMENT`/`TEST_INSTANCE`/`TEST_FUNCTION`, а AE2 уже пишет под них
(`GameTestPlotAdapter extends GameTestInstance`). NeoForge добавляет от себя только пост
`RegisterGameTestsEvent` из `RegistryDataLoader.load(...)`. Мы же чуть не начали писать
свой тестовый харнесс и портировать FML.

Порядок действий для любого NeoForge-API:
1. `javap` по ванильному jar — может, класс/реестр/хук уже есть;
2. Fabric API — может, есть готовый колбэк/лукап;
3. если есть — шим = **тонкий адаптер** (как `LifecycleBridge`, `ClientEventBridge`,
   `UseBlockCallback`-диспетчеры, `FabricCapabilities`);
4. только если аналога нет — писать реализацию (как `ModelData`, `QuadCollection`-мосты, `EventBus`).

`[ ]` Отдельная задача: пройтись по `compat/java/net/neoforged/**` и найти классы, которые
дублируют то, что уже есть в ванили/Fabric API (кандидаты: `DeferredRegister`, `SavedData`-подобное,
`GameTestHooks`, `ModelData`, `TagKey`-хелперы, `StructureTemplate`-обёртки). Для каждого —
вердикт «адаптер / реализация / не нужно вовсе».

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

## Итог сессии 17.11 (клиент + гайд)

Клиент запускается, гайд работает целиком (страницы, спрайты, 3D-сцены, хоткей G, иконки жидкостей), spatial IO и подсветка области проверены, экран настроек на Cloth Config работает (Mod Menu), TOML читается/пишется. Геймтесты 81/81, shimTest зелёный. Открыто: onChunkUnloaded, PiP-иконки жидкостей в JEI, обводка деталей, наковальня, растяжение сцены в момент ресайза окна (транзиент).