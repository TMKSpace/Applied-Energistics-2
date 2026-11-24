
- Prefer running tests, building the project and finding compilation errors via the IDEs MCP server.
- When unit tests fail with "Components not bound yet" error, it means they need a fully initialized Minecraft (incl. data packs).
  Inject a MinecraftServer into the test constructor to set this up and add `@ExtendWith(EphemeralTestServerProvider.class)` to the test class.
- Game tests use a full game environment and are needed if a level is required for testing.
  Check classes in `appeng.server.testplots` for examples. To run game tests, run the `Gametests` IDE run configuration.
  If the exit code is 0, all tests passed.

## Референс: официальная Fabric-ветка AE2 1.20.1

Клон: **`D:\Code\java\applied-energistics-2-1.20.1-fabric`**
(`git clone --branch fabric/1.20.1 --depth 1 https://github.com/AppliedEnergistics/Applied-Energistics-2.git`,
артефакт `appliedenergistics2-fabric`, MC 1.20.1, Fabric API 0.88.1).

Это официальный порт AE2 на Fabric, то есть **готовые ответы на большинство вопросов «а как это
делается на Fabric»**. ПРАВИЛО: прежде чем писать миксин/шим-класс, сначала грепнуть здесь.

Что там уже есть (проверено):
- `src/main/java/appeng/init/InitApiLookup.java` — вынос AE2-ных lookup'ов наружу
  (`MEStorage`, `GenericInternalInventory`, `ICraftingMachine`, `ICrankable`, P2P, энерго-предметы,
  `PartApiLookup.addHostType`). База для пункта TODO «`AECapabilities` наружу» и для портов аддонов.
- `appeng/server/services/ChunkLoadState.java` + `mixins/chunkloading/ServerLevelMixin.java`
  (запрет снятия чужого форса) — альтернатива нашему `TicketOwnershipData`-подходу.
- `appeng/hooks/RenderBlockOutlineHook.java` — обводка деталей.
- `appeng/hotkeys/*`, `appeng/init/*` — регистрация контента без NeoForge.

Чего там НЕТ (и не надо искать): **Curios/Trinkets** — ни одного упоминания, аксессуары на
Fabric в 1.20.1 не поддерживались. Наш мост Curios→Trinkets — новая работа, референса нет.

Важно: ветка 1.20.1, API там старее (Fabric lookups вместо `AECapabilities`, другие имена
классов), поэтому копировать код «как есть» нельзя — берём подход и адаптируем под 26.3.

## Наше (nfcompat-порт), не апстрим

- **НИКОГДА не убивайте java-процессы игры или сервера.** Пользователь держит запущенный
  клиент/сервер, и `Stop-Process` по `fabric.dli.config` роняет его. Проверьте, запущено ли
  что-то (`Get-CimInstance Win32_Process -Filter "Name like '%java%'" | Where-Object { $_.CommandLine -like '*fabric.dli.config*' }`),
  и если да — **не убивайте процессы**, а просто собирайте; сборка/датаген сервер не задевают
  (он живёт в `D:\Servers\Minecraft` отдельным инстансом, наши `build/` и `runDatagen` — нет).
- **Сервер пользователя: `D:\Servers\Minecraft` — не ронять и не трогать.** Не убивать его
  процессы, не писать в его каталог (логи, конфиги, миры, моды), не запускать `runServer`
  на том же порту. Наш стенд — только `D:\Code\java\ae2-fabric-probe`.
- Сборка: `powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1 -Task classes -NoAnalyze`
  (в `.opencode/`, логи там же). Тесты шима: `-Task shimTest`.
- **Один писатель в дереве.** Параллельно можно запускать только read-only агентов (аудиты,
  разведка). Два пишущих лейна в одном рабочем дереве ломают друг другу `build/` и дают ложные
  падения/ложные «зелёные» прогоны. Если нужно два писателя — git worktree на каждого
  (`.slim/worktrees/<slug>`, отдельный `build/`), слияние через orchestrator.
- **Инкрементальный `compileJava` не замечает удаления файлов** в `compat/`: после `git rm`
  он может сказать `UP-TO-DATE`. Удаления проверять `-Task clean` → `-Task classes`.
- Правки в `src/` — только блоками `//#if FABRIC` … `//#else` … `//#endif`, апстрим не переписывать.
  Всё, что можно, живёт в `compat/java`.

