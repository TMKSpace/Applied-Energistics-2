/*
 * Часть nfcompat. Сборник клиентских хуков NeoForge.
 *
 * В NeoForge ClientHooks — большая свалка всего, что патчится на клиенте. AE2 нужен
 * ровно один метод: регистрация определений слоёв моделей сущностей до создания
 * EntityModelSet. Здесь он складывает их в карту, которую потом забирает
 * наша инициализация клиента.
 */
package net.neoforged.neoforge.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public final class ClientHooks {
    private static final Map<ModelLayerLocation, Supplier<LayerDefinition>> LAYER_DEFINITIONS = new LinkedHashMap<>();

    private ClientHooks() {
    }

    public static void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> supplier) {
        LAYER_DEFINITIONS.put(location, supplier);
    }

    public static Map<ModelLayerLocation, Supplier<LayerDefinition>> getLayerDefinitions() {
        return LAYER_DEFINITIONS;
    }
}
