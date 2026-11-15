/*
 * Часть nfcompat. Данные, привязанные к render state.
 *
 * NeoForge патчит `LevelRenderState`, добавляя `setRenderData(ContextKey, value)` /
 * `getRenderDataOrDefault(...)`: так моды передают свои данные из фазы извлечения
 * состояния в фазу отрисовки. На Fabric этого нет, поэтому храним значения здесь
 * по слабой ссылке на сам state.
 *
 * Ограничения: данные живут, пока жив объект state; при пересоздании state их надо
 * положить заново (что AE2 и делает — пишет их каждый кадр).
 */
package nfcompat.client;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.context.ContextKey;

public final class RenderStateData {
    private static final Map<Object, Map<ContextKey<?>, Object>> VALUES = Collections
            .synchronizedMap(new WeakHashMap<>());

    private RenderStateData() {
    }

    public static <T> void set(Object state, ContextKey<T> key, T value) {
        synchronized (VALUES) {
            VALUES.computeIfAbsent(state, k -> new java.util.HashMap<>()).put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getOrDefault(Object state, ContextKey<T> key, T fallback) {
        synchronized (VALUES) {
            var map = VALUES.get(state);
            if (map != null) {
                var value = map.get(key);
                if (value != null) {
                    return (T) value;
                }
            }
        }
        return fallback;
    }
}
