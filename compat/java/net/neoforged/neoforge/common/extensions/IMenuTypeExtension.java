/*
 * Часть nfcompat. Расширение MenuType.
 *
 * NeoForge патчит MenuType, добавляя вариант с фабрикой, которой на клиенте
 * передаётся буфер с доп. данными. У Fabric для этого есть
 * ExtendedScreenHandlerType, но он требует PacketCodec на доп. данные, а AE2
 * передаёт через этот путь свой MenuLocator.
 *
 * TODO: заменить на ExtendedScreenHandlerType, когда дойдём до сети и меню.
 * Сейчас реализация намеренно падает громко: молчаливый null сломал бы регистрацию
 * меню гораздо позже и непонятнее.
 */
package net.neoforged.neoforge.common.extensions;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;

public interface IMenuTypeExtension<T> {
    static <T extends AbstractContainerMenu> MenuType<T> create(IContainerFactory<T> factory) {
        throw new UnsupportedOperationException(
                "nfcompat: IMenuTypeExtension.create ещё не портирован (нужен ExtendedScreenHandlerType)");
    }

    T create(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf extraData);
}
