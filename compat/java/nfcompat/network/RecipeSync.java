/*
 * Часть nfcompat. Синхронизация рецептов на клиент.
 *
 * ПОЧЕМУ ЭТО НУЖНО
 * NeoForge патчит ванильный пакет рецептов так, что клиент получает полную карту
 * рецептов, и постит `RecipesReceivedEvent`. AE2 этим пользуется: `AppEngClient`
 * складывает карту в `recipeMap` и отдаёт её через `getRecipeMapForType`.
 *
 * В ванили 26.3 клиент рецептов НЕ получает вообще: `ClientLevel.recipeAccess()` —
 * это `ClientRecipeContainer` без списка (только property sets и каменотёс). Из-за
 * этого у нас на клиенте не работали:
 *   - автоподстановка результата в терминале кодирования (рецепт не находится);
 *   - кодирование крафт-рецепта (клиент не видит рецепт -> режим уезжает в processing);
 *   - подстановка рецепта из JEI (JEI-плагин AE2 берёт карту у клиента).
 *
 * Поэтому сервер при входе игрока (и после перезагрузки датапаков) отправляет карту
 * рецептов пакетами, а клиент собирает её обратно и постит то же событие, что NeoForge.
 *
 * Формат пакета — ванильный `RecipeHolder.STREAM_CODEC`, поэтому рецепты едут со всеми
 * компонентами/тегами и не требуют своего формата.
 */
package nfcompat.network;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

import nfcompat.recipes.RecipeMaps;

public final class RecipeSync {
    /** Сколько рецептов кладём в один пакет: держим размер заведомо ниже любых лимитов. */
    private static final int BATCH_SIZE = 100;

    /** Собираемый на клиенте список: приходит несколькими пакетами. */
    private static final List<RecipeHolder<?>> CLIENT_BUFFER = new ArrayList<>();

    private static boolean installed;

    private RecipeSync() {
    }

    /** Идемпотентно: вызывается и с клиентского, и с серверного entrypoint. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        PayloadTypeRegistry.clientboundPlay().register(RecipesSyncPayload.TYPE, RecipesSyncPayload.CODEC);

        // Сервер: при входе игрока и после перезагрузки датапаков отдаём карту целиком.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTo(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                for (var player : server.getPlayerList().getPlayers()) {
                    sendTo(player);
                }
            }
        });

        // Клиент: приёмник живёт в отдельном классе, чтобы на выделенном сервере
        // клиентские классы Fabric вообще не загружались.
        if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            nfcompat.network.RecipeSyncClient.install();
        }
    }

    public static void sendTo(ServerPlayer player) {
        var server = player.level().getServer();
        if (server == null) {
            return;
        }
        var all = new ArrayList<>(RecipeMaps.of(server.getRecipeManager()).values());

        if (all.isEmpty()) {
            Payloads.send(player, new RecipesSyncPayload(List.of(), true));
            return;
        }

        for (int start = 0; start < all.size(); start += BATCH_SIZE) {
            int end = Math.min(all.size(), start + BATCH_SIZE);
            boolean last = end == all.size();
            Payloads.send(player, new RecipesSyncPayload(new ArrayList<>(all.subList(start, end)), last));
        }
    }

    /**
     * Сброс недособранного клиентского буфера.
     *
     * Мир покинули или переключились на другой сервер — недоехавшие пакеты первого
     * сервера не должны склеиться с картой второго. Вызывается из клиентского
     * приёмника на Fabric-евенте disconnect (см. {@link RecipeSyncClient}).
     */
    static void clearClientBuffer() {
        CLIENT_BUFFER.clear();
    }

    /** Клиентская сторона: накопить пакеты и собрать карту. */
    static void receive(RecipesSyncPayload payload) {
        CLIENT_BUFFER.addAll(payload.recipes());
        if (!payload.last()) {
            return;
        }

        var recipes = List.copyOf(CLIENT_BUFFER);
        CLIENT_BUFFER.clear();

        var recipeMap = RecipeMaps.createFrom(recipes);
        var types = new java.util.HashSet<net.minecraft.world.item.crafting.RecipeType<?>>();
        for (var holder : recipes) {
            types.add(holder.value().getType());
        }

        net.neoforged.neoforge.common.NeoForge.EVENT_BUS
                .post(new net.neoforged.neoforge.client.event.RecipesReceivedEvent(types, recipeMap));
    }
}
