/*
 * Часть nfcompat. Расширение ванильного `Item` в стиле NeoForge.
 *
 * В NeoForge `IItemExtension` вплетается прямо в `net.minecraft.world.item.Item` (патч ядра),
 * поэтому любой предмет неявно получает дефолтные реализации, а AE2 просто переопределяет
 * нужные методы (`FacadeItem`, `UpgradeCardItem`, `MemoryCardItem`, ...). На Fabric ядро не
 * патчено, поэтому интерфейс добавляем через `nfcompat.mixin.common.ItemExtensionMixin`.
 *
 * Здесь только те два метода, которые реально нужны AE2. Полный интерфейс NeoForge огромен
 * и целиком нам не нужен: добавляй по мере надобности, а не "на всякий случай".
 *
 * Кто вызывает: `nfcompat.bridge.ItemUseFirstBridge` (аналог неофорджевской точки
 * `Item#onItemUseFirst` в патченном `ServerPlayerGameMode/MultiPlayerGameMode#useItemOn`).
 */
package net.neoforged.neoforge.common.extensions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LevelReader;

public interface IItemExtension {
    /**
     * Вызывается при ПКМ предметом по блоку ДО ванильной обработки блока (и до `Item#useOn`).
     * Не-`PASS` результат отменяет ванильную обработку клика.
     */
    default InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return InteractionResult.PASS;
    }

    /**
     * `true` — при шифт-ПКМ ваниль не должна подавлять обработку блока (в NeoForge этот флаг
     * снимает проверку `isSecondaryUseActive` в `ServerPlayerGameMode#useItemOn`).
     */
    default boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return false;
    }
}
