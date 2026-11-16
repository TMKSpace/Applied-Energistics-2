/*
 * Часть nfcompat. `net.minecraft.world.item.Item implements IItemExtension`.
 *
 * В NeoForge `IItemExtension` — это патч самого класса `Item`, поэтому переопределения в
 * предметах AE2 (`onItemUseFirst`, `doesSneakBypassUse`) компилируются с `@Override` и
 * вызываются движком. На Fabric ядро не патчено, и без этого миксина у `FacadeItem`,
 * `UpgradeCardItem`, `MemoryCardItem` и остальных методы просто лежат мёртвым грузом:
 * их никто не вызывает.
 *
 * Миксин добавляет только интерфейс, все методы — `default`, так что поведение ванильных
 * (и любых чужих) предметов не меняется: они отвечают `PASS`/`false`.
 *
 * В `src` AE2 эти методы уже обрамлены `//#if FABRIC //#else @Override //#endif`, поэтому
 * после миксина они становятся настоящими переопределениями и на Fabric — править `src` не нужно.
 */
package nfcompat.mixin;

import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.extensions.IItemExtension;

@Mixin(Item.class)
public abstract class ItemExtensionMixin implements IItemExtension {
}
