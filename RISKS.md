# RISKS: что может сломаться и что мы с этим делаем

Один список вместо «в отчёте фиксера написан риск, и все его забыли».
Правило: **каждый риск, о котором пишет фиксер, обязан лежать либо комментарием в коде на
месте, либо строкой здесь.** Иначе это не риск, а болтовня.

Типы:
- **fragile** — держится на именах/сигнатурах ванили или чужого кода; при обновлении сломается
  (обычно громко: `require = 1`, компиляция или старт).
- **divergence** — сознательное расхождение с апстримом/NeoForge. Пересматривать только осознанно.
- **unknown** — требует проверки в игре, кода не касается.
- **noise** — выглядит страшно в логе, но не баг. Оставлено, чтобы не искать заново.
- **debt** — код-долг, который хочется убрать, но не блокирует.

Триггеры пересмотра: **MC** — обновление Minecraft; **MIXIN** — обновление Mixin/MixinExtras;
**NEOSRC** — обновление исходников NeoForge (сверка API); **ADDON** — порт конкретного аддона;
**REBASE** — синк с апстримом AE2; **RELEASE** — перед релизом.

## Fragile (сломается при обновлении, но громко)

| # | Риск | Где | Действие | Триггер |
|---|---|---|---|---|
| F1 | Инжекты по имени метода: `LevelChunk#clearAllBlockEntities`, `EnderDragon#checkWalls`, `WitherBoss#customServerAiStep`, `BlockBehaviour#onExplosionHit`, `LevelExtractor#extractBlockOutline`, `LevelRenderer#submitBlockOutline`, `LivingEntity#completeUsingItem` | `compat/java/nfcompat/mixin/**` | ничего; при MC-обновлении смотреть `InvalidInjectionException` на старте | MC |
| F2 | `@Local`-захваты (визер/дракон/обводка) держатся на LVT | `mixin/common/{WitherBoss,EnderDragon}*`, `mixin/client/LevelExtractorBlockOutlineMixin` | при MC-обновлении: если локаль стала неоднозначной — `@Local(index = ...)` | MC |
| F3 | MixinExtras: `Operation` обязан быть последним, сахар — хвостовой; `@WrapOperation` + `@Local` вместе не работают | любые новые миксины | писать `@ModifyExpressionValue`/`@WrapOperation` без сахара; помнить про `non-trailing sugared parameters` | MIXIN |
| F4 | Разморозка `MappedRegistry.frozen` через reflection и перевязка holder'ов | `nfcompat/registry/ModRegistries.java` | если ваниль переименует поле — падение на старте, чинится одной строкой | MC |
| F5 | `BlockState.CODEC` сменил формат в 26.3 (`id` вместо `Name`) | `nfcompat/util/LegacyBlockStateCodec` + гейт в `FacadeContainer` | кодек терпим к обоим; при MC-обновлении проверить, что старый формат ещё нужен | MC |
| F6 | Пиксельные имена ванильных полей/методов в шиме (`GuiGraphicsExtractor.scissorStack`, `guiRenderState`, `LevelTicks`, `ChunkMap`) | `compat/java/nfcompat/**` | при MC-обновлении — компиляция ловит сразу | MC |
| F7 | Скан `@EventBusSubscriber` читает class-файлы всех модов: имена методов/аннотаций чужие | `nfcompat/event/AutoEventSubscriber`, `nfcompat/scan/ClassFileWalker` | при смене формата аннотаций NeoForge (`FancyModLoader`) — сверить `value/modid` | NEOSRC |
| F8 | Скан не заходит в jar-in-jar (zipfs) — аддон, упакованный так, не подпишется | `ClassFileWalker` | при жалобе «мой хендлер не вызывается» — проверить, не JiJ ли это | ADDON |
| F9 | `Class.forName(..., false)` при скане может дёрнуть линковку клиентских/datagen-классов на сервере | `AutoEventSubscriber` | ошибка ловится per-class (строка «не подписано» в логе); если шумит — фильтровать по пакетам | ADDON |

## Divergence (сознательно отличаемся)

| # | Расхождение | Где | Почему | Триггер |
|---|---|---|---|---|
| D1 | `CachedPlane#addTick` прибавляет смещение плана | гейт в `src/main/java/appeng/spatial/CachedPlane.java` | апстрим планирует тик по координатам плана → `LevelTicks` выбрасывает его. На NeoForge баг остаётся | REBASE (не «унифицировать» обратно) |
| D2 | `PlayerTickEvent` группируется по тику (`Pre1 Pre2 … Post1 Post2`) | `LifecycleBridge` / `ClientEventBridge` | так работают Fabric-tick события; у NeoForge чередование по игрокам | ADDON, если аддону важен порядок |
| D3 | `EventBusSubscriber.bus` оставлен (в NeoForge 26.x его нет), `bus=MOD` уважается | `compat/java/net/neoforged/fml/common/EventBusSubscriber.java` | старые порты его писали; NeoForge выбирает шину по `IModBusEvent` | NEOSRC |
| D4 | Все мод-контейнеры используют один `NeoForge.EVENT_BUS` | `AutoEventSubscriber` | аддоны получат «чужие» lifecycle-события (например `FMLCommonSetupEvent` с контейнером AE2) | ADDON, если аддон на это завяжется |
| D5 | `receiveCanceled` фильтрует только `@SubscribeEvent`-хендлеры | `SimpleEventBus` | лямбды из `addListener(...)` не меняем, чтобы не сломать существующие мосты | MIXIN/ADDON |
| D6 | Классы с одним `@Mod` не подписываются | `AutoEventSubscriber` | так и в NeoForge 26.x (скан строго по `@EventBusSubscriber`) | NEOSRC |
| D7 | GuideME — бинарный джар, ассеты копируются, `/guideme export` мёртв | `build.gradle`, `compat/resources` | форк библиотеки не делали (решение) | RELEASE |
| D8 | Trinkets: слот-иконки не рисуются в 4.x, кастомный слот выключен, живём на `legs/belt` | `compat/resources/data/trinkets/**` | в 4.x `icon` не рендерится; вернуть = дописать `legs/terminal` в `entities/ae2_terminal.json` | обновление Trinkets |
| D9 | Curios-интеграция заменена мостом на Trinkets | `appeng/integration/modules/curios/TrinketsCuriosAccess` | Curios не существует на Fabric | обновление Trinkets |
| D10 | `AECapabilities` доступны через наши `BlockCapability`, но не через Fabric-lookup'ы | `nfcompat/bridge/*`, TODO | нативным Fabric-модам нужны отдельные lookup'ы (план есть) | RELEASE / ADDON |
| D11 | **Автоподписка `@EventBusSubscriber` выключена по умолчанию** (`-Dnfcompat.autoEventSubscriber=true` включает). Скан classpath'а стоил ~0.6 с на стенде; пока нет T1/T2, свои обработчики регистрируем явно (`EVENT_BUS.register(Class)`) | `nfcompat/event/AutoEventSubscriber` | полноценной поддержки NF-аддонов ещё нет; флаг убрать, когда T1/T2 будут сделаны | T1/T2 |
| D15 | **Конфиги AE2 целиком ведёт ForgeConfigAPIPort** (жёсткая зависимость, `depends` в fabric.mod.json): `ModConfig`/`IConfigSpec`/`ModConfigSpec` - его классы, свой шим конфига удалён. night-config тоже НЕ наш: FCP несёт `core/toml 3.8.4` как JiJ. Их `ConfigTracker` грузит non-SERVER конфиги **внутри `registerConfig`** и постит только свои `fuzs...ModConfigEvents`, поэтому наши `ModConfigEvent.Loading/Reloading` рассылает наш `ModContainer.pendingLoads` | `compat/java/net/neoforged/fml/ModContainer.java`, `src/main/java/appeng/core/AEConfig.java` (гейт `LOCAL`→`COMMON`), `build.gradle`, `compat/resources/fabric.mod.json` | FCP 26.3.1. Ломалось уже дважды: NoSuchMethodError `ModConfig$Type.extension()` (наши классы затеняли их) и NoSuchFieldError `WritingMode.REPLACE_ATOMIC` (наш старый night-config). Теперь коллизии нет; но если FCP сменит API/ JiJ-версию - проверять песочницей с FCP | FCP UPDATE |
| D14 | `SpatialStorageHelper#swapRegions` уведомлял соседей принимающего региона в **уровне-источнике** (`cSrc.getLevel()` вместо `cDst.getLevel()`): обновления уходили по dst-координатам в чужой мир, а реальные соседи не просыпались. Баг апстримный (есть и в 1.20.1-Fabric) | гейт в `src/main/java/appeng/spatial/SpatialStorageHelper.java` | наш фикс под `#if FABRIC`; стоит отправить апстриму (замена `cSrc` → `cDst`, API не меняется) | REBASE / upstream-PR |
| D13 | **Внутренняя шкала жидкостей = droplets** (81000/ведро), как в официальном 1.20.1-Fabric-порте: `AEFluidKey.AMOUNT_BUCKET/AMOUNT_BLOCK` (гейт `#if FABRIC`) и шимный `FluidType.BUCKET_VOLUME`. Конверсии mB↔droplets больше нет | `src/main/java/appeng/api/stacks/AEFluidKey.java`, `compat/.../fluids/FluidType.java`, `nfcompat/bridge/FluidUnits` (identity) | так устроен сам Fabric (Fabric Transfer, RebornCore `FluidValue` = ×81); апстрим это и предполагает (`AEKey` javadoc «1000 on Forge and 81000 on Fabric»). Плата: (а) сохранённые миры читаются в 81 раз меньше — нужна миграция или вайп; (б) аддоны с mB-литералами (`insert(water, 1000)`) видят 1/81 ведра, уже собранные аддоны держат 1000 инлайном (нужна пересборка); (в) одна `ResourceHandler.insert/extract`-операция ограничена int = 26 501 ведро | REBASE / ADDON |
| D12 | Клиентский `RenderLevelStageEvent`: постится только `AfterWeather`, остальные под-стадии объявлены, но не firing | `nfcompat/mixin/client/LevelRendererRenderStageMixin` | минимализм: аддонам (extendedae_plus) нужен только AfterWeather; остальное - по требованию | ADDON |

## Unknown (проверяется только в игре)

| # | Что | Как проверить | Статус |
|---|---|---|---|
| U1 | Масштаб жидкостей NeoForge 1000/ведро ↔ Fabric 81000 | ведро → бак → ячейка → терминал и обратно, сверить объёмы | не проверено |
| U2 | Item/fluid P2P между сетями; FE-P2P только pull | туннели в двух сетях | не проверено |
| U3 | Автокрафт через molecular assembler и инскрайбер/печь | прогнать шаблоны | **проверено 26.11** |
| U4 | 3D-иконка жидкости в JEI (PiP) | категория Transform: куб вместо плоского тайла | **проверено 26.11** |
| U5 | Обводка деталей кабеля (`ExtractBlockOutlineRenderStateEvent`) | навести на кабель с фасадом | **проверено 26.11** |
| U6 | Слот Trinkets мышью + хоткей терминала | положить терминал в `legs/belt`, нажать хоткей | **проверено 26.11** |
| U7 | `canEntityDestroy`: дракон/визер не ломают `matrix_frame` | коробка из рамки, `summon ender_dragon` | **проверено: рамка целая, кобл разлетелся** (пилоны/контроллер ест — так и в апстриме) |
| U8 | Spatial: форс чанков и данные тикетов переживают рестарт | `forceload query` после ребута | **проверено: `[0,0]` форснут** |
| U9 | Фасады из старых `{Name:...}` (гайд, старые шаблоны) | открыть гайд, страницы с фасадами; лог без `Serialization errors` | код есть, в игре не смотрели |
| U10 | Сложность `peaceful` и «Skipping Entity with id» | `server.properties difficulty=normal`, рестарт, лог чист | понято, стенд переведён |
| U11 | **Флейки геймтестов**: `ae2:fe_charger` иногда упирается в лимит 300 тиков (комментарий апстрима сам говорит про рандом зарядника). В изоляции проходит, в полном прогоне ловится ~1 из 5 | `gradlew runGameTest -PgtFilter=ae2:fe_charger` | известно; если начнёт падать часто - поднять `maxTicks` гейтом `//#if FABRIC` |

## Noise (не баги, чтобы не искать заново)

| # | Симптом | Разбор |
|---|---|---|
| N1 | `Skipping Entity with id minecraft:zombie` ×71 | `EntityType.create` вернул null из-за `notInPeaceful` и `difficulty=peaceful`; сообщение ванили врёт про «id» |
| N2 | `Exceeded time limit of 25 ms after processing 1 queued tick callbacks` | штатный бюджет `TickHandler`: весь spatial-переход в одном колбэке; «1 remain» не растёт |
| N3 | `Failed to fetch user properties` / Realms 401 | оффлайн dev-клиент |
| N4 | `failed to start HTTP bridge on 127.0.0.1:25599` | второй mcpfabric (клиент или сервер) не смог занять порт |
| N5 | `AmecsKeyModifiersEarlyInit ClassNotFoundException` | чужой мод прощупывает отсутствующий Amecs |
| N6 | `Requested post effect does not exist: minecraft:end_of_frame` | один запрос пост-эффекта; разобраться, чей (наш `RenderPipeline`-шим подозревается) |
| N7 | `handleAmbientOcclusionPerPart: null -> true` | одноразовая правка конфига NeoForgeClientConfig при первом старте |

## Debt (не блокирует, но хочется)

| # | Долг | Где | Триггер |
|---|---|---|---|
| T1 | `AECapabilities` наружу через Fabric-lookup'ы (для нативных Fabric-модов и аддонов) | план в TODO, образец — `InitApiLookup` из 1.20.1-порта | после аддон-лейнов |
| T2 | Публикация событий в fabric-style `Event`-объекты (см. TODO) | — | после T1 |
| T3 | Тинт `MutableQuad` → `BakedQuad` (индикаторы каналов белеют) | `compat/java/.../quad/*` | если станет заметно |
| T4 | Фасады без ambient occlusion | `FacadeBuilder` | если станет заметно |
| T5 | `Blitter.copy()` не копирует `blending`/`transform` | GuideME-шим | **закрыто**: `guideme.BlitterCopyMixin` переносит флаги после `copy()` |
| T6 | `GUI_TEXTURED_OPAQUE` = обычный `GUI_TEXTURED` | GuideME-шим | если понадобится настоящая непрозрачность |
| T7 | `debug.log`: Mixin не понимает class version 69 (Java 25) | сборка шима | MIXIN |
| T8 | Устаревший комментарий в `attachment/AttachmentType.java` | `compat` | **закрыто** (переписан в лейне addon-api) |
| T9 | `SpatialStorageHelper#swapRegions` применяет `updateNeighborsAt` к оверворлду с координатами spatial-измерения | `src/main/java/appeng/spatial/SpatialStorageHelper.java` | если всплывёт в игре |
| T10 | Аудит дублей шима (`compat/java/net/neoforged/**` vs ваниль/Fabric API) | — | после аддонов |
| T11 | Датаген не выгружает registry-записи: живём на апстримных JSON | `build.gradle` | **оставлено как есть** (решение 26.11): живём на апстримных данных |
| T12 | Дев-команды `/nfcompat cap|parts|pp|provider` в сборке | `nfcompat/debug/*` | **закрыто**: только по `-Dnfcompat.debug=true` |
| T13 | `PlayerTickEvent` порядок (см. D2) — если аддону нужен чередование | `LifecycleBridge` | ADDON |
| T14 | Input-API для аддонов: `KeyMapping` 6-арг конструктор с `IKeyConflictContext`/`KeyModifier`, `getKeyConflictContext/getKeyModifier/isActiveAndMatches` | `compat/java/net/neoforged/neoforge/client/**` | ADDON |
| T15 | `InputConstants.Type.KEYSYM` удалён в ванили 26.3 — аддоны, собранные под 26.1.2 (`extendedae_plus`), ломаются и на стоковом NeoForge; порт требует правки их исходников | — | ADDON (по факту порта) |
| T16 | Переименования 26.3, на которых спотыкаются аддоны: `LevelRenderer#collectPerFrameGizmos` -> `collectPerFrameRenderThreadGizmos` | — | ADDON |

Последнее обновление: 26.11 (сессия аддон-поверхности и логов).

## Проверка раздаточного джара на чистом сервере (26.11)

Подняли настоящий выделенный сервер с нуля (fabric-installer, MC 26.3, loader 0.20.1,
`.slim/release-test/server`, моды: наш джар + Fabric API + mcpfabric) и поймали два блокера,
которые **в dev не видны**:

1. `NoClassDefFoundError com/electronwill/nightconfig/...` — night-config был только
   `implementation` (в dev на classpath от Gradle). **Исправлено**: `bundle(...)` для
   `night-config:core` и `:toml`, в джаре теперь 162 файла `com/electronwill/**`.
2. `IllegalAccessError ParticleTypes -> SimpleParticleType.<init>` — `"accessWidener"` не был
   объявлен в `fabric.mod.json` (в dev Loom применяет AW сам). **Исправлено**: добавлен
   `"accessWidener": "ae2.accesswidener"`.

После правок: `Preparing level "world"` -> `Done (1.616s)`, ошибок нет. Конфиги на втором
старте читаются без «исправлений по спецификации» (секции `[powerRatios]` и т.д. на месте).
