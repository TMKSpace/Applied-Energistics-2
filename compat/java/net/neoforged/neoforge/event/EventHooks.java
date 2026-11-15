/*
 * Часть nfcompat.
 *
 * В NeoForge EventHooks — это десятки статических хуков, вызываемых из патченного
 * ванильного кода (размещение блока, взрыв, крафт, сон, харвест...). Нам ванильный
 * код патчить нечем, поэтому здесь только те хуки, которые AE2 зовёт сам.
 *
 * Если понадобится ещё какой-то — добавить сюда метод с той же сигнатурой и
 * постом соответствующего события на NeoForge.EVENT_BUS.
 */
package net.neoforged.neoforge.event;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerExplosion;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;

/**
 * Только используемое AE2 подмножество NeoForge EventHooks.
 */
public final class EventHooks {
    private EventHooks() {
    }

    public static void firePlayerCraftingEvent(Player player, ItemStack crafted, Container craftMatrix) {
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent(player, crafted,
                craftMatrix));
    }

    public static boolean onBlockPlace(@Nullable Entity entity, BlockSnapshot blockSnapshot, Direction direction) {
        var level = blockSnapshot.getLevel();
        if (level == null) {
            return false;
        }
        var placedAgainst = level.getBlockState(blockSnapshot.getPos().relative(direction.getOpposite()));
        var event = new net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent(blockSnapshot, placedAgainst, entity);
        return NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    public static void onExplosionDetonate(Level level, ServerExplosion explosion, List<Entity> entities,
            List<BlockPos> blocks) {
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ExplosionEvent.Detonate(level, explosion, entities,
                blocks));
    }
}
