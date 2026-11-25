/*
 * Часть nfcompat. `Level` -> `IAttachmentHolder`.
 *
 * NeoForge навешивает интерфейс на `Level` (данные мира, например кэши на серверной
 * стороне). Аддоны из нашего замера на `Level` attachment'ы не вешают, но проверка
 * `level instanceof IAttachmentHolder` встречается в обвязке NeoForge-модов, а стоит
 * этот миксин ноль строк кода: тела методов — default в интерфейсе.
 */
package nfcompat.mixin.common;

import net.minecraft.world.level.Level;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Level.class)
public abstract class LevelAttachmentMixin implements IAttachmentHolder {
}
