/*
 * Часть nfcompat. Доступ к standalone-моделям.
 *
 * NeoForge патчит `ModelManager`, добавляя `getStandaloneModel(StandaloneModelKey)`:
 * моды регистрируют такие модели в `ModelEvent.RegisterStandalone`, и они пекутся
 * вместе с остальными. На Fabric этого механизма нет — возвращаем null, и вызывающие
 * обязаны это переживать (`Objects.requireNonNull` в AE2 упадёт).
 *
 * TODO: реализовать реестр standalone-моделей и запекание в нашем клиентском слое.
 */
package nfcompat.client;

import net.minecraft.client.resources.model.ModelManager;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jetbrains.annotations.Nullable;

public final class StandaloneModels {
    private StandaloneModels() {
    }

    @Nullable
    public static <T> T get(ModelManager manager, StandaloneModelKey<T> key) {
        return null;
    }
}
