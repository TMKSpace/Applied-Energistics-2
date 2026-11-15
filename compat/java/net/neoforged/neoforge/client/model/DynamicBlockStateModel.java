/*
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
