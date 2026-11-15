/*
 * Часть nfcompat. Общие хуки NeoForge.
 *
 * В NeoForge это огромный класс (96 КБ) хуков во все места ванильного кода. AE2 зовёт
 * оттуда ровно три вещи, они и написаны:
 *  - setCraftingPlayer/getCraftingPlayer — «кто крафтит прямо сейчас», нужно рецептам,
 *    которые портят инструмент при крафте (у ванили такого понятия нет);
 *  - onLeftClickBlock — создаёт событие левого клика, AE2 сама его постит.
 */
package net.neoforged.neoforge.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

public final class CommonHooks {
    /**
     * В NeoForge это делает EventHooks через стек вызовов. У нас нет патча в
     * ванильный крафт, поэтому значение ставит и снимает сам вызывающий
     * (AE2 так и делает: setCraftingPlayer(player) ... setCraftingPlayer(null)).
     */
    private static final ThreadLocal<Player> CRAFTING_PLAYER = new ThreadLocal<>();

    private CommonHooks() {
    }

    public static void setCraftingPlayer(@Nullable Player player) {
        if (player == null) {
            CRAFTING_PLAYER.remove();
        } else {
            CRAFTING_PLAYER.set(player);
        }
    }

    @Nullable
    public static Player getCraftingPlayer() {
        return CRAFTING_PLAYER.get();
    }

    /**
     * Создаёт (но не постит) событие левого клика по блоку. В NeoForge оно
     * отправляется из патченного клиента; здесь событие строит вызывающий.
     */
    public static PlayerInteractEvent.LeftClickBlock onLeftClickBlock(Player player, BlockPos pos, Direction face,
            ServerboundPlayerActionPacket.Action action) {
        // в событии свой enum (START/STOP/ABORT), у ванильного пакета — свой набор
        var eventAction = switch (action) {
            case START_DESTROY_BLOCK -> PlayerInteractEvent.LeftClickBlock.Action.START;
            case STOP_DESTROY_BLOCK -> PlayerInteractEvent.LeftClickBlock.Action.STOP;
            default -> PlayerInteractEvent.LeftClickBlock.Action.ABORT;
        };
        return new PlayerInteractEvent.LeftClickBlock(player, pos, face, eventAction);
    }
}
