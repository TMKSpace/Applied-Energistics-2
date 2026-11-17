/*
 * Часть nfcompat. Даёт всем блокам неофорджевский интерфейс `IBlockExtension`.
 *
 * Сам интерфейс добавляет `onNeighborChange(...)` с пустым дефолтом, поэтому миксин ничего не
 * меняет в поведении ванильных блоков: он нужен только чтобы `LevelNeighborChangeMixin` мог
 * спросить `block instanceof IBlockExtension` и позвать переопределение AE2 (у `CableBusBlock`
 * там уведомление частей о смене соседа).
 */
package nfcompat.mixin.common;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(Block.class)
public abstract class BlockNeighborChangeMixin implements IBlockExtension {
}
