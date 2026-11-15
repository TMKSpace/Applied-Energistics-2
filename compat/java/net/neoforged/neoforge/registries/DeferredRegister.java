/*
 * Часть nfcompat. Шим net.neoforged.neoforge.registries.DeferredRegister для Fabric.
 *
 * ЧЕМ ОТЛИЧАЕТСЯ ОТ NeoForge (читать до того, как удивляться):
 *
 * 1. Нетерпеливая регистрация. В NeoForge DeferredRegister - это очередь: объекты создаются
 *    и кладутся в реестр на RegisterEvent, то есть после того, как все статические
 *    инициализаторы модов отработали. На Fabric события регистрации нет, поэтому здесь
 *    register(...) СРАЗУ создаёт объект и СРАЗУ кладёт его в Registry через
 *    Registry.registerForHolder(Registry, ResourceKey, T) - тот же самый ванильный путь,
 *    которым пользуется Fabric. DeferredHolder получает готовый Holder.Reference.
 *
 *    Практическое следствие: порядок статических инициализаторов теперь значим так же,
 *    как в обычном (не ленивом) коде. AE2 к этому готова: в AEBlocks.block() сначала
 *    регистрируется блок (DR.registerBlock), и только потом предмет, чья фабрика дергает
 *    deferredBlock.get() - блок к этому моменту уже в реестре. Обратный случай
 *    (предмет раньше блока) дал бы NPE. В NeoForge такой код работал бы в любом порядке.
 *
 * 2. Реестры, которых на Fabric нет (NeoForge-специфичные: neoforge:attachment_types,
 *    neoforge:fluid_type и т.п.), эмулируются локальной LinkedHashMap внутри
 *    DeferredRegister: значения создаются и запоминаются, а холдеры биндятся на них
 *    напрямую. Это заглушка: ни id-маппинга, ни тегов, ни сериализации у таких
 *    "реестров" нет.
 *
 * 3. register(IEventBus) ничего не делает - всё уже зарегистрировано. Метод оставлен,
 *    потому что AE2 его вызывает.
 *
 * 4. addAlias(...) сохраняет алиасы в мапе, но применить их некуда: в MC/Fabric нет
 *    Registry.addAlias как в NeoForge. Заглушка.
 *
 * 5. makeRegistry(Consumer<RegistryBuilder>) и работа с NewRegistryEvent не реализованы -
 *    в AE2 не используются, а честно создать новый Registry в Fabric из шима нельзя.
 */
package net.neoforged.neoforge.registries;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import org.jspecify.annotations.Nullable;

/**
 * Хелпер для регистрации объектов в ванильные (и не очень) реестры + ленивые с виду
 * {@link DeferredHolder}ы поверх них.
 *
 * @param <T> базовый тип реестра
 */
public class DeferredRegister<T> {

    public static <T> DeferredRegister<T> create(Registry<T> registry, String namespace) {
        return new DeferredRegister<>(registry.key(), namespace);
    }

    public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> key, String namespace) {
        return new DeferredRegister<>(key, namespace);
    }

    public static <B> DeferredRegister<B> create(Identifier registryName, String modid) {
        return new DeferredRegister<>(ResourceKey.createRegistryKey(registryName), modid);
    }

    public static DeferredRegister.Items createItems(String modid) {
        return new Items(modid);
    }

    public static DeferredRegister.Blocks createBlocks(String modid) {
        return new Blocks(modid);
    }

    public static DataComponents createDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey,
            String modid) {
        return new DataComponents(registryKey, modid);
    }

    public static Entities createEntities(String modid) {
        return new Entities(modid);
    }

    private final ResourceKey<? extends Registry<T>> registryKey;
    private final String namespace;
    private final Map<DeferredHolder<T, ? extends T>, Supplier<? extends T>> entries = new LinkedHashMap<>();
    private final Set<DeferredHolder<T, ? extends T>> entriesView = Collections.unmodifiableSet(entries.keySet());
    private final Map<Identifier, Identifier> aliases = new HashMap<>();

    /**
     * Эмуляция реестров, которых в Fabric/MC нет. Ключ - id, значение - созданный объект.
     * См. пункт 2 в шапке файла.
     */
    private final Map<Identifier, T> localRegistry = new LinkedHashMap<>();

    private final RegistryHolder<T> registryHolder;

    protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
        this.registryKey = Objects.requireNonNull(registryKey);
        this.namespace = Objects.requireNonNull(namespace);
        // nfcompat: раньше поле инициализировалось inline и падало на "registryKey might not have
        // been initialized" — javac прав, поля инициализируются до тела конструктора.
        this.registryHolder = new RegistryHolder<>(this.registryKey);
    }

    /**
     * Регистрирует объект и возвращает холдер. В отличие от NeoForge объект создаётся
     * сразу (см. пункт 1 в шапке файла).
     */
    public <I extends T> DeferredHolder<T, I> register(final String name, final Supplier<? extends I> sup) {
        return this.register(name, key -> sup.get());
    }

    public <I extends T> DeferredHolder<T, I> register(final String name, final Function<Identifier, ? extends I> func) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(func);
        final Identifier key = Identifier.fromNamespaceAndPath(namespace, name);

        DeferredHolder<T, I> ret = createHolder(this.registryKey, key);
        if (entries.putIfAbsent(ret, () -> func.apply(key)) != null) {
            throw new IllegalArgumentException("Duplicate registration " + name);
        }

        // Нетерпеливая регистрация: см. пункт 1.
        registerNow(ret);

        return ret;
    }

    private void registerNow(DeferredHolder<T, ?> holder) {
        Registry<T> registry = lookupRegistry();
        T value = entries.get(holder).get();

        if (registry == null) {
            // Реестра на Fabric нет - держим значение локально (пункт 2).
            localRegistry.put(holder.getId(), value);
            bindLocal(holder, value);
            return;
        }

        ResourceKey<T> key = ResourceKey.create(this.registryKey, holder.getId());

        // Если объект уже кто-то положил руками через Registry.register - переиспользуем холдер.
        if (registry.containsKey(holder.getId())) {
            Holder.Reference<T> existing = registry.get(key).orElse(null);
            if (existing != null) {
                ((DeferredHolder<T, ?>) holder).bindTo(existing);
                return;
            }
        }

        Holder.Reference<T> reference = Registry.registerForHolder(registry, key, value);
        ((DeferredHolder<T, ?>) holder).bindTo(reference);
    }

    /** Настоящий реестр по ключу или null, если такого реестра на Fabric нет. */
    @Nullable
    @SuppressWarnings("unchecked")
    private Registry<T> lookupRegistry() {
        return (Registry<T>) BuiltInRegistries.REGISTRY.getValue(this.registryKey.identifier());
    }

    /**
     * Привязка значения для «виртуального» реестра. Сырой каст нужен потому, что
     * {@code DeferredHolder<T, ?>} не даёт положить T в bindLocal(T extends-параметр).
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void bindLocal(DeferredHolder<T, ? extends T> holder, T value) {
        ((DeferredHolder) holder).bindLocal(value);
    }

    /**
     * Создаёт холдер нужного типа (DeferredHolder / DeferredItem / DeferredBlock).
     */
    protected <I extends T> DeferredHolder<T, I> createHolder(ResourceKey<? extends Registry<T>> registryKey,
            Identifier key) {
        return DeferredHolder.create(registryKey, key);
    }

    public Supplier<Registry<T>> getRegistry() {
        return this.registryHolder;
    }

    /** Значения «виртуального» реестра (для реестров, которых на Fabric нет). */
    public Collection<T> getLocalEntries() {
        return Collections.unmodifiableCollection(this.localRegistry.values());
    }

    public TagKey<T> createTagKey(String path) {
        Objects.requireNonNull(path);
        return createTagKey(Identifier.fromNamespaceAndPath(this.namespace, path));
    }

    public TagKey<T> createTagKey(Identifier location) {
        Objects.requireNonNull(location);
        return TagKey.create(this.registryKey, location);
    }

    /**
     * Заглушка: в MC/Fabric нет Registry.addAlias. Алиасы запоминаются и не применяются
     * (пункт 4 в шапке файла).
     */
    public void addAlias(Identifier from, Identifier to) {
        this.aliases.put(from, to);
    }

    /**
     * Ничего не делает: всё уже зарегистрировано нетерпеливо (пункт 3).
     */
    public void register(IEventBus bus) {
        // no-op
    }

    public Collection<DeferredHolder<T, ? extends T>> getEntries() {
        return entriesView;
    }

    public ResourceKey<? extends Registry<T>> getRegistryKey() {
        return this.registryKey;
    }

    public Identifier getRegistryName() {
        return this.registryKey.identifier();
    }

    /** {@return namespace (modid), с которым создан этот DeferredRegister} */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Специализированный DeferredRegister для {@link Block}.
     */
    public static class Blocks extends DeferredRegister<Block> {
        protected Blocks(String namespace) {
            super(Registries.BLOCK, namespace);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Function<Identifier, ? extends B> func) {
            return (DeferredBlock<B>) super.register(name, func);
        }

        @Override
        public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> sup) {
            return this.register(name, key -> sup.get());
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name,
                Function<BlockBehaviour.Properties, ? extends B> func, Supplier<BlockBehaviour.Properties> properties) {
            return this.register(name,
                    key -> func.apply(properties.get().setId(ResourceKey.create(Registries.BLOCK, key))));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name,
                Function<BlockBehaviour.Properties, ? extends B> func,
                UnaryOperator<BlockBehaviour.Properties> properties) {
            return this.registerBlock(name, func, () -> properties.apply(BlockBehaviour.Properties.of()));
        }

        public <B extends Block> DeferredBlock<B> registerBlock(String name,
                Function<BlockBehaviour.Properties, ? extends B> func) {
            return this.registerBlock(name, func, UnaryOperator.identity());
        }

        public DeferredBlock<Block> registerSimpleBlock(String name, Supplier<BlockBehaviour.Properties> properties) {
            return this.registerBlock(name, Block::new, properties);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name,
                UnaryOperator<BlockBehaviour.Properties> properties) {
            return this.registerBlock(name, Block::new, properties);
        }

        public DeferredBlock<Block> registerSimpleBlock(String name) {
            return this.registerSimpleBlock(name, UnaryOperator.identity());
        }

        @Override
        protected <I extends Block> DeferredBlock<I> createHolder(ResourceKey<? extends Registry<Block>> registryKey,
                Identifier key) {
            return DeferredBlock.createBlock(ResourceKey.create(registryKey, key));
        }
    }

    /**
     * Специализированный DeferredRegister для {@link Item}.
     */
    public static class Items extends DeferredRegister<Item> {
        protected Items(String namespace) {
            super(Registries.ITEM, namespace);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <I extends Item> DeferredItem<I> register(String name, Function<Identifier, ? extends I> func) {
            return (DeferredItem<I>) super.register(name, func);
        }

        @Override
        public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> sup) {
            return this.register(name, key -> sup.get());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block,
                Supplier<Item.Properties> properties) {
            return this.registerItem(name, props -> new BlockItem(block.get(), props),
                    () -> properties.get().useBlockDescriptionPrefix());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block,
                UnaryOperator<Item.Properties> properties) {
            return this.registerSimpleBlockItem(name, block, () -> properties.apply(new Item.Properties()));
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
            return this.registerSimpleBlockItem(name, block, UnaryOperator.identity());
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block,
                Supplier<Item.Properties> properties) {
            return this.registerSimpleBlockItem(block.unwrapKey().orElseThrow().identifier().getPath(), block::value,
                    properties);
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block,
                UnaryOperator<Item.Properties> properties) {
            return this.registerSimpleBlockItem(block, () -> properties.apply(new Item.Properties()));
        }

        public DeferredItem<BlockItem> registerSimpleBlockItem(Holder<Block> block) {
            return this.registerSimpleBlockItem(block, UnaryOperator.identity());
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func,
                Supplier<Item.Properties> properties) {
            return this.register(name, key -> func.apply(properties.get().setId(ResourceKey.create(Registries.ITEM, key))));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func,
                UnaryOperator<Item.Properties> properties) {
            return this.registerItem(name, func, () -> properties.apply(new Item.Properties()));
        }

        public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> func) {
            return this.registerItem(name, func, UnaryOperator.identity());
        }

        public DeferredItem<Item> registerSimpleItem(String name, Supplier<Item.Properties> properties) {
            return this.registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
            return this.registerItem(name, Item::new, properties);
        }

        public DeferredItem<Item> registerSimpleItem(String name) {
            return this.registerItem(name, Item::new, UnaryOperator.identity());
        }

        @Override
        protected <I extends Item> DeferredItem<I> createHolder(ResourceKey<? extends Registry<Item>> registryKey,
                Identifier key) {
            return DeferredItem.createItem(ResourceKey.create(registryKey, key));
        }
    }

    /**
     * Специализированный DeferredRegister для {@link DataComponentType}.
     */
    public static class DataComponents extends DeferredRegister<DataComponentType<?>> {
        protected DataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
            super(registryKey, namespace);
        }

        public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name,
                UnaryOperator<DataComponentType.Builder<D>> builder) {
            return this.register(name, () -> builder.apply(DataComponentType.builder()).build());
        }
    }

    /**
     * Специализированный DeferredRegister для {@link EntityType}.
     */
    public static class Entities extends DeferredRegister<EntityType<?>> {
        protected Entities(String namespace) {
            super(Registries.ENTITY_TYPE, namespace);
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category) {
            return this.registerEntityType(name, factory, category, UnaryOperator.identity());
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category,
                BiFunction<EntityType.EntityFactory<E>, MobCategory, EntityType.Builder<E>> builder) {
            return this.register(name,
                    key -> builder.apply(factory, category).build(ResourceKey.create(Registries.ENTITY_TYPE, key)));
        }

        public <E extends Entity> DeferredHolder<EntityType<?>, EntityType<E>> registerEntityType(String name,
                EntityType.EntityFactory<E> factory, MobCategory category,
                UnaryOperator<EntityType.Builder<E>> builder) {
            return this.registerEntityType(name, factory, category,
                    (fact, cat) -> builder.apply(EntityType.Builder.of(fact, cat)));
        }
    }

    private static class RegistryHolder<V> implements Supplier<Registry<V>> {
        private final ResourceKey<? extends Registry<V>> registryKey;

        private RegistryHolder(ResourceKey<? extends Registry<V>> registryKey) {
            this.registryKey = registryKey;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public @Nullable Registry<V> get() {
            return (Registry<V>) BuiltInRegistries.REGISTRY.getValueOrThrow((ResourceKey) this.registryKey);
        }
    }

    /** Алиасы, которые некуда применить (пункт 4). */
    public Map<Identifier, Identifier> getAliases() {
        return Collections.unmodifiableMap(aliases);
    }

    /** Для отладки: список зарегистрированных id. */
    public Set<Identifier> getRegisteredIds() {
        Set<Identifier> ids = new java.util.LinkedHashSet<>();
        for (Entry<DeferredHolder<T, ? extends T>, Supplier<? extends T>> e : entries.entrySet()) {
            ids.add(e.getKey().getId());
        }
        return Collections.unmodifiableSet(ids);
    }
}
