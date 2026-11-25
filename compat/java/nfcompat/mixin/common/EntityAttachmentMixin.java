/*
 * Часть nfcompat. `Entity` -> `IAttachmentHolder`.
 *
 * В NeoForge `Entity` (и через него `Player`, `ServerPlayer`, ...) реализует
 * `net.neoforged.neoforge.attachment.IAttachmentHolder` прямо в патченной ванили,
 * поэтому аддон компилируется с вызовом `player.getData(SUPPLIER)` — и в байткоде
 * получается `invokevirtual net/minecraft/world/entity/player/Player.getData(Supplier)`.
 * Без этого миксина такой вызов падает NoSuchMethodError'ом (проверено javap'ом
 * на ae2wtlib-26.3.2: CraftingTerminalHandler зовёт именно Player.getData(Supplier)).
 *
 * Тела методов уже есть — они default в самом интерфейсе и делегируют в
 * nfcompat.attachments.Attachments, так что миксин только навешивает интерфейс.
 * Это осознанно: минимум кода, который может сломаться при обновлении MC.
 */
package nfcompat.mixin.common;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Entity.class)
public abstract class EntityAttachmentMixin implements IAttachmentHolder {
}
