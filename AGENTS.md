
- Prefer running tests, building the project and finding compilation errors via the IDEs MCP server.
- When unit tests fail with "Components not bound yet" error, it means they need a fully initialized Minecraft (incl. data packs).
  Inject a MinecraftServer into the test constructor to set this up and add `@ExtendWith(EphemeralTestServerProvider.class)` to the test class.
- Game tests use a full game environment and are needed if a level is required for testing.
  Check classes in `appeng.server.testplots` for examples. To run game tests, run the `Gametests` IDE run configuration.
  If the exit code is 0, all tests passed.

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
- Правки в `src/` — только блоками `//#if FABRIC` … `//#else` … `//#endif`, апстрим не переписывать.
  Всё, что можно, живёт в `compat/java`.

