/*
 * Часть nfcompat. Standalone-модели (модели, не привязанные к блоку или предмету).
 *
 * NeoForge патчит `ModelManager`, добавляя `getStandaloneModel(StandaloneModelKey)`, и
 * рассылает `ModelEvent.RegisterStandalone` из `ModelDiscovery`: моды кладут в карту
 * `UnbakedStandaloneModel`, загрузчик запекает их вместе с остальными моделями.
 *
 * На Fabric есть ровно тот же механизм, только называется по-другому:
 * `ModelLoadingPlugin.Context#addModel(ExtraModelKey, UnbakedExtraModel)`. Дополнительные
 * модели добавляются в корни `ModelDiscovery` (значит, их зависимости тоже загружаются)
 * и пекутся в общем `ModelBakery`, а результат складывается в `ModelManager`
 * (`FabricModelManager#getModel`), причём при каждом reload карта заменяется целиком.
 * Поэтому:
 *  - регистрацию делает `NfCompatClient` (постит событие AE2 в нашу карту);
 *  - `ModelLoadingPlugin`, который мы регистрируем ниже, на старте каждого reload
 *    отдаёт содержимое карты Fabric-у;
 *  - `get(...)` — это просто чтение запечённой модели из `ModelManager`, без ленивого
 *    запекания и без ручного `ModelBaker` (его снаружи и не достать: `ModelBakery`
 *    не отдаёт бейкер, он живёт только внутри `bakeModels`).
 *
 * Порядок постинга важен: `StorageCellModels` наполняется в
 * `InitializeClientRegistriesEvent`, т.е. событие standalone-моделей должно лететь
 * ПОСЛЕ него, но до первого reload ресурсов (клиентская инициализация мода).
 */
package nfcompat.client;

import java.util.IdentityHashMap;
import java.util.Map;

import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricModelManager;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedExtraModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ResolvableModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.model.standalone.UnbakedStandaloneModel;
import org.jetbrains.annotations.Nullable;

public final class StandaloneModels {

    /**
     * Карта, которую наполняет `ModelEvent.RegisterStandalone`. Ключи сравниваются по
     * идентичности (`StandaloneModelKey` этим и занимается), поэтому IdentityHashMap.
     */
    private static final Map<StandaloneModelKey<?>, UnbakedStandaloneModel<?>> MODELS = new IdentityHashMap<>();

    /** Соответствие ключа AE2 и ключа Fabric. Создаётся один раз на ключ и живёт между reload'ами. */
    private static final Map<StandaloneModelKey<?>, ExtraModelKey<?>> EXTRA_KEYS = new IdentityHashMap<>();

    private static boolean installed;

    private StandaloneModels() {
    }

    /**
     * Регистрирует Fabric-плагин загрузки моделей. Идемпотентно: повторный вызов (в т.ч.
     * из {@link #get}) ничего не делает. Вызывать при клиентской инициализации, до первого
     * reload ресурсов.
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        ModelLoadingPlugin.register(StandaloneModels::addModels);
    }

    /** Карта для `ModelEvent.RegisterStandalone`. */
    public static Map<StandaloneModelKey<?>, UnbakedStandaloneModel<?>> models() {
        return MODELS;
    }

    /**
     * {@return запечённая standalone-модель по ключу, либо null, если такой ключ не регистрировался}
     */
    @Nullable
    public static <T> T get(ModelManager manager, StandaloneModelKey<T> key) {
        if (manager == null) {
            return null;
        }
        var extraKey = extraKey(key);
        if (extraKey == null) {
            return null;
        }
        return manager(manager).getModel(extraKey);
    }

    // ------------------------------------------------------------------
    // Внутренности
    // ------------------------------------------------------------------

    /**
     * Вызывается Fabric-ом на старте каждого reload'а моделей, когда уже известны все
     * модификации реестра моделей, но запекание ещё не началось.
     */
    private static void addModels(ModelLoadingPlugin.Context context) {
        for (var entry : MODELS.entrySet()) {
            var extraKey = extraKey(entry.getKey());
            if (extraKey != null) {
                add(context, entry.getKey(), extraKey, entry.getValue());
            }
        }
        System.out.println("[nfcompat] standalone-моделей: " + MODELS.size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void add(ModelLoadingPlugin.Context context,
            StandaloneModelKey<?> key,
            ExtraModelKey<?> extraKey,
            UnbakedStandaloneModel<?> model) {
        ModelDebugName name = key::getName;
        context.addModel((ExtraModelKey) extraKey, new UnbakedExtraModel() {
            @Override
            public void resolveDependencies(ResolvableModel.Resolver resolver) {
                model.resolveDependencies(resolver);
            }

            @Override
            public Object bake(ModelBaker baker) {
                return model.bake(baker, name);
            }
        });
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static <T> ExtraModelKey<T> extraKey(StandaloneModelKey<T> key) {
        var extraKey = EXTRA_KEYS.get(key);
        if (extraKey == null && MODELS.containsKey(key)) {
            extraKey = ExtraModelKey.create(() -> "ae2:standalone/" + key.getName());
            EXTRA_KEYS.put(key, extraKey);
        }
        return (ExtraModelKey<T>) extraKey;
    }

    /** `ModelManager` получает `FabricModelManager` через миксин fabric-api. */
    private static FabricModelManager manager(ModelManager manager) {
        return (FabricModelManager) (Object) manager;
    }
}
