/*
 * Часть nfcompat. Перезагрузка конфигов без перезапуска клиента.
 *
 * На NeoForge правка toml на диске подхватывается файловым вотчером: загрузчик
 * перечитывает конфиг и рассылает `ModConfigEvent.Reloading`. Вотчера у нас нет
 * (не хотим держать поток на каждый запуск ради редкой операции), поэтому
 * перечитывание явное:
 *
 *   /ae2config reload
 *
 * Команда перечитывает конфиги всех контейнеров (`ModContainer.reloadAllConfigs`):
 * и ae2-client/ae2-common, и guideme.toml. Значения с restart-типом NONE после
 * этого сразу видны через `ConfigValue#get()`, WORLD/GAME — только после
 * перезахода в мир/игру (кэш таких значений не сбрасывается, как и в NeoForge).
 *
 * HARD: это не `/reload` и не перезагрузка датапаков — конфиг-файлы не ресурсы,
 * Fabric их не трогает.
 */
package nfcompat.config;

import com.mojang.brigadier.CommandDispatcher;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;

public final class ConfigReloadCommand {

    private ConfigReloadCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ae2config")
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource()))));
    }

    private static int reload(CommandSourceStack source) {
        int count;
        try {
            count = ModContainer.reloadAllConfigs();
        } catch (RuntimeException e) {
            // Битый toml: показываем причину игроку, а не только в лог.
            source.sendFailure(Component.literal("[nfcompat] конфиги не перечитаны: " + e.getMessage()));
            return 0;
        }

        // Один и тот же текст и в чат, и в лог: конфиг правят руками, полезно видеть
        // в логе, что перечитывание действительно было.
        String message = "[nfcompat] конфигов перечитано: " + count;
        source.sendSuccess(() -> Component.literal(message), false);
        System.out.println(message);
        return count;
    }
}
