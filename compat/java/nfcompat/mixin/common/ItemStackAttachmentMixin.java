/*
 * Часть nfcompat. `ItemStack` -> `IAttachmentHolder`.
 *
 * В NeoForge `ItemStack` тоже держатель attachment'ов. Из замеренных аддонов этого
 * никто не использует, но интерфейс дешёвый (тела — default в IAttachmentHolder).
 *
 * ВАЖНО про семантику: хранилище ключуется по identity (см. nfcompat.attachments.Attachments),
 * поэтому attachment'ы НЕ переезжают в копию стака и НЕ чистятся при `setCount(0)`.
 * В NeoForge с этим разбирается `AttachmentType#copyHandler`, которого у нас нет.
 * То есть `stack.getData(...)` работает, а вот полагаться на перенос данных в копию нельзя.
 */
package nfcompat.mixin.common;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemStack.class)
public abstract class ItemStackAttachmentMixin implements IAttachmentHolder {
}
