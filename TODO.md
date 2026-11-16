# TODO: где шим ещё тонкий

Список дыр NeoForge-шима, отсортирован по «насколько больно будет в игре». Ведём здесь,
чтобы не искать по чатам. Статусы: `[ ]` не сделано, `[~]` в работе, `[x]` сделано и проверено.

## 1. Наружу из AE2 ничего не выставляется
`[ ]` Мы научили шим брать чужие `ItemStorage`/`FluidStorage` (inbound), но обратной
регистрации нет: в `compat/java` ни одного `*.SIDED.register*`.
Ломается: воронка/труба другого мода не вставят в ME-сундук/интерфейс/ячейку; storage bus
и интерфейс снаружи выглядят пустыми.
Тест: воронка над ME-сундуком; труба в интерфейс.
Где: по образцу энергетики — `BlockCapability.providerBlocks()` + `registerForBlocks`
(см. `nfcompat/bridge/EnergyBridge.registerOutbound`).

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
`[ ]` Своё измерение, spatial IO, portal, cross-dimension телепорт блоков. Часть инфраструктуры
(тики, выгрузка чанков, ticket controllers) смостена в `LifecycleBridge`, но сам spatial —
самый экзотический путь, в игре не проверялся.
Тест: spatial IO port + storage cell + spatial pylon, сохранить/загрузить область.

## 5. Инструменты на неофорджевских патчах MC
`[ ]` `Player#getDestroySpeed` — скайстоун копается ~×50 медленнее (известный гэп).
`[ ]` `LivingEntity#onClimbable` — по кабелю нельзя лазать.
Тест: кирка по скайстоуну (замер времени); зажать прыжок на кабеле.

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
