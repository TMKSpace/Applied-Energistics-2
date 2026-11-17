/*
 * Шим nfcompat. Привязка значений к holder'ам.
 *
 * ПРОБЛЕМА. MappedRegistry связывает значение с Holder.Reference только в freeze().
 * В обычной игре это нормально: реестры морозятся один раз в конце регистрации, а до
 * этого никто не читает значения через holder. Но у нас в тестах (и в dev-рантайме на
 * Fabric, где реестры вообще не морозятся) регистрация идёт ПОСЛЕ ванильного bootstrap'а,
 * то есть после freeze(). Мы размораживаем реестры, регистрируем мод - и получаем
 * "Trying to access unbound value" на первом же Holder#value(): это ломает
 * NfCompat.runRegistrationPhases на bakeAll (AEKeyType читает свой реестр сразу после
 * RegisterEvent).
 *
 * РЕШЕНИЕ. Повторяем ту часть freeze(), которая привязывает значения: проходим по
 * MappedRegistry#byValue (значение -> holder) и зовём Holder.Reference#bindValue.
 * Компоненты и теги привяжет обычный freeze() в конце регистрации.
 */
package nfcompat.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

public final class RegistryBinding {
    private static final Field BY_VALUE = field("byValue");
    private static final Method BIND_VALUE = method("bindValue", Object.class);

    private RegistryBinding() {
    }

    /** Привязывает значения ко всем ещё не привязанным holder'ам ванильных и шимовых реестров. */
    public static void bindAllValues() {
        if (BY_VALUE == null || BIND_VALUE == null) {
            return;
        }
        for (var registry : BuiltInRegistries.REGISTRY) {
            if (registry instanceof MappedRegistry<?> mapped) {
                bind(mapped);
            }
        }
        // Реестры, созданные самим шимом (NewRegistryEvent). После ModRegistries#expose они
        // тоже лежат в BuiltInRegistries.REGISTRY, но список CREATED остаётся каноническим:
        // там реестр есть даже если разморозить реестр реестров не удалось.
        for (var registry : net.neoforged.neoforge.registries.NewRegistryEvent.getCreatedRegistries()) {
            if (registry instanceof MappedRegistry<?> mapped) {
                bind(mapped);
            }
        }
    }

    /**
     * Привязывает один holder к значению. Нужно сразу после {@code register}, когда реестр
     * ещё не заморожен: {@code MappedRegistry#register} создаёт holder через
     * {@code Reference.createStandAlone}, и без привязки любое чтение значения
     * ({@code Holder#value}) падает с "Trying to access unbound value".
     */
    public static void bindValue(Holder.Reference<?> holder, Object value) {
        if (BIND_VALUE == null || holder.isBound()) {
            return;
        }
        try {
            BIND_VALUE.invoke(holder, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Не смогли - привяжет финальный freeze().
        }
    }

    @SuppressWarnings("unchecked")
    private static void bind(MappedRegistry<?> registry) {
        try {
            var byValue = (Map<Object, Holder.Reference<?>>) BY_VALUE.get(registry);
            if (byValue == null) {
                return;
            }
            for (var entry : byValue.entrySet()) {
                var holder = entry.getValue();
                if (!holder.isBound()) {
                    BIND_VALUE.invoke(holder, entry.getKey());
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Не смогли - значения привяжет финальный freeze().
        }
    }

    @Nullable
    private static Field field(String name) {
        try {
            var field = MappedRegistry.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Method method(String name, Class<?>... args) {
        try {
            var method = Holder.Reference.class.getDeclaredMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
