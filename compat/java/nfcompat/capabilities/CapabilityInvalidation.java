/*
 * Часть nfcompat. Инвалидация capability — настоящая, а не заглушка.
 *
 * ЗАЧЕМ. NeoForge патчит `BlockEntity`/`Level`, добавляя `invalidateCapabilities()`
 * (`ServerLevel#registerCapabilityListener` + `invalidateCapabilities()` у block entity):
 * им AE2 говорит «провайдеры этого блока изменились, перечитай», и на этом держится
 * `BlockCapabilityCache` — кэш, который сам перезапрашивает значение по такому сигналу.
 *
 * У нас кэша нет (`BlockCapabilityCache.getCapability()` всегда спрашивает провайдеров), но
 * нам нужен именно СИГНАЛ: без него `StorageBusPart.updateTarget` вызывается один раз и,
 * если цель ещё не готова, ставит `NO_UPDATE` и больше никогда не перепроверяется.
 * Это валило `ae2:subnet`: шина смотрит в интерфейс подсети, интерфейс на первом тике ещё
 * без сети (`networkStorage == null`) -> шина видит пустоту -> `insert` возвращает 0 навсегда,
 * пока игрок не откроет GUI шины и не дёрнет настройку.
 *
 * КАК. Реестр `(уровень, позиция) -> слабые ссылки на слушателей`. `registerListener` пишут
 * AE2-парты (в `StorageBusPart.addToWorld`), `invalidate(holder)` вызывается в местах, где
 * NeoForge звал бы `invalidateCapabilities()` (`InterfaceLogic.notifyNeighbors`,
 * `CableBusContainer.updateAfterPartChange`, `CapabilityP2PTunnelPart`). Слушатель возвращает
 * `false`, когда он больше не нужен (парт убрали) — тогда ссылку выкидываем.
 *
 * ЧЕГО НЕ ДЕЛАЕМ. Не изобретаем кэш: значение по-прежнему читается из провайдеров каждый раз,
 * это дешевле ошибок «устаревший хендлер». Реестр — только доставка сигнала.
 */
package nfcompat.capabilities;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;

import org.jetbrains.annotations.Nullable;

public final class CapabilityInvalidation {

    private record Key(Level level, BlockPos pos) {
    }

    private static final Map<Key, List<WeakReference<ICapabilityInvalidationListener>>> LISTENERS = new ConcurrentHashMap<>();

    private CapabilityInvalidation() {
    }

    /**
     * NeoForge-но `ServerLevel#registerCapabilityListener`: «следи за capability в этой позиции».
     *
     * @param level    уровень (обычно {@code ServerLevel})
     * @param pos      позиция провайдера, чьи capability нам нужны
     * @param listener слушатель; хранится слабой ссылкой (владелец — тот, кто его создал)
     */
    public static void registerListener(@Nullable Object level, @Nullable Object pos,
            @Nullable Object listener) {
        if (!(level instanceof Level serverLevel)
                || !(pos instanceof BlockPos blockPos)
                || !(listener instanceof ICapabilityInvalidationListener invalidationListener)) {
            return;
        }

        var key = new Key(serverLevel, blockPos.immutable());
        var list = LISTENERS.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (list) {
            // Чистим мёртвые ссылки заодно, чтобы список не рос в мире с частой перестройкой.
            list.removeIf(ref -> ref.get() == null || ref.get() == invalidationListener);
            list.add(new WeakReference<>(invalidationListener));
        }
    }

    /**
     * NeoForge-но `invalidateCapabilities()` у block entity: доставить сигнал всем, кто следит
     * за этой позицией.
     */
    public static void invalidate(@Nullable Object holder) {
        if (!(holder instanceof BlockEntity blockEntity)) {
            return;
        }

        var level = blockEntity.getLevel();
        if (level == null) {
            return;
        }

        deliver(new Key(level, blockEntity.getBlockPos()), null);
    }

    /** Доставить сигнал всем слушателям в позиции (например, при смене блока). */
    public static void invalidate(@Nullable Object level, @Nullable Object pos) {
        if (level instanceof Level serverLevel && pos instanceof BlockPos blockPos) {
            deliver(new Key(serverLevel, blockPos.immutable()), null);
        }
    }

    private static void deliver(Key key, @Nullable ICapabilityInvalidationListener except) {
        var list = LISTENERS.get(key);
        if (list == null) {
            return;
        }

        List<WeakReference<ICapabilityInvalidationListener>> snapshot;
        synchronized (list) {
            snapshot = new ArrayList<>(list);
        }

        List<WeakReference<ICapabilityInvalidationListener>> finished = null;
        for (var ref : snapshot) {
            var listener = ref.get();
            if (listener == null) {
                if (finished == null) {
                    finished = new ArrayList<>(1);
                }
                finished.add(ref);
                continue;
            }
            if (listener == except) {
                continue;
            }

            boolean keep = true;
            try {
                keep = listener.onInvalidate();
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger()
                        .warn("Capability invalidation listener failed for {}", key.pos(), e);
            }

            if (!keep) {
                if (finished == null) {
                    finished = new ArrayList<>(1);
                }
                finished.add(ref);
            }
        }

        if (finished != null) {
            synchronized (list) {
                list.removeAll(finished);
            }
            if (list.isEmpty()) {
                LISTENERS.remove(key, list);
            }
        }
    }
}
