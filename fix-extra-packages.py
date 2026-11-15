# -*- coding: utf-8 -*-
"""Мелкие пакеты: выкидываем тяжёлое, пишем минимальное руками."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java"

DELETE = [
    "net/neoforged/neoforge/registries/holdersets/AndHolderSet.java",
    "net/neoforged/neoforge/registries/holdersets/AnyHolderSet.java",
    "net/neoforged/neoforge/registries/holdersets/CompositeHolderSet.java",
    "net/neoforged/neoforge/registries/holdersets/HolderSetType.java",
    "net/neoforged/neoforge/registries/holdersets/ICustomHolderSet.java",
    "net/neoforged/neoforge/registries/holdersets/NotHolderSet.java",
    "net/neoforged/neoforge/registries/holdersets/package-info.java",
    "net/neoforged/neoforge/common/crafting/DifferenceIngredient.java",
    "net/neoforged/neoforge/common/crafting/ICustomIngredient.java",
    "net/neoforged/neoforge/client/extensions/common/ClientExtensionsManager.java",
    "net/neoforged/neoforge/client/extensions/common/IClientFluidTypeExtensions.java",
    "net/neoforged/neoforge/client/extensions/common/IClientItemExtensions.java",
    "net/neoforged/neoforge/client/extensions/common/IClientMobEffectExtensions.java",
    "net/neoforged/neoforge/client/extensions/common/package-info.java",
]

for rel in DELETE:
    p = os.path.join(ROOT, rel.replace("/", os.sep))
    if os.path.exists(p):
        os.remove(p)
        print("удалён", rel)

FILES = {}

FILES["net/neoforged/neoforge/registries/IRegistryExtension.java"] = '''/*
 * Часть nfcompat. Расширение реестра.
 *
 * В NeoForge `Registry` патчится до `IRegistryExtension` и умеет `addCallback`.
 * В ванили такого нет, поэтому интерфейс объявлен только чтобы компилировался
 * подписной API коллбеков. Вызов `registry.addCallback(...)` из AE2 придётся
 * переписать — см. AE2-PATCHES.md.
 */
package net.neoforged.neoforge.registries;

import net.neoforged.neoforge.registries.callback.RegistryCallback;

public interface IRegistryExtension<T> {
    default void addCallback(RegistryCallback<T> callback) {
        throw new UnsupportedOperationException("nfcompat: коллбеки реестров не поддерживаются");
    }
}
'''

FILES["net/neoforged/neoforge/registries/holdersets/OrHolderSet.java"] = '''/*
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
'''

FILES["net/neoforged/neoforge/common/loot/NeoForgeLootContextParams.java"] = '''/*
 * Часть nfcompat. Дополнительные параметры лут-контекста.
 *
 * В NeoForge набор шире; AE2 использует ровно один — стек, по которому сработал триггер.
 * На Fabric сам параметр в контекст никто не кладёт (это делает патч NeoForge),
 * поэтому значение в лут-таблице будет отсутствовать — см. комментарий в коде вызова.
 */
package net.neoforged.neoforge.common.loot;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemInstance;

public final class NeoForgeLootContextParams {
    public static final ContextKey<ItemInstance> QUERIED_STACK = new ContextKey<>(
            Identifier.fromNamespaceAndPath("neoforge", "queried_stack"));

    private NeoForgeLootContextParams() {
    }
}
'''

FILES["net/neoforged/neoforge/client/extensions/common/RegisterClientExtensionsEvent.java"] = '''/*
 * Часть nfcompat. Регистрация клиентских расширений блоков и предметов.
 *
 * В NeoForge событие несёт реестр всех видов расширений (блок, предмет, жидкость, эффект).
 * AE2 подписывает только блоки, поэтому оставлен только `registerBlock`, а сам реестр
 * держится здесь: `ClientExtensionsManager` из NeoForge целиком не портирован —
 * он завязан на патчи ванильных классов.
 */
package net.neoforged.neoforge.client.extensions.common;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterClientExtensionsEvent extends Event implements IModBusEvent {
    private static final Map<Block, IClientBlockExtensions> BLOCK_EXTENSIONS = new IdentityHashMap<>();

    public RegisterClientExtensionsEvent() {
    }

    public void registerBlock(IClientBlockExtensions extensions, Block... blocks) {
        for (Block block : blocks) {
            BLOCK_EXTENSIONS.put(block, extensions);
        }
    }

    public static IClientBlockExtensions getBlockExtensions(Block block) {
        return BLOCK_EXTENSIONS.get(block);
    }
}
'''

for rel, text in FILES.items():
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("написан", rel)
