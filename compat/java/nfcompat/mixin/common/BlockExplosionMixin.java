/*
 * Часть nfcompat. Неофорджевский хук `Block#onBlockExploded` — "блок сам решает, что с ним
 * сделает взрыв".
 *
 * ГДЕ. В NeoForge `BlockBehaviour#onExplosionHit` пропатчен: дропы считаются как в ванили, а
 * вместо `level.setBlockAndUpdate(pos, AIR)` + `block.wasExploded(...)` в конце вызывается
 * `state.onBlockExploded(level, pos, explosion)` -> `IBlockExtension#onBlockExploded`. Дефолт
 * хука делает ровно то, что делала ваниль, а переопределение может блок сохранить. В AE2 так
 * делает `MatrixFrameBlock` ("immune to explosions"): в FABRIC-ветке его тела остались без
 * `@Override` и без вызова, поэтому рамку spatial-плота сносило взрывом.
 *
 * ТОЧКА ВКЛИНА. Ванильный 26.3
 * `BlockBehaviour#onExplosionHit(BlockState, ServerLevel, BlockPos, Explosion,
 * BiConsumer<ItemStack, BlockPos>)` сначала зовёт `spawnAfterBreak` и собирает дропы через
 * `getDrops(...).forEach(...)` (спавнит их `ServerExplosion` уже после возврата), а в самом
 * конце делает `level.setBlockAndUpdate(pos, Blocks.AIR)` и `block.wasExploded(...)`.
 * Вклиниваемся перед `setBlockAndUpdate` и отменяем остаток метода: дропы уже собраны, а
 * удаление блока отдаём шиму. Для блоков без переопределения дефолт хука повторяет ваниль
 * (`setBlock(..., 3)` + `wasExploded`), так что их поведение не меняется.
 *
 * ХРУПКОСТЬ. Таргет привязан к owner'у вызова: в 26.3 это `ServerLevel`, хотя сам
 * `setBlockAndUpdate(BlockPos, BlockState)` — дефолтный метод `LevelWriter`. Если при обновлении
 * MC компилятор начнёт ссылаться на `Level`/`LevelWriter` (или метод переименуют), инжектор не
 * найдёт точку и миксин упадёт на старте (required) — лечится правкой target.
 */
package nfcompat.mixin.common;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public abstract class BlockExplosionMixin {

    @Inject(
            method = "onExplosionHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z",
                    shift = At.Shift.BEFORE),
            cancellable = true)
    private void nfcompat$onBlockExploded(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
            BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        // `Block` реализует `IBlockExtension` через `BlockNeighborChangeMixin`, так что ветка
        // срабатывает для всех блоков, а переопределения AE2 (`MatrixFrameBlock`) подхватываются
        // виртуально. Если интерфейс почему-то не навесился — не отменяем метод и не падаем кастом,
        // блок просто уходит в ванильную ветку.
        if (state.getBlock() instanceof IBlockExtension extension) {
            extension.onBlockExploded(state, level, pos, explosion);
            ci.cancel();
        }
    }
}
