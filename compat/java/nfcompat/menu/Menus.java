/*
 * Часть nfcompat. Открытие меню с дополнительными данными.
 *
 * NeoForge патчит `ServerPlayer#openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)`:
 * данные, которые мод допишет в буфер, уезжают клиенту вместе с открытием меню.
 * На Fabric это делает `ExtendedMenuProvider#getScreenOpeningData`, но AE2 свой
 * провайдер не расширяет и не знает про Fabric, поэтому оборачиваем его здесь:
 * обёртка делегирует всё прежнее поведение и добавляет сериализацию данных.
 *
 * Формат данных для нас непрозрачный: AE2 сама решает, что писать в буфер. Мы
 * складываем байты как есть и на клиенте заворачиваем обратно в буфер.
 */
package nfcompat.menu;

import java.util.function.Consumer;

import io.netty.buffer.Unpooled;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

public final class Menus {
    private Menus() {
    }

    /** Оборачивает провайдер AE2 в Fabric-провайдер с данными открытия. */
    public static MenuProvider withData(MenuProvider delegate, Consumer<RegistryFriendlyByteBuf> writer) {
        return new ExtendedMenuProvider<byte[]>() {
            @Override
            public Component getDisplayName() {
                return delegate.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return delegate.createMenu(containerId, inventory, player);
            }

            // nfcompat: `shouldTriggerClientSideContainerClosingOnOpen` — метод NeoForge
            // (в ванильном MenuProvider его нет), поэтому поведение «не закрывать меню
            // при переходе между AE-меню» здесь не воспроизводится: ваниль закроет
            // предыдущее меню. TODO: если это будет мешать, разобраться с закрытием.

            @Override
            public byte[] getScreenOpeningData(ServerPlayer player) {
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
                try {
                    writer.accept(buffer);
                    var data = new byte[buffer.readableBytes()];
                    buffer.readBytes(data);
                    return data;
                } finally {
                    buffer.release();
                }
            }
        };
    }
}
