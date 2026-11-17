/*
 * Часть nfcompat. Шим net.neoforged.neoforge.common.util.Lazy.
 *
 * На NeoForge это утилита «ленивое значение с кэшем»: `of(Supplier)` считает значение
 * один раз, `invalidate()` сбрасывает кэш, чтобы посчитать заново. GuideME держит в
 * ней фабрику иконок навигации (`NavigationNode#iconFactory`): иконка — это
 * `ItemStack` из индекса гайда, её надо пересобирать после перезагрузки ресурсов и
 * при входе в мир (там же вызывается `invalidate()`).
 *
 * Без этого класса GuideME падал бы с NoClassDefFoundError при первом же
 * `GuideNavBar`/`invalidateNavigationIcons` — то есть на входе в мир.
 */
package net.neoforged.neoforge.common.util;

import java.util.Objects;
import java.util.function.Supplier;

public interface Lazy<T> extends Supplier<T> {

    static <T> Lazy<T> of(Supplier<T> supplier) {
        return new CachingLazy<>(supplier);
    }

    static <T> Lazy<T> concurrentOf(Supplier<T> supplier) {
        return new ConcurrentCachingLazy<>(supplier);
    }

    @Override
    T get();

    /** Сбрасывает закэшированное значение: следующий {@link #get()} посчитает его заново. */
    void invalidate();

    final class CachingLazy<T> implements Lazy<T> {
        private final Supplier<T> supplier;
        private T value;
        private boolean resolved;

        CachingLazy(Supplier<T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        public T get() {
            if (!this.resolved) {
                this.value = this.supplier.get();
                this.resolved = true;
            }
            return this.value;
        }

        @Override
        public void invalidate() {
            this.value = null;
            this.resolved = false;
        }
    }

    final class ConcurrentCachingLazy<T> implements Lazy<T> {
        private final Supplier<T> supplier;
        private T value;
        private boolean resolved;

        ConcurrentCachingLazy(Supplier<T> supplier) {
            this.supplier = Objects.requireNonNull(supplier);
        }

        @Override
        public synchronized T get() {
            if (!this.resolved) {
                this.value = this.supplier.get();
                this.resolved = true;
            }
            return this.value;
        }

        @Override
        public synchronized void invalidate() {
            this.value = null;
            this.resolved = false;
        }
    }
}
