# -*- coding: utf-8 -*-
"""Добор шима: DynamicBlockStateModel, IEntityWithComplexSpawn, реестры, enqueueWork."""
import io
import os

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java"

FILES = {}

FILES["net/neoforged/neoforge/client/model/DynamicBlockStateModel.java"] = '''/*
 * Часть nfcompat. Модель блок-состояния с контекстом уровня.
 *
 * NeoForge патчит ванильный `BlockStateModel` до `BlockStateModelExtension` и добавляет
 * 5-аргументный `collectParts(level, pos, state, random, parts)`. В ванили метод только
 * 2-аргументный. Здесь 5-аргументный объявлен сам по себе, а 2-аргументный получил
 * дефолтную реализацию, которая зовёт 5-аргументный с пустым контекстом — ровно как
 * делал патч NeoForge, но без правки ванильного интерфейса.
 */
package net.neoforged.neoforge.client.model;

import java.util.List;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public interface DynamicBlockStateModel extends BlockStateModel {

    /** Полная версия: модель может заглянуть в уровень, позицию и состояние. */
    void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
            List<BlockStateModelPart> parts);

    @Override
    default void collectParts(RandomSource random, List<BlockStateModelPart> parts) {
        collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, Blocks.AIR.defaultBlockState(), random, parts);
    }
}
'''

FILES["net/neoforged/neoforge/entity/IEntityWithComplexSpawn.java"] = '''/*
 * Часть nfcompat. Сущность, которая сама пишет свои данные при спавне.
 *
 * В ванили `Entity` за спавн-данные отвечает механизм `EntityType`/`SpawnPacket`, а
 * NeoForge даёт сущности хуки: `writeSpawnData`/`readSpawnData`. Реализация на стороне
 * Fabric должна вызывать их из своего spawn-пакета — пока это TODO, интерфейс нужен
 * для компиляции AE2.
 */
package net.neoforged.neoforge.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;

public interface IEntityWithComplexSpawn {
    void writeSpawnData(RegistryFriendlyByteBuf buffer);

    void readSpawnData(RegistryFriendlyByteBuf additionalData);
}
'''

FILES["net/neoforged/neoforge/registries/RegistryBuilder.java"] = '''/*
 * Часть nfcompat. Параметры создания нового реестра.
 *
 * В NeoForge настоящий билдер с десятком настроек (sync, maxId, intrusiveHolders,
 * default-ключи и т.д.). Здесь только то, что реально передаёт AE2, плюс геттеры:
 * значения не теряются, но часть из них (sync, maxId) на Fabric ни на что не влияет.
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public final class RegistryBuilder<T> {
    private final ResourceKey<? extends Registry<T>> key;
    private boolean sync;
    private int maxId = Integer.MAX_VALUE;

    public RegistryBuilder(ResourceKey<? extends Registry<T>> key) {
        this.key = key;
    }

    public RegistryBuilder<T> sync(boolean sync) {
        this.sync = sync;
        return this;
    }

    public RegistryBuilder<T> maxId(int maxId) {
        this.maxId = maxId;
        return this;
    }

    public ResourceKey<? extends Registry<T>> key() {
        return key;
    }

    public boolean isSynced() {
        return sync;
    }

    public int maxId() {
        return maxId;
    }
}
'''

FILES["net/neoforged/neoforge/registries/NewRegistryEvent.java"] = '''/*
 * Часть nfcompat. Создание собственного реестра мода.
 *
 * В NeoForge реестр создаётся до заморозки `BuiltInRegistries` и вставляется туда, поэтому
 * его ключ потом работает при сериализации. На Fabric такой точки нет: `BuiltInRegistries`
 * уже заморожен. Возвращаем настоящий `MappedRegistry`, но живёт он только в руках AE2 —
 * для её собственного использования этого достаточно, для сериализации по ключу — нет.
 */
package net.neoforged.neoforge.registries;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class NewRegistryEvent extends Event implements IModBusEvent {
    public NewRegistryEvent() {
    }

    public <T> Registry<T> create(RegistryBuilder<T> builder) {
        return new MappedRegistry<>(builder.key(), Lifecycle.stable());
    }
}
'''

FILES["net/neoforged/neoforge/registries/RegisterEvent.java"] = '''/*
 * Часть nfcompat. Событие «наполни этот ванильный реестр».
 *
 * В NeoForge оно приходит по одному на каждый реестр, до его заморозки, и моды в этот
 * момент вызывают `DeferredRegister#register`. У нас регистрация жадная (объект создаётся
 * сразу в `register(...)`), поэтому событие нужно только для кода AE2, который вешает
 * обработчик: он получит уже заполненные реестры.
 */
package net.neoforged.neoforge.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

public class RegisterEvent extends Event implements IModBusEvent {
    private final ResourceKey<? extends Registry<?>> registryKey;

    public RegisterEvent(ResourceKey<? extends Registry<?>> registryKey) {
        this.registryKey = registryKey;
    }

    public ResourceKey<? extends Registry<?>> getRegistryKey() {
        return registryKey;
    }

    @SuppressWarnings("unchecked")
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {
        return (Registry<T>) ((Registry<Registry<?>>) (Registry<?>) BuiltInRegistries.REGISTRY).getValue(key);
    }
}
'''

FILES["nfcompat/registry/RegistryCallbacks.java"] = '''/*
 * Часть nfcompat. Коллбеки реестров.
 *
 * В NeoForge `Registry` патчится до `IRegistryExtension` и умеет `addCallback`: коллбек
 * срабатывает, когда реестр «запекается» (все объекты известны). Добавить метод в ванильный
 * `Registry` нельзя, поэтому AE2 в FABRIC-ветке зовёт этот хелпер.
 *
 * Наши регистрации жадные, то есть на момент вызова объекты уже на месте — поэтому
 * коллбек честно выполняется сразу. Семантика «после запекания» приближена, но не совпадает:
 * если коллбек должен увидеть объекты, добавленные позже, он их не увидит.
 */
package nfcompat.registry;

import java.util.function.Consumer;

import net.minecraft.core.Registry;

public final class RegistryCallbacks {
    private RegistryCallbacks() {
    }

    public static <T> void onBake(Registry<T> registry, Consumer<Registry<T>> callback) {
        callback.accept(registry);
    }
}
'''

for rel, text in FILES.items():
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("написан", rel)

# --- правки существующих файлов ---

def patch(rel, old, new):
    path = os.path.join(ROOT, rel.replace("/", os.sep))
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! НЕ НАЙДЕНО в", rel)
        return
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, new, 1))
    print("поправлен", rel)

patch("net/neoforged/fml/event/lifecycle/ParallelDispatchEvent.java",
      """    public void enqueueWork(Runnable runnable) {
        deferredWorkQueue.enqueueWork(runnable);
    }""",
      """    /**
     * NeoForge возвращает CompletableFuture, и AE2 этим пользуется
     * (`enqueueWork(...).whenComplete(...)`). Работы у нас выполняются сразу,
     * поэтому и future уже завершён.
     */
    public java.util.concurrent.CompletableFuture<Void> enqueueWork(Runnable runnable) {
        deferredWorkQueue.enqueueWork(runnable);
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }""")
