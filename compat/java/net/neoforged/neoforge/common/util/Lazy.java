/*
 * Часть nfcompat. Шим net.neoforged.neoforge.common.util.Lazy.
 *
 * В NeoForge это ленивое <значение по требованию>: `of(Supplier)` считает значение
 * один раз, `invalidate()` сбрасывает его, чтобы посчитать заново. GuideME строит из
 * этой фабрики иконки навигации (`NavigationNode#iconFactory`): иконка - это
 * `ItemStack` из шаблона страницы, но её собирают после перерегрузки ресурсов и
 * при входе в мир (там же вызывается `invalidate()`).
 *
 * ВАЖНО: в NeoForge 26.3 `Lazy` - **класс**, а не интерфейс. GuideME собрана под неё и
 * вызывает `Lazy.of(...)` как `Methodref` к классу. Когда у нас здесь был интерфейс,
 * клиент падал на загрузке ресурсов с
 *   IncompatibleClassChangeError: Method 'Lazy Lazy.of(Supplier)' must be InterfaceMethodref
 * и это снова приводило к recovery-экрану и чёрному окну.
 */
package net.neoforged.neoforge.common.util;

import java.util.Objects;
import java.util.function.Supplier;

public class Lazy<T> implements Supplier<T> {

    private final Supplier<T> supplier;
    private final boolean threadSafe;

    private volatile boolean resolved;
    private T value;

    private Lazy(Supplier<T> supplier, boolean threadSafe) {
        this.supplier = Objects.requireNonNull(supplier);
        this.threadSafe = threadSafe;
    }

    public static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<>(supplier, false);
    }

    public static <T> Lazy<T> concurrentOf(Supplier<T> supplier) {
        return new Lazy<>(supplier, true);
    }

    @Override
    public T get() {
        if (this.threadSafe) {
            synchronized (this) {
                return resolve();
            }
        }
        return resolve();
    }

    /** Сбрасывает закэшированное значение: следующий {@link #get()} посчитает его заново. */
    public void invalidate() {
        if (this.threadSafe) {
            synchronized (this) {
                this.value = null;
                this.resolved = false;
            }
            return;
        }
        this.value = null;
        this.resolved = false;
    }

    private T resolve() {
        if (!this.resolved) {
            this.value = this.supplier.get();
            this.resolved = true;
        }
        return this.value;
    }
}
