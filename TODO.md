# TODO: где шим ещё тонкий

Список дыр NeoForge-шима, отсортирован по «насколько больно будет в игре». Ведём здесь,
чтобы не искать по чатам. Статусы: `[ ]` не сделано, `[~]` в работе, `[x]` сделано и проверено.

## 1. Наружу из AE2 ничего не выставляется
`[x]` Сделано: `nfcompat/bridge/StorageBridge` регистрирует `ItemStorage.SIDED`/`FluidStorage.SIDED`
по `Capabilities.*.providerBlocks()` + `getCapabilityDirect`; адаптеры `ItemHandlerStorage`
(SlottedStorage) и переписанный `FluidHandlerStorage` (через `HandlerTransactions` + `FluidUnits`).
Сверено с 1.20.1 `InitApiLookup` — потерянных блоков нет.

## 2. Item-capabilities вообще не смостены
`[ ]` `Capabilities.Fluid.ITEM` и `Capabilities.Energy.ITEM` не имеют Fabric-моста. Сейчас
это уже стреляет: JEI-плагин падает на `registerP2PAttunement` (`Ingredient.of([])` —
`UnsupportedOperationException: Ingredients can't be empty`), потому что оба attunement-списка
(энергия и жидкости) пустые.
Ломается: вёдра/контейнеры в жидкостном терминале и интерфейсе, P2P-аттюнмент по предмету,
ghost-drag жидкости из JEI.
Тест: ведро воды в слот жидкостного терминала; R/U по жидкости; P2P-категории в JEI.

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
