/*
 * Часть nfcompat. Реестр кастомных рендереров окружения (небо / облака / погода).
 *
 * На NeoForge связь "измерение -> рендерер" задаётся атрибутами окружения биома
 * (neoforge:custom_{skybox,clouds,weather_effects}), которые лежат в реестре
 * environment_attribute. В ванильном реестре таких атрибутов нет, поэтому биом
 * с ними не загружается (см. вырезанный блок в InitBiomes под FABRIC).
 *
 * Здесь эта связь делается руками: мод регистрирует рендереры в
 * RegisterCustomEnvironmentEffectRendererEvent (карты ниже), а измерение
 * привязывает к id рендерера через bind(). Шим при этом ничего не знает про AE2.
 */
package nfcompat.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.CustomCloudsRenderer;
import net.neoforged.neoforge.client.CustomSkyboxRenderer;
import net.neoforged.neoforge.client.CustomWeatherEffectRenderer;

public final class EnvironmentRenderers {
    private static final Map<Identifier, CustomCloudsRenderer> CLOUDS = new ConcurrentHashMap<>();
    private static final Map<Identifier, CustomSkyboxRenderer> SKYBOX = new ConcurrentHashMap<>();
    private static final Map<Identifier, CustomWeatherEffectRenderer> WEATHER = new ConcurrentHashMap<>();

    /**
     * Измерение -> id рендерера. Один и тот же id используется для всех трёх видов эффектов, как на NeoForge
     * (там это три отдельных атрибута, но мод вправе указать один id во всех трёх).
     */
    private static final Map<ResourceKey<Level>, Identifier> BY_LEVEL = new ConcurrentHashMap<>();

    private EnvironmentRenderers() {
    }

    public static Map<Identifier, CustomCloudsRenderer> cloudRenderers() {
        return CLOUDS;
    }

    public static Map<Identifier, CustomSkyboxRenderer> skyboxRenderers() {
        return SKYBOX;
    }

    public static Map<Identifier, CustomWeatherEffectRenderer> weatherRenderers() {
        return WEATHER;
    }

    /**
     * Привязывает измерение к зарегистрированному id рендерера окружения. Вызывается модом на клиенте
     * (у AE2 — из AppEngClient вместе с регистрацией самих рендереров).
     */
    public static void bind(ResourceKey<Level> dimension, Identifier rendererId) {
        BY_LEVEL.put(dimension, rendererId);
    }

    @Nullable
    public static CustomSkyboxRenderer skybox(@Nullable ClientLevel level) {
        var id = rendererId(level);
        return id == null ? null : SKYBOX.get(id);
    }

    @Nullable
    public static CustomCloudsRenderer clouds(@Nullable ClientLevel level) {
        var id = rendererId(level);
        return id == null ? null : CLOUDS.get(id);
    }

    @Nullable
    public static CustomWeatherEffectRenderer weather(@Nullable ClientLevel level) {
        var id = rendererId(level);
        return id == null ? null : WEATHER.get(id);
    }

    @Nullable
    private static Identifier rendererId(@Nullable ClientLevel level) {
        if (level == null) {
            return null;
        }
        return BY_LEVEL.get(level.dimension());
    }
}
