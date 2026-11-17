/*
 * Часть nfcompat. Свои реестры мода в ванильном реестре реестров.
 *
 * ПРОБЛЕМА. Кодеки, которые ходят через `RegistryAccess`, берут реестр не у мода, а у
 * `RegistryAccess`:
 *     `ByteBufCodecs.registry(AEKeyType.REGISTRY_KEY)` ->
 *     `input.registryAccess().lookupOrThrow(AEKeyType.REGISTRY_KEY)`
 * В 26.3 `RegistryAccess` сервера, клиента и юнит-тестов AE2 — это
 * `RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)`
 * (`RegistryLayer.STATIC_ACCESS`, `ClientRegistryLayer.STATIC_ACCESS`, а тест
 * `CodecTestUtil` строит его тем же вызовом). То есть внутри видно ровно то, что лежит
 * в реестре реестров. Наш реестр, созданный в `NewRegistryEvent`, лежал только в
 * `NewRegistryEvent.CREATED`, поэтому `lookupOrThrow` падал:
 *     java.lang.IllegalStateException: Missing registry: ResourceKey[minecraft:root / ae2:keytypes]
 *
 * РЕШЕНИЕ. Делаем как NeoForge: реестр, созданный модом, кладём в реестр реестров
 * (`BuiltInRegistries.REGISTRY`) под его собственным ключом. На NeoForge это происходит
 * до заморозки `BuiltInRegistries` в `NewRegistryEvent`, у нас ванильный bootstrap уже
 * прошёл — поэтому размораживаем реестр реестров руками и возвращаем флаг обратно.
 *
 * ЧТО ЭТО ДАЁТ. Все четыре метода `RegistryAccess` начинают видеть реестр мода
 * (`lookup`, `lookupOrThrow`, `registries`, `listRegistryKeys`), причём для любых
 * инстансов, собранных через `fromRegistryOfRegistries` — на сервере, на клиенте и в
 * юнит-тестах. Для `ImmutableRegistryAccess` (композит `LayeredRegistryAccess`) — тоже,
 * потому что он собирается из `registries()` слоёв, а STATIC-слой и есть
 * `fromRegistryOfRegistries`.
 *
 * ПОЧЕМУ НЕ МИКСИН В `RegistryAccess`. Юнит-тесты AE2 идут обычным JUnit'ом на classpath
 * (`build.gradle`, сорсет `test`), без Knot-загрузчика, поэтому микшины в тестах вообще
 * не применяются — миксин не починил бы падающие `AEKeyTypeTest#test*NetworkRoundtrip`.
 * Реестр реестров — единственная точка, которую ваниль опрашивает сама, без патчей.
 *
 * ЧЕГО НЕ ДЕЛАТЬ. Не пытаться звать `BuiltInRegistries.REGISTRY.freeze()` повторно после
 * разморозки: `MappedRegistry#freeze` падает с "Tags already present before freezing",
 * потому что теги уже связаны первым freeze(). Поэтому флаг `frozen` возвращается
 * напрямую, а holder нового реестра привязывается к значению вручную (см. RegistryBinding).
 */
package nfcompat.registry;

import java.lang.reflect.Field;

import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;

public final class ModRegistries {
    private static final Object LOCK = new Object();
    private static final Field FROZEN = frozenField();

    private ModRegistries() {
    }

    /**
     * Кладёт реестр мода в реестр реестров, чтобы его было видно через {@code RegistryAccess}.
     * Идемпотентно: повторный вызов для того же ключа ничего не делает.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void expose(ResourceKey<? extends Registry<?>> key, Registry<?> registry) {
        synchronized (LOCK) {
            var root = BuiltInRegistries.REGISTRY;
            var rootRegistry = (Registry) root;

            if (rootRegistry.containsKey((ResourceKey) key)) {
                return;
            }

            var wasFrozen = isFrozen(root);
            if (wasFrozen == null) {
                // Реестр реестров не MappedRegistry (или рефлексия недоступна) — разморозить нечем.
                LogUtils.getLogger().warn(
                        "Cannot expose registry {} to RegistryAccess: registry-of-registries is not a MappedRegistry",
                        key.identifier());
                return;
            }

            try {
                setFrozen(root, false);
                ((WritableRegistry) rootRegistry).register((ResourceKey) key, registry, RegistrationInfo.BUILT_IN);

                // register() оставляет holder непривязанным до freeze(): он создаётся через
                // Reference.createStandAlone. RegistryAccess читает значение через Holder#value,
                // без привязки это "Trying to access unbound value".
                var holder = ((HolderGetter) rootRegistry).get((ResourceKey) key);
                if (holder.isPresent()) {
                    RegistryBinding.bindValue((Holder.Reference<?>) holder.get(), registry);
                }
            } catch (RuntimeException e) {
                LogUtils.getLogger().warn("Cannot expose registry {} to RegistryAccess",
                        key.identifier(), e);
            } finally {
                // Возвращаем ровно то состояние, что было: повторный freeze() звать нельзя
                // (см. шапку файла).
                setFrozen(root, wasFrozen);
            }
        }
    }

    /** {@return true, если реестр уже лежит в реестре реестров} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static boolean isExposed(ResourceKey<? extends Registry<?>> key) {
        return ((Registry) BuiltInRegistries.REGISTRY).containsKey((ResourceKey) key);
    }

    /** {@return true, если реестр реестров уже заморожен, то есть ванильный bootstrap прошёл} */
    public static boolean isRootFrozen() {
        return Boolean.TRUE.equals(isFrozen(BuiltInRegistries.REGISTRY));
    }

    private static Boolean isFrozen(Object registry) {
        if (FROZEN == null || !(registry instanceof MappedRegistry<?>)) {
            return null;
        }
        try {
            return FROZEN.getBoolean(registry);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void setFrozen(Object registry, boolean frozen) {
        if (FROZEN == null) {
            return;
        }
        try {
            FROZEN.setBoolean(registry, frozen);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Не смогли вернуть флаг — реестр реестров останется размороженным.
        }
    }

    private static Field frozenField() {
        try {
            var field = MappedRegistry.class.getDeclaredField("frozen");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
