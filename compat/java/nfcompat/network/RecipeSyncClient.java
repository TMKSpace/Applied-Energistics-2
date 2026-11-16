/*
 * Часть nfcompat. Клиентский приёмник синхронизации рецептов.
 *
 * Отдельный класс, чтобы на выделенном сервере Fabric не грузил клиентские
 * классы сетевого API (RecipeSync.install() загружает его только в клиентском окружении).
 */
package nfcompat.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class RecipeSyncClient {

    private RecipeSyncClient() {
    }

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(RecipesSyncPayload.TYPE, (payload, context) ->
                context.client().execute(() -> RecipeSync.receive(payload)));
    }
}
