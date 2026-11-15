/*
 * Часть nfcompat. Расширение MenuType.
 *
 * NeoForge патчит MenuType, добавляя вариант с фабрикой, которой на клиенте
 * передаётся буфер с доп. данными. На Fabric аналог — `ExtendedMenuType<D>` с
 * `StreamCodec` на эти данные. Данные у нас непрозрачные (AE2 пишет в буфер что
 * хочет), поэтому D = byte[]: сервер отдаёт байты как есть, клиент заворачивает
 * их обратно в `RegistryFriendlyByteBuf` и передаёт фабрике AE2.
 */
package net.neoforged.neoforge.common.extensions;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;

public interface IMenuTypeExtension<T> {
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static <T extends AbstractContainerMenu> MenuType<T> create(IContainerFactory<T> factory) {
        StreamCodec<RegistryFriendlyByteBuf, byte[]> codec = (StreamCodec) ByteBufCodecs.BYTE_ARRAY;
        return new ExtendedMenuType<T, byte[]>(
                (containerId, inventory, data) -> factory.create(containerId, inventory, toBuffer(inventory, data)),
                codec);
    }

    static RegistryFriendlyByteBuf toBuffer(Inventory inventory, byte[] data) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), inventory.player.registryAccess());
    }

    T create(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf extraData);
}
