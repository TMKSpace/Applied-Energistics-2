/*
 * Часть nfcompat. Фабрика контейнеров с дополнительными данными.
 *
 * В NeoForge это расширение MenuType: сервер шлёт вместе с открытием меню
 * произвольный буфер, клиент отдаёт его фабрике.
 */
package net.neoforged.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

@FunctionalInterface
public interface IContainerFactory<T extends AbstractContainerMenu> {
    T create(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf data);
}
