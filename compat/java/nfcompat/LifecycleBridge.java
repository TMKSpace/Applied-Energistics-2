/*
 * Часть nfcompat. Рантайм-события, которые на NeoForge рассылает загрузчик.
 *
 * Регистрационные события (реестры, capability, payloads) уже рассылает NfCompat,
 * но всё, что происходит ПОСЛЕ старта, на Fabric никто не постит. Без этого AE2
 * не тикает: очередь блок-сущностей не разгребается, grid-узлы не создаются,
 * EnergyService не пересчитывает питание — контроллер навсегда offline, а
 * терминалы не открывают экраны (меню создаётся, но содержимое не работает).
 *
 * Здесь же решается вторая проблема: NeoForge сам создаёт ThreadGroup серверных
 * потоков, а `appeng.util.Platform.assertServerThread()` сравнивает поток с
 * ThreadGroup, скопированным при инициализации класса. На Fabric группа серверного
 * потока — обычная, поэтому при первом же создании grid'а (TickHandler.addNetwork)
 * прилетал бы UnsupportedOperationException. Группу подхватываем на первом тике —
 * он гарантированно идёт на серверном потоке (в одиночной игре тоже).
 */
package nfcompat;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.neoforged.fml.util.thread.SidedThreadGroups;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class LifecycleBridge {
    private static boolean installed;
    private static ThreadGroup adoptedGroup;

    private LifecycleBridge() {
    }

    /** Идемпотентно: вызывается из клиентской и из серверной точки входа. */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        // --- Синхронизация рецептов на клиент ---
        // Ваниль 26.3 больше не отправляет клиенту рецепты, а AE2 на клиенте их ждёт
        // (терминал кодирования, JEI-подстановка). NeoForge делает это патчем пакета.
        nfcompat.network.RecipeSync.install();

        // --- Item#onItemUseFirst / doesSneakBypassUse ---
        // Аналог неофорджевского вызова хука предмета из патченного useItemOn. Регистрируется
        // ДО RightClickBlockBridge: Fabric перебирает хэндлеры UseBlockCallback в порядке
        // регистрации, а NeoForge тоже сначала даёт шанс предмету, и только потом блоку.
        nfcompat.bridge.ItemUseFirstBridge.install();

        // --- Правая кнопка по блоку ---
        // Аналог неофорджевского PlayerInteractEvent.RightClickBlock: UseBlockCallback из Fabric API
        // стреляет и на клиенте, и на сервере, и умеет отменять ванильную обработку. Нужен
        // appeng.hooks.WrenchHook (поворот/разбор гаечным ключом).
        nfcompat.bridge.RightClickBlockBridge.install();

        // ВРЕМЕННО: команда-проба моста capability -> Fabric Transfer API.
        // Удалить, когда шины/провайдер будут проверены в бою.
        nfcompat.debug.CapabilityProbeCommand.register();

        // --- Перезагрузка конфигов (/ae2config reload) ---
        // Аналог неофорджевского файлового вотчера: у нас перечитывание явное,
        // иначе правки в config/*.toml подхватывались бы только с перезапуском.
        nfcompat.config.ConfigReloadCommand.register();

        // --- Тики сервера ---
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            adoptServerThread();
            NeoForge.EVENT_BUS.post(new ServerTickEvent.Pre(() -> true, server));
        });
        ServerTickEvents.END_SERVER_TICK.register(server ->
                NeoForge.EVENT_BUS.post(new ServerTickEvent.Post(() -> true, server)));

        // --- Тики измерения: NeoForge рассылает их по одному Level ---
        ServerTickEvents.START_LEVEL_TICK.register(level -> {
            adoptServerThread();
            // Отложенная валидация тикетов: на момент LOAD чанков ещё нет, блок-сущности
            // якорей не создать, поэтому коллбэк (AE2 сверяет реальность якорей) крутим
            // на первом тике, когда чанки уже подгружены.
            TicketController.runDeferredValidation(level);
            NeoForge.EVENT_BUS.post(new LevelTickEvent.Pre(() -> true, level));
        });
        ServerTickEvents.END_LEVEL_TICK.register(level ->
                NeoForge.EVENT_BUS.post(new LevelTickEvent.Post(() -> true, level)));

        // --- Жизненный цикл сервера ---
        // SERVER_STARTING в одиночной игре тоже приходит, поэтому текущий сервер
        // выставляем здесь, а не только в dedicated-энтрипоинте.
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerLifecycleHooks.setCurrentServer(server);
            NeoForge.EVENT_BUS.post(new ServerAboutToStartEvent(server));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                NeoForge.EVENT_BUS.post(new ServerStartedEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                NeoForge.EVENT_BUS.post(new ServerStoppingEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            NeoForge.EVENT_BUS.post(new ServerStoppedEvent(server));
            ServerLifecycleHooks.setCurrentServer(null);
        });

        // --- Загрузка/выгрузка измерения и чанков ---
        // LOAD: NeoForge постит LevelEvent.Load при создании уровня. Заодно восстанавливаем
        // форс-лоады, сохранённые владельцами тикетов (spatial anchor), и планируем
        // валидацию на первый тик измерения.
        ServerLevelEvents.LOAD.register((server, level) -> {
            NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
            for (var controller : RegisterTicketControllersEvent.getRegisteredControllers()) {
                controller.validateTickets(level);
            }
        });
        // Выгрузка измерения и чанков (уборка очередей и grid-узлов).
        ServerLevelEvents.UNLOAD.register((server, level) -> {
            TicketController.clearDeferredValidation(level);
            NeoForge.EVENT_BUS.post(new LevelEvent.Unload(level));
        });
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newChunk) ->
                NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, newChunk)));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk)));

        // --- Команды AE2 (/ae2 ...) ---
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, environment) ->
                NeoForge.EVENT_BUS.post(new RegisterCommandsEvent(dispatcher, environment, buildContext)));
    }

    /**
     * Подхватывает серверный поток.
     *
     * NeoForge держит серверные потоки в отдельной ThreadGroup, и AE2 сравнивает группу
     * (appeng.util.Platform.isServer/isClient/assertServerThread). На Fabric в одиночной
     * игре серверный поток живёт в той же группе, что и рендер-поток, поэтому вместо
     * группы запоминаем сам поток (см. nfcompat.ServerThread) — там же и сравнение.
     */
    private static void adoptServerThread() {
        nfcompat.ServerThread.adopt();
    }
}
