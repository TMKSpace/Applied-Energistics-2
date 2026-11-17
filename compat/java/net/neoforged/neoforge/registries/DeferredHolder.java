/*
 * Часть nfcompat. Шим net.neoforged.neoforge.registries.DeferredHolder для Fabric.
 *
 * Отличия от NeoForge (важно):
 *  - В NeoForge DeferredHolder ленивый: держит ResourceKey, а значение подтягивает из
 *    реестра на RegisterEvent. У нас события регистрации нет (см. DeferredRegister),
 *    поэтому холдер получает значение СРАЗУ при регистрации: либо Holder.Reference из
 *    настоящего MC-реестра (Fabric), либо локальное значение, если реестра на Fabric
 *    не существует вообще (NeoForge-специфичные реестры вроде neoforge:attachment_types).
 *  - Как и в NeoForge, наследуемся от Holder.Reference<R>: это бесплатно даёт is(Identifier),
 *    is(ResourceKey), is(Predicate), kind() и прочий контракт Holder, который иначе
 *    пришлось бы писать руками. Внутреннее поле value у Reference остаётся null,
 *    поэтому value()/get()/unwrap()/components()/tags() перекрыты и смотрят в binding.
 */
package net.neoforged.neoforge.registries;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

/**
 * Ленивый по имени, нетерпеливый по факту холдер зарегистрированного объекта.
 *
 * @param <R> тип реестра (например {@code Item})
 * @param <T> конкретный тип значения (например {@code MyItem})
 */
public class DeferredHolder<R, T extends R> extends Holder.Reference<R> implements Supplier<T> {

    /** Холдер из настоящего MC-реестра. null, если реестра на Fabric нет. */
    @Nullable
    private Holder<R> bound;

    /** Значение для реестров, которых на Fabric не существует. */
    @Nullable
    private T localValue;

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<? extends Registry<R>> registryKey,
            Identifier valueName) {
        return create(ResourceKey.create(registryKey, valueName));
    }

    public static <R, T extends R> DeferredHolder<R, T> create(ResourceKey<R> key) {
        return new DeferredHolder<>(key);
    }

    protected DeferredHolder(ResourceKey<R> key) {
        Objects.requireNonNull(key);
        // owner = null: у нас нет HolderOwner для чужих реестров. NeoForge тоже передаёт null,
        // когда реестр ещё не создан.
        super(Holder.Reference.Type.STAND_ALONE, null, key, null);
    }

    /** Привязка к настоящему холдеру MC-реестра. Вызывается из DeferredRegister. */
    void bindTo(Holder<R> holder) {
        this.bound = Objects.requireNonNull(holder);
        this.localValue = null;
    }

    /** Привязка к значению реестра, которого на Fabric нет. Вызывается из DeferredRegister. */
    void bindLocal(T value) {
        this.localValue = Objects.requireNonNull(value);
        this.bound = null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T value() {
        if (this.bound != null) {
            return (T) this.bound.value();
        }
        if (this.localValue != null) {
            return this.localValue;
        }
        throw new NullPointerException("Trying to access unbound value: " + this.key());
    }

    @Override
    public T get() {
        return value();
    }

    @Override
    public boolean isBound() {
        if (this.bound != null) {
            return this.bound.isBound();
        }
        return this.localValue != null;
    }

    @Override
    public boolean areComponentsBound() {
        if (this.bound != null) {
            return this.bound.areComponentsBound();
        }
        // Локальные значения (attachment types и т.п.) компонентов не имеют.
        return this.localValue != null;
    }

    @Override
    public DataComponentMap components() {
        return this.bound != null ? this.bound.components() : DataComponentMap.EMPTY;
    }

    @Override
    public Stream<TagKey<R>> tags() {
        return this.bound != null ? this.bound.tags() : Stream.empty();
    }

    @Override
    public Either<ResourceKey<R>, R> unwrap() {
        return isBound() ? Either.right(value()) : Either.left(this.key());
    }

    @Override
    public Optional<ResourceKey<R>> unwrapKey() {
        return Optional.of(this.key());
    }

    @Override
    public boolean is(TagKey<R> tag) {
        return this.bound != null && this.bound.is(tag);
    }

    @Override
    @Deprecated
    public boolean is(Holder<R> holder) {
        return this.bound != null ? this.bound.is(holder) : holder == this;
    }

    @Override
    public boolean canSerializeIn(net.minecraft.core.HolderOwner<R> owner) {
        return this.bound != null ? this.bound.canSerializeIn(owner) : owner == null;
    }

    /** Идентификатор объекта, на который указывает холдер (аналог NeoForge). */
    public Identifier getId() {
        return this.key().identifier();
    }

    @Override
    public String toString() {
        return "DeferredHolder{" + this.key() + "}";
    }
}
