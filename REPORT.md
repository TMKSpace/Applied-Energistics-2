# AE2 26.3 (NeoForge) → Fabric 26.3: отчёт по compile-probe

Дата: 2026-11-15. Проект: `D:\Code\java\ae2-fabric-probe` (одноразовый стенд, не порт).

## Что собрано

- Источник: `AppliedEnergistics/Applied-Energistics-2@main` (c35b18e, 2026-11-02) — это ровно тот код,
  что уходит в `26.3.1-alpha [NEOFORGE]`.
- Обвязка: `FabricMC/fabric-example-mod@26.3` → Loom **1.19.4**, MC **26.3**, loader **0.20.1**,
  Fabric API **0.166.2+26.3**, Java **25** (`jdk-25.0.2.10-hotspot`).
- Скопировано: `src/main/java` (1085 файлов), `src/client/java` (314), `src/main/resources`,
  `src/client/resources`, `src/generated/resources`. Тесты/JMH/buildtools не брались.
- Зависимости, которых AE2 ждёт на компиляции: `org.appliedenergistics:guideme:26.3.7-alpha`
  (есть на Maven Central, подтянулась как есть), snakeyaml, directory-watcher, flatbuffers-java.

## Что выяснилось по дороге (важное)

1. **MC 26.x не обфусцирован.** `loom.officialMojangMappings()` падает с
   `Cannot use Mojang mappings in a non-obfuscated environment`. Маппинги не нужны вообще:
   исходники AE2 (mojmap-имена) компилируются против jar'а MC напрямую. Ремапа нет.
2. **`splitEnvironmentSourceSets()` ломает AE2.** AE2 держит клиентский код в `main`
   (`appeng.client.*`) и лезет оттуда в `com.mojang.blaze3d`, GUI и рендер. На Fabric
   с разделёнными окружениями это даёт 3852 «пустых» ошибки (пакет blaze3d не существует).
   Лечится тем, что всё валим в один сорсет — проверено, ошибки уходят в ноль.
3. GuideME подтягивается без вопросов: артефакт на Central, зависимостей в POM нет.
4. Loom нормально переваривает MC 26.3 без маппингов; сборка конфигурируется за ~20 c,
   инкрементальный `compileJava` — 15 c.

## Цифры компиляции (один сорсет, без правок кода)

| | |
|---|---|
| Java-файлов на входе | 1399 |
| Уникальных ошибок (файл:строка:текст) | **5164** |
| Файлов с ошибками | **758** (54%) |
| Уникальных FQN из `net.neoforged` | **176** |
| Файлов с `import net.neoforged` | 185 |

### Категории

| шт. | что |
|---|---|
| 2372 | `cannot find symbol` (каскад от отсутствующих классов NeoForge) |
| 1020 | `package net.neoforged.* does not exist` |
| 628 | `method does not override or implement a method from a supertype` (реализации `ResourceHandler`, capabilities) |
| 366 | `package <Класс> does not exist` — тоже каскад (`NeoForge`, `Capabilities`, `Tags`, `ModelData`, `ModConfigSpec`) |
| 354 | отсутствующие third-party: jade, wthit, jei, rei, emi — лечится зависимостями/исключением интеграций |
| 194 | реальный дрейф ванильного API (`ItemStackTemplate` vs `ItemStack`, `Identifier` vs `String`, `SimpleParticleType` protected, вывод `SavedDataType`...) |
| 154 | `reference to of is ambiguous` (`ItemResource.of` / `FluidResource.of` перегрузки) |
| **76** | `private/protected access` → нужен accesswidener. У AE2 в NeoForge **31** запись в `accesstransformer.cfg` — конвертируется механически |

### Топ сообщений

```
2372  cannot find symbol
 628  method does not override or implement a method from a supertype
 152  reference to of is ambiguous
  98  package net.neoforged.neoforge.model.data does not exist
  96  package net.neoforged.neoforge.transfer does not exist
  76  package NeoForge does not exist
  74  package Tags does not exist
  70  package Capabilities does not exist
  70  package net.neoforged.neoforge.capabilities does not exist
  62  package net.neoforged.neoforge.transfer.item does not exist
  56  package net.neoforged.neoforge.transfer.transaction does not exist
  48  package snownee.jade.api does not exist
  48  package net.neoforged.neoforge.client.event does not exist
  ...
  32  items has private access in RecipeProvider
```

### Самые «грязные» файлы

```
111  appeng/core/AEConfig.java                  (ModConfigSpec)
 89  appeng/client/render/AERenderPipelines.java (ушло после отказа от split env)
 87  appeng/client/AppEngClient.java
 65  appeng/client/integrations/jei/JEIPlugin.java
 44  appeng/util/inv/AppEngInternalInventory.java
 42  appeng/api/inventories/InternalInventoryResourceHandler.java
 41  appeng/core/AppEngBase.java
 34  appeng/init/InitCapabilityProviders.java
```

## Поверхность шима: 176 классов NeoForge

Полный список — `shim-surface.txt`. По подпакетам:

```
49  net.neoforged.neoforge.client        (model.data, model.*, network, event, render)
28  net.neoforged.neoforge.common        (Tags, NeoForge, CommonHooks, FakePlayer, SoundActions)
21  net.neoforged.neoforge.transfer      (ResourceHandler, ItemResource, FluidResource, Transaction*, EnergyHandler)
18  net.neoforged.neoforge.event
13  net.neoforged.neoforge.registries    (DeferredRegister, ...)
 8  net.neoforged.neoforge.capabilities  (BlockCapability, BlockCapabilityCache, Capabilities)
 6  net.neoforged.neoforge.network       (PacketDistributor, NeoForgeStreamCodecs)
 6  net.neoforged.fml.event + fml.*      (ModList/ModContainer/ModLoader/ModWorkManager/InterModComms)
 4  net.neoforged.bus.api                (IEventBus, SubscribeEvent, EventPriority, Event)
```

Топ по числу файлов (в скобках — сколько файлов AE2 это импортируют):

```
39 ModelData            28 ResourceHandler       21 ClientPacketDistributor
29 ItemResource         16 TransactionContext    15 NeoForge (EVENT_BUS)
13 Capabilities         13 FluidResource         12 NeoForgeStreamCodecs
10 BlockCapability      10 ModelProperty         10 CustomUnbakedBlockStateModel
```

## Как это устроено в NeoForge (transfer layer)

`net/neoforged/neoforge/transfer` (ветка 26.3.x):

```
ResourceHandler.java          14 KB  — интерфейс: size(), getResource, getAmountAsLong,
                                        getCapacityAsLong, isValid, insert(index?, T, int, TransactionContext),
                                        extract(index?, T, int, TransactionContext)
ResourceHandlerUtil.java      23 KB  — move/moveItemStack/поиск по слотам, всё через транзакции
StacksResourceHandler.java    11 KB  — «списочный» реестр ресурсов
ResourceStacksResourceHandler 2 KB / CombinedResourceHandler, Delegating, Ranged, Voiding, Infinite, Empty
transfer/item/ItemResource.java       — предмет+компоненты, toStack(amount), of(ItemStack)
transfer/fluid/FluidResource.java     — то же для жидкостей
transfer/energy/EnergyHandler.java    — энергия как ResourceHandler<EnergyResource>
transfer/resource/Resource.java       — базовый ресурс (в т.ч. сериализация)
transfer/transaction/Transaction.java          11 KB  — коммит/роллбэк, вложенность
transfer/transaction/TransactionContext.java  1 KB   — то, что прокидывается в insert/extract
transfer/transaction/SnapshotJournal.java     10 KB  — база для транзакционного хендлера: updateSnapshots/revert
transfer/transaction/TransactionManager.java  5 KB   — корневая транзакция
```

Ключевое: **это тот же дизайн, что у Fabric Transfer API v1** (его писал Technici4n, NeoForge потом
эволюционировал копию). Fabric на 26.3 везёт `fabric-transfer-api-v1-8.0.27` со ровно теми же
понятиями:

| NeoForge | Fabric v1 |
|---|---|
| `ResourceHandler<T>` (+`size`, индексы, int-кол-ва) | `Storage<T>` / `SlottedStorage<T>` (long-кол-ва) |
| `ItemResource` / `FluidResource` | `ItemVariant` / `FluidVariant` (+ `long amount` рядом) |
| `Transaction`, `TransactionContext` | `Transaction`, `TransactionContext` |
| `SnapshotJournal<T>` | `SnapshotParticipant<T>` |
| `Capabilities.ItemHandler.BLOCK` / `BlockCapability<T,Ctx>` | `ItemStorage.SIDED` / `ApiLookup<T,Ctx>` |
| `Capabilities.BlockCapabilityCache` | `BlockApiCache` |
| `ResourceHandlerUtil.move(...)` | `StorageUtil.move(...)` |
| `TransferPreconditions` | `StoragePreconditions` |
| `DeferredRegister` | `Registry.register` в инициализаторе |
| `NeoForgeStreamCodecs` | ванильный `StreamCodec` / `CustomPayload` |
| `ClientPacketDistributor` | `ClientPlayNetworking.send` |
| `ModConfigSpec` | своего нет (Cloth Config / Owo / руками) |
| **`ModelData` + `ModelProperty` + `client.model.*`** | **аналога нет** |

AE2 пишет собственные реализации, например
`appeng/api/inventories/InternalInventoryResourceHandler extends SnapshotJournal<...> implements
ResourceHandler<ItemResource>, IndexModifier<ItemResource>` — то есть шим должен не только
принимать NeoForge-вызовы, но и позволять AE2-классам реализовывать этот интерфейс, а внутри
транслировать в Fabric `Storage`. Двусторонний мост транзакций (NF `TransactionContext` ⇄ Fabric
`TransactionContext`) — самая тонкая часть, но обе модели совместимы структурно.

## Разбивка по сложности

**A. Механика (ским шаблонный шим, ~3-5k строк):** transfer, capabilities, registries, bus/events,
network codecs, config, FakePlayer/CommonHooks, Tags. 176 классов, но 2/3 — интерфейсы и
перечисления в 5-30 строк. Для чистой компиляции хватит заглушек; для работы — только transfer,
capabilities и события нужно делать по-настоящему.

**B. Третьи стороны:** jade / wthit / jei / rei / emi. На Fabric у всех есть свои API; 354 ошибки
уходят либо подключением fabric-артефактов, либо исключением `appeng/integration/modules/*`
(именно так AE2 сам делает `exclude` для rei/theoneprobe в main). Не архитектурная проблема.

**C. Настоящая стена — рендер/модели:** `neoforge.model.data.ModelData` + `ModelProperty` +
`client.model.{CustomUnbakedBlockStateModel, DynamicBlockStateModel, MutableQuad, QuadTransforms,
StandaloneModelKey}` на **42+26 файлах**. Это система «модель зависит от данных блока/предмета»,
на которой висят кабели, фасады, части и их раскраска. У Fabric есть `ModelLoadingPlugin` и
`FabricBakedModel`, но нет ни `ModelData` на block entity, ни хука «динамическая модель по
состоянию». Это не шим в 200 строк — это свой мини-фреймворк поверх ванильных моделей, и его
надо будет писать с нуля и потом отлаживать глазами.

**D. Ванильный дрейф (194 ошибки):** `ItemStackTemplate`, `Identifier`→`String`, protected-конструкторы
частиц, `SavedDataType` — по мелочи, но по всему коду.

## Вывод по реалистичности (обновлённый)

- Компиляция «в ноль» через шим — **реально**, и это не месяцы: недели. Цифры выше показывают,
  что 74% ошибок — механическое отсутствие 176 классов NeoForge с очевидными Fabric-аналогами.
- «Работает и не падает» — упирается в пункт C. Пока `ModelData` не воспроизведён, AE2 будет
  компилироваться и падать на клиенте при первом кабеле.
- Порядок работ, который я бы предложил: (1) шим transfer+capabilities+registries+events,
  (2) accesswidener из 31 AT-записи, (3) добить оставшиеся ~200 ошибок ванильного дрейфа,
  (4) серверная часть уже запускается и тестируется геймтестами без рендера — это дешёвая
  проверка «логика ME-сети жива», (5) только потом рендер/модели как отдельный проект.

## Следующий шаг (если продолжаем)

Сгенерировать скелет `net.neoforged.*` шима (176 классов, тела — `throw new UnsupportedOperationException`
или трансляция в Fabric), подключить как отдельный модуль `ae2-nfcompat` и мерить остаток ошибок.
Это даёт точную цифру «сколько реальной работы», а не оценку.
