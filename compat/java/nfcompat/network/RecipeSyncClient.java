/*
 * Часть nfcompat. Клиентский приёмник синхронизации рецептов.
 *
 * Отдельный класс, чтобы на выделенном сервере Fabric не грузил клиентские
 * классы сетевого API (RecipeSync.install() загружает его только в клиентском окружении).
 */
package nfcompat.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class RecipeSyncClient {

    private RecipeSyncClient() {
    }

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(RecipesSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> RecipeSync.receive(payload)));

        // Выход из мира/смена сервера: недособранная карта чистится, иначе рецепты
        // прошлого мира доживают до следующей синхронизации и попадают в новую карту.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(RecipeSync::clearClientBuffer));
    }
}
