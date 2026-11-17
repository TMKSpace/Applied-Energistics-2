/*
 * Часть nfcompat. Неофорджевский `Block#onNeighborChange` — вызов при смене соседа.
 *
 * ГДЕ. NeoForge дергает его из `Level#updateNeighbourForOutputSignal(BlockPos, Block)`:
 * проходит по всем шести направлениям (сначала горизонтальные, потом вертикальные), и для
 * каждого загруженного соседа зовёт `state.onNeighborChange(this, relativePos, pos)`.
 * Ваниль в этом методе обходит только горизонтальные направления и никакого хука не зовёт,
 * поэтому у нас `CableBusBlock#onNeighborChange` (уведомление частей) был мёртв: шина хранения
 * не узнавала, что рядом поставили/убрали контейнер.
 *
 * Реализуем ту же семантику: перебираем шесть направлений и дергаем наш `IBlockExtension`
 * у блока-соседа. Список направлений упорядочен как в NeoForge (горизонтальные первыми) —
 * это важно для редстоун-поведения, которое зависит от порядка уведомлений.
 */
package nfcompat.mixin.common;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Level.class)
public abstract class LevelNeighborChangeMixin {

    /** NeoForge: горизонтальные направления первыми, затем вертикальные. */
    private static final List<Direction> NFCOMPAT_NEIGHBOR_DIRECTIONS = List.of(
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST,
            Direction.UP, Direction.DOWN);

    @Inject(method = "updateNeighbourForOutputSignal", at = @At("HEAD"))
    private void nfcompat$notifyNeighborChange(BlockPos pos, Block changedBlock, CallbackInfo ci) {
        var level = (Level) (Object) this;

        for (var direction : NFCOMPAT_NEIGHBOR_DIRECTIONS) {
            var neighborPos = pos.relative(direction);
            if (!level.hasChunkAt(neighborPos)) {
                continue;
            }

            BlockState neighborState = level.getBlockState(neighborPos);
            Block neighborBlock = neighborState.getBlock();
            if (neighborBlock instanceof IBlockExtension extension) {
                extension.onNeighborChange(neighborState, level, neighborPos, pos);
            }
        }
    }
}
