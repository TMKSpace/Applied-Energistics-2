/*
 * Часть nfcompat. `Block#canEntityDestroy` для дракона Края.
 *
 * NeoForge патчит `EnderDragon#checkWalls`: чтение `MOB_GRIEFING` заменяется на
 * `CommonHooks.canEntityDestroy(level, blockPos, this)`, то есть спрашивает у блока,
 * можно ли ему разрушать. У AE2 на это отвечает `MatrixFrameBlock` (spatial-плот).
 *
 * ПОЧЕМУ НЕ `@ModifyExpressionValue` НА ЧТЕНИЕ ГЕЙМРУЛА.
 * В `checkWalls` три вызова `GameRules.get(...)` в трёх разных местах метода, и
 * `@ModifyExpressionValue` без `ordinal` бьёт по всем сразу. Плюс логика ванили такая:
 *
 *     if (!griefing || state.is(DRAGON_IMMUNE)) { hitWall = true; continue; }
 *     ... level.removeBlock(pos, false) ...
 *
 * то есть подмена значения геймрула вынуждает нас подделывать ветку `hitWall`, а не
 * запрет разрушения. Проще и честнее — обернуть сам `removeBlock`: если блок запрещает
 * разрушение сущностью, возвращаем `false` и ничего не трогаем (блок остаётся, дроп не
 * спавнится). `MOB_GRIEFING=false` при этом всё равно отсекает всё раньше, как в ванили.
 *
 * ХЕНДЛЕР БЕЗ САХАРА НАМЕРЕННО. У `@WrapOperation` в хвосте обязан быть `Operation`,
 * а MixinExtras требует хвостовых сахарных параметров - вместе они дают
 * "non-trailing sugared parameters". Здесь `pos` приходит аргументом вызова, `state`
 * берём из уровня, поэтому `@Local` не нужен вообще.
 *
 * ЛОГИ. Пишем в лог каждую попытку дракона сломать блок AE2 (`appeng` namespace) и
 * итог - этого достаточно, чтобы в игре понять, применяется ли хук, и не спамит на
 * обычном камне.
 *
 * ХРУПКОСТЬ. Держится на имени `checkWalls` и на вызове `ServerLevel.removeBlock(BlockPos, Z)`.
 * Переименование метода или смена owner'а вызова ломают старт громко (`require = 1`).
 */
package nfcompat.mixin.common;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EnderDragon.class)
public abstract class EnderDragonDestroyBlocksMixin {

    private static final Logger NFCOMPAT_LOGGER = LoggerFactory.getLogger("nfcompat/dragon");

    @WrapOperation(
            method = "checkWalls",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean nfcompat$canEntityDestroy(ServerLevel level, BlockPos pos, boolean moving,
            Operation<Boolean> original) {
        var state = level.getBlockState(pos);

        if (state.getBlock() instanceof IBlockExtension extension) {
            var dragon = (EnderDragon) (Object) this;
            boolean allowed = extension.canEntityDestroy(state, level, pos, dragon);

            if (!allowed) {
                if (nfcompat$isAe2Block(state.getBlock())) {
                    NFCOMPAT_LOGGER.info("dragon blocked by {} at {}", state.getBlock(), pos);
                }
                return false;
            }
        }

        if (nfcompat$isAe2Block(state.getBlock())) {
            NFCOMPAT_LOGGER.info("dragon destroyed {} at {}", state.getBlock(), pos);
        }
        return original.call(level, pos, moving);
    }

    private static boolean nfcompat$isAe2Block(net.minecraft.world.level.block.Block block) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        return id != null && "ae2".equals(id.getNamespace());
    }
}
