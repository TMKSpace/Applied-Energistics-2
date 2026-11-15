/*
 * Часть nfcompat. См. {@link IItemStackExtension}.
 *
 * Отличие от пустышки: canConvertToSource в NeoForge патчит FluidState, добавляя
 * второй параметр (ServerLevel). У нас ванильный FluidState такого метода не имеет,
 * поэтому CreateFluidSourceEvent берёт ванильный вариант через FluidStateHooks.
 */
package net.neoforged.neoforge.common.extensions;

public interface IFluidStateExtension {
}
