package nfcompat.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import nfcompat.GameBootstrap;

/**
 * ModelDataHooks — замена неофорджевских методов {@code BlockEntity#getModelData()} и
 * {@code BlockGetter#getModelData(BlockPos)}. Главное, что здесь проверяется: хелпер
 * никогда не падает на "не том" объекте (рендер зовёт его из кучи мест) и отдаёт
 * именно {@link ModelData#EMPTY}, если данных нет.
 * <p>
 * Настоящий BlockEntity здесь всё-таки создаётся: конструктор требует валидный
 * BlockEntityType, а собрать тип вручную нельзя (он создаёт intrusive holder в
 * замороженном BuiltInRegistries.BLOCK_ENTITY_TYPE), поэтому берём настоящий CHEST.
 */
class ModelDataHooksTest {

    static final ModelProperty<Integer> COUNT = new ModelProperty<>();

    private static BlockEntityType<?> type;

    @BeforeAll
    static void setUp() {
        GameBootstrap.ensure();
        type = BlockEntityTypes.CHEST;
    }

    /** Блок-сущность с данными модели — как AEBaseBlockEntity. */
    static class DataBlockEntity extends BlockEntity implements ModelDataProvider {
        DataBlockEntity() {
            super(type, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        }

        @Override
        public ModelData getModelData() {
            return ModelData.of(COUNT, 42);
        }
    }

    /** Обычная блок-сущность без ModelDataProvider. */
    static class PlainBlockEntity extends BlockEntity {
        PlainBlockEntity() {
            super(type, BlockPos.ZERO, Blocks.CHEST.defaultBlockState());
        }
    }

    @Test
    @DisplayName("get(null, pos) не падает и отдаёт EMPTY")
    void nullGetterIsSafe() {
        assertThat(ModelDataHooks.get(null, BlockPos.ZERO)).isSameAs(ModelData.EMPTY);
    }

    @Test
    @DisplayName("не-BlockGetter (посторонний объект) — тоже EMPTY, без ClassCastException")
    void foreignGetterIsSafe() {
        assertThat(ModelDataHooks.get("это не BlockGetter", BlockPos.ZERO)).isSameAs(ModelData.EMPTY);
        assertThat(ModelDataHooks.get(42, BlockPos.ZERO)).isSameAs(ModelData.EMPTY);
    }

    @Test
    @DisplayName("get(null) для блок-сущности — EMPTY, без NPE")
    void nullBlockEntityIsSafe() {
        assertThat(ModelDataHooks.get((BlockEntity) null)).isSameAs(ModelData.EMPTY);
    }

    @Test
    @DisplayName("блок-сущность без ModelDataProvider — EMPTY")
    void plainBlockEntityGivesEmpty() {
        assertThat(ModelDataHooks.get(new PlainBlockEntity())).isSameAs(ModelData.EMPTY);
    }

    @Test
    @DisplayName("блок-сущность с ModelDataProvider отдаёт свои данные")
    void providerBlockEntityGivesItsData() {
        ModelData data = ModelDataHooks.get(new DataBlockEntity());

        assertThat(data).isNotSameAs(ModelData.EMPTY);
        assertThat(data.get(COUNT)).isEqualTo(42);
    }

    @Test
    @DisplayName("get(getter, pos): данные берутся у блок-сущности из позиции")
    void getterReadsBlockEntityAtPos() {
        var blockEntity = new DataBlockEntity();
        BlockGetter getter = fakeGetter(blockEntity);

        assertThat(ModelDataHooks.get(getter, BlockPos.ZERO).get(COUNT)).isEqualTo(42);
    }

    @Test
    @DisplayName("get(getter, pos): позиция без блок-сущности — EMPTY")
    void getterWithoutBlockEntityGivesEmpty() {
        assertThat(ModelDataHooks.get(fakeGetter(null), BlockPos.ZERO)).isSameAs(ModelData.EMPTY);
    }

    @Test
    @DisplayName("requestUpdate(null) — no-op, а не NPE")
    void requestUpdateIsNoOp() {
        assertThatCode(() -> ModelDataHooks.requestUpdate(null)).doesNotThrowAnyException();
        assertThatCode(() -> ModelDataHooks.requestUpdate(new Object())).doesNotThrowAnyException();
        assertThatCode(() -> ModelDataHooks.requestUpdate(new DataBlockEntity())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ModelDataProvider.requestModelDataUpdate() по умолчанию ничего не делает")
    void defaultRequestModelDataUpdateIsNoOp() {
        var provider = new DataBlockEntity();

        assertThatCode(provider::requestModelDataUpdate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("миксин BlockEntityMixin компилируется в этом сорсете и даёт ModelDataProvider")
    void blockEntityMixinCompiles() throws Exception {
        // Заодно страховка, что compat/java/nfcompat/mixin собирается здесь: ему нужен
        // org.spongepowered.asm, который приезжает транзитивно с fabric-loader.
        // Саму аннотацию @Mixin проверить нечем — у неё RetentionPolicy.CLASS, в рантайме
        // её не видно, поэтому проверяем контракт: класс implements ModelDataProvider.
        Class<?> mixin = Class.forName("nfcompat.mixin.BlockEntityMixin");

        assertThat(nfcompat.model.ModelDataProvider.class).isAssignableFrom(mixin);
        assertThat(mixin.getMethod("getModelData").getDeclaringClass()).isEqualTo(mixin);
    }

    /**
     * BlockGetter — интерфейс, так что для теста хватает прокси, который отвечает
     * только на getBlockEntity и валится на всём остальном (если шим начнёт звать
     * что-то ещё, тест это покажет).
     */
    private static BlockGetter fakeGetter(BlockEntity blockEntity) {
        return (BlockGetter) Proxy.newProxyInstance(
                ModelDataHooksTest.class.getClassLoader(),
                new Class<?>[] { BlockGetter.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getBlockEntity")) {
                        return blockEntity;
                    }
                    throw new UnsupportedOperationException("неожиданный вызов: " + method.getName());
                });
    }
}
