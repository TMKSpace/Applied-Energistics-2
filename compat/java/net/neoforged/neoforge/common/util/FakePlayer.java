/*
 * Часть nfcompat. Фейковый игрок.
 *
 * В NeoForge это ServerPlayer с пометкой «неродной», которого моды используют как
 * контекст действия (крафт, размещение, добыча). AE2 проверяет `instanceof FakePlayer`
 * и берёт его из Platform.getFakePlayer. Отдельного поведения классу не нужно —
 * важно, что это именно наш тип, а не любой ServerPlayer.
 */
package net.neoforged.neoforge.common.util;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class FakePlayer extends ServerPlayer {
    public FakePlayer(ServerLevel level, GameProfile profile) {
        // ClientInformation обязателен: ServerPlayer#updateOptions в 26.3 дёргает
        // information.language(). NeoForge тоже передаёт createDefault(), а не null.
        // Раньше здесь брали clientInformation() первого игрока в списке, но на
        // выделенном сервере (и в геймтестах) список пуст -> NPE ещё в конструкторе.
        super(level.getServer(), level, profile, ClientInformation.createDefault());
    }

    /** Ничего не отправляем на клиент: такого игрока там нет. */
    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    /**
     * У фиктивного игрока нет сетевого соединения, а ванильный
     * {@code ServerPlayer#closeContainer} шлёт клиенту пакет через {@code connection}.
     * В геймтестах AE2 этот метод зовётся напрямую (CraftingTerminalTestPlots),
     * и без заглушки сервер падал с NPE.
     */
    @Override
    public void closeContainer() {
    }

    @Override
    public String toString() {
        return "FakePlayer[" + getUUID() + "]";
    }

    static {
        // ссылка, чтобы не потерять связь типов при будущем рефакторинге
        assert Player.class != null;
    }
}
