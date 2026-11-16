/*
 * Часть nfcompat. ВРЕМЕННАЯ энерго-секция для `/nfcompat cap held` (предмет в руке).
 *
 * Показывает оба слоя моста энергии ПРЕДМЕТОВ:
 *   1. что видит Fabric: `EnergyStorage.ITEM.find(stack, context)` — класс,
 *      amount/capacity, supportsInsertion/supportsExtraction;
 *   2. что видит шим: `Capabilities.Energy.ITEM.getCapability(stack, access)` — класс
 *      и getAmountAsLong/getCapacityAsLong.
 * Ничего не пишет (энергию не тратит): это диагностика, а не тест.
 *
 * Вызывать только когда `FabricEnergy.isAvailable()`: класс трогает типы Team Reborn
 * и без мода не должен грузиться вообще.
 *
 * Удалить вместе с ItemCapabilityProbe, когда мост проверен в бою.
 */
package nfcompat.debug;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import team.reborn.energy.api.EnergyStorage;

public final class EnergyItemProbe {

    private EnergyItemProbe() {
    }

    public static void probe(CommandSourceStack source, Player player, ItemStack stack) {
        ContainerItemContext context = ContainerItemContext.forPlayerInteraction(player,
                InteractionHand.MAIN_HAND);

        EnergyStorage storage;
        try {
            storage = EnergyStorage.ITEM.find(stack, context);
        } catch (RuntimeException e) {
            CapabilityProbeCommand.say(source, ChatFormatting.RED,
                    "EnergyStorage.ITEM.find бросил " + e);
            storage = null;
        }
        if (storage == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "TeamReborn EnergyStorage.ITEM = null");
        } else {
            CapabilityProbeCommand.say(source, ChatFormatting.AQUA,
                    "TeamReborn EnergyStorage.ITEM = " + storage.getClass().getSimpleName()
                            + " amount=" + storage.getAmount() + "/" + storage.getCapacity()
                            + " insert=" + storage.supportsInsertion()
                            + " extract=" + storage.supportsExtraction());
        }

        ItemAccess access = ItemAccess.forPlayerInteraction(player, InteractionHand.MAIN_HAND);
        var handler = Capabilities.Energy.ITEM.getCapability(stack, access);
        if (handler == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "шим Capabilities.Energy.ITEM = null");
        } else {
            CapabilityProbeCommand.say(source, ChatFormatting.GREEN,
                    "шим Capabilities.Energy.ITEM = " + handler.getClass().getSimpleName()
                            + " amount=" + handler.getAmountAsLong()
                            + "/" + handler.getCapacityAsLong());
        }
    }
}
