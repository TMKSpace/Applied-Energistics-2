/*
 * Часть nfcompat. Объединение нескольких HolderSet.
 *
 * NeoForge'вская версия наследует CompositeHolderSet и опирается на патчи `HolderSet`
 * (сериализация, инвалидация). В ванили `HolderSet` — простой интерфейс, поэтому здесь
 * честная реализация объединения: stream/contains/getRandomElement/unwrap.
 *
 * Плата: набор несериализуем (unwrapKey пуст) — этого хватает для рецептов AE2.
 */
package net.neoforged.neoforge.registries.holdersets;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;

public final class OrHolderSet<T> implements HolderSet<T> {
    private final List<HolderSet<T>> components;

    public OrHolderSet(List<HolderSet<T>> components) {
        this.components = List.copyOf(components);
    }

    @Override
    public Stream<Holder<T>> stream() {
        return components.stream().flatMap(HolderSet::stream).distinct();
    }

    @Override
    public int size() {
        return (int) stream().count();
    }

    @Override
    public boolean isBound() {
        return components.stream().allMatch(HolderSet::isBound);
    }

    @Override
    public Either<TagKey<T>, List<Holder<T>>> unwrap() {
        return Either.right(stream().toList());
    }

    @Override
    public Optional<Holder<T>> getRandomElement(RandomSource random) {
        var all = stream().toList();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(random.nextInt(all.size())));
    }

    @Override
    public Holder<T> get(int index) {
        return stream().toList().get(index);
    }

    @Override
    public boolean contains(Holder<T> holder) {
        return components.stream().anyMatch(component -> component.contains(holder));
    }

    @Override
    public boolean canSerializeIn(HolderOwner<T> owner) {
        return components.stream().allMatch(component -> component.canSerializeIn(owner));
    }

    @Override
    public Optional<TagKey<T>> unwrapKey() {
        return Optional.empty();
    }

    @Override
    public Iterator<Holder<T>> iterator() {
        return stream().iterator();
    }
}
