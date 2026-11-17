/*
 * Часть nfcompat. Колбэки реестров.
 *
 * В NeoForge `Registry` расширяется `IRegistryExtension` с методом `addCallback`:
 * колбэк срабатывает, когда реестр <запечён> (то есть после всех регистраций).
 * У нас свой `Registry`, поэтому эмулируем: `onBake` запоминает колбэк, а
 * `bakeAll` прогоняет их после того, как все `RegisterEvent` AE2 отработали.
 *
 * ПРОСТО ВЫЗВАТЬ СРАЗУ НЕЛЬЗЯ: AE2 регистрирует реестр типов ключей в
 * `NewRegistryEvent`, а сами типы - позже, в `RegisterEvent(AEKeyType.REGISTRY_KEY)`.
 * При немедленном вызове колбэк видит пустой реестр и навсегда фиксирует
 * `AEKeyTypes.getAll()` как пустой набор, из-за чего в терминале отсекается
 * каждая запись сетки.
 */
package nfcompat.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.Registry;

public final class RegistryCallbacks {
    private static final List<Runnable> PENDING = new ArrayList<>();

    private RegistryCallbacks() {
    }

    public static <T> void onBake(Registry<T> registry, Consumer<Registry<T>> callback) {
        PENDING.add(() -> callback.accept(registry));
    }

    /**
     * Прогоняет отложенные колбэки. Вызывать один раз - после завершения всех
     * регистраций AE2 (см. {@code NfCompat.runRegistrationPhases}).
     */
    public static void bakeAll() {
        // Значения, зарегистрированные после ванильного freeze(), ещё не привязаны к своим
        // holder'ам - а AEKeyTypesInternal читает реестр через holder. См. RegistryBinding.
        RegistryBinding.bindAllValues();

        // В NeoForge реестры мода замораживаются сразу после всех регистраций. Теперь наш
        // реестр лежит в реестре реестров (см. ModRegistries), то есть попадает в RegistryAccess,
        // а ваниль считает такие реестры "загруженными": TagNetworkSerialization зовёт
        // getTags() (у незамороженного - "Tags not bound"), TagLoader - prepareTagReload
        // ("Invalid method used for tag loading"). Поэтому морозим здесь, когда регистрация
        // уже закончена, но сервер ещё не стартовал.
        for (var registry : net.neoforged.neoforge.registries.NewRegistryEvent.getCreatedRegistries()) {
            try {
                registry.freeze();
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Could not freeze modded registry {}", registry.key().identifier(), e);
            }
        }

        var pending = new ArrayList<>(PENDING);
        PENDING.clear();
        for (var runnable : pending) {
            runnable.run();
        }
    }
}
