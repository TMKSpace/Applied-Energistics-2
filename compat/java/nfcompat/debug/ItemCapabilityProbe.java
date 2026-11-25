/*
 * Часть nfcompat. ВРЕМЕННАЯ проверка item-capability: `/nfcompat cap held`.
 *
 * Зачем: после моста `Capabilities.*.ITEM` <-> `FluidStorage.ITEM` / `ItemStorage.ITEM` /
 * `EnergyStorage.ITEM` надо за один прогон увидеть, что видит каждый слой. Берём предмет
 * из главной руки и печатаем:
 *   1. Fabric-ные lookup'ы — класс хранилища, слоты/объём/энергию;
 *   2. шим `Capabilities.*.ITEM` — то же самое через `ItemAccess`.
 * Ничего не меняет: только чтение (в отличие от `/nfcompat cap`, где пробная вставка
 * камня коммитится).
 *
 * Проверять так:
 *   - ведро воды -> в жидкостной секции вода 81000 droplets (ведро);
 *   - пустое ведро -> хранилище всё равно не null (в него можно налить), объём 0;
 *   - энергоячейка/заряжаемый инструмент AE2 -> энергия X/Y (и в Fabric, и в шиме).
 *
 * Энерго-секция живёт в EnergyItemProbe: без мода team_reborn_energy этот класс не
 * грузится вообще (см. FabricEnergy).
 *
 * Удалить вместе с CapabilityProbeCommand, когда мост проверен в бою.
 */
package nfcompat.debug;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class ItemCapabilityProbe {

    private ItemCapabilityProbe() {
    }

    public static int probeHeld(CommandSourceStack source) {
        Player player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[nfcompat] нужен игрок в мире"));
            return 0;
        }

        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal(
                    "[nfcompat] в главной руке пусто — возьми ведро или энергоячейку"));
            return 0;
        }

        CapabilityProbeCommand.say(source, ChatFormatting.GOLD,
                "предмет " + BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());

        probeFluid(source, player, stack);
        probeItems(source, player, stack);

        if (nfcompat.bridge.FabricEnergy.isAvailable()) {
            EnergyItemProbe.probe(source, player, stack);
        } else {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "энергия: мода team_reborn_energy нет — секция пропущена");
        }

        return 1;
    }

    private static void probeFluid(CommandSourceStack source, Player player, ItemStack stack) {
        ContainerItemContext context = ContainerItemContext
                .forPlayerInteraction(player, InteractionHand.MAIN_HAND);

        Storage<FluidVariant> fabric = null;
        try {
            fabric = FluidStorage.ITEM.find(stack, context);
        } catch (RuntimeException e) {
            CapabilityProbeCommand.say(source, ChatFormatting.RED,
                    "FluidStorage.ITEM.find бросил " + e);
        }

        if (fabric == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "Fabric FluidStorage.ITEM = null");
        } else {
            CapabilityProbeCommand.say(source, ChatFormatting.AQUA,
                    "Fabric FluidStorage.ITEM = " + fabric.getClass().getSimpleName()
                            + " " + describeFluidStorage(fabric));
        }

        // Шим: тут либо провайдер мода, либо наш мост.
        ItemAccess access = ItemAccess.forPlayerInteraction(player, InteractionHand.MAIN_HAND);
        var shim = Capabilities.Fluid.ITEM.getCapability(stack, access);
        if (shim == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "шим Capabilities.Fluid.ITEM = null");
        } else {
            FluidResource resource = shim.getResource(0);
            CapabilityProbeCommand.say(source, ChatFormatting.GREEN,
                    "шим Capabilities.Fluid.ITEM = " + shim.getClass().getSimpleName()
                            + " size=" + shim.size()
                            + " slot0=" + describeNfFluid(resource)
                            + "@" + shim.getAmountAsLong(0)
                            + "/" + shim.getCapacityAsLong(0, resource));
        }
    }

    private static void probeItems(CommandSourceStack source, Player player, ItemStack stack) {
        ContainerItemContext context = ContainerItemContext
                .forPlayerInteraction(player, InteractionHand.MAIN_HAND);

        Storage<ItemVariant> fabric = null;
        try {
            fabric = ItemStorage.ITEM.find(stack, context);
        } catch (RuntimeException e) {
            CapabilityProbeCommand.say(source, ChatFormatting.RED,
                    "ItemStorage.ITEM.find бросил " + e);
        }
        if (fabric == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "Fabric ItemStorage.ITEM = null");
        } else {
            CapabilityProbeCommand.say(source, ChatFormatting.AQUA,
                    "Fabric ItemStorage.ITEM = " + fabric.getClass().getSimpleName()
                            + " " + describeItemStorage(fabric));
        }

        ItemAccess access = ItemAccess.forPlayerInteraction(player, InteractionHand.MAIN_HAND);
        var shimItems = Capabilities.Item.ITEM.getCapability(stack, access);
        if (shimItems == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "шим Capabilities.Item.ITEM = null");
        } else {
            ItemResource resource = shimItems.getResource(0);
            CapabilityProbeCommand.say(source, ChatFormatting.GREEN,
                    "шим Capabilities.Item.ITEM = " + shimItems.getClass().getSimpleName()
                            + " size=" + shimItems.size()
                            + " slot0=" + (resource.isEmpty() ? "-" : resource.toString())
                            + "@" + shimItems.getAmountAsLong(0)
                            + "/" + shimItems.getCapacityAsLong(0, resource));
        }
    }

    private static String describeFluidStorage(Storage<FluidVariant> storage) {
        StringBuilder sb = new StringBuilder("slots=").append(slotCount(storage)).append(" [");
        boolean first = true;
        for (StorageView<FluidVariant> view : storage) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            if (view.isResourceBlank()) {
                sb.append("-@0/").append(nf(view.getCapacity()));
            } else {
                sb.append(view.getResource().getFluid())
                        .append('@').append(nf(view.getAmount()))
                        .append('/').append(nf(view.getCapacity()));
            }
        }
        return sb.append(']').toString();
    }

    private static String describeItemStorage(Storage<ItemVariant> storage) {
        StringBuilder sb = new StringBuilder("slots=").append(slotCount(storage)).append(" [");
        boolean first = true;
        for (StorageView<ItemVariant> view : storage) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(view.isResourceBlank() ? "-" : String.valueOf(view.getResource().getItem()))
                    .append('@').append(view.getAmount());
        }
        return sb.append(']').toString();
    }

    private static String describeNfFluid(FluidResource resource) {
        if (resource.isEmpty()) {
            return "-";
        }
        return String.valueOf(BuiltInRegistries.FLUID.getKey(resource.getFluid()));
    }

    private static int slotCount(Storage<?> storage) {
        return storage instanceof SlottedStorage<?> slotted ? slotted.getSlotCount() : 1;
    }

    /** Юниты общие (droplets, ведро = 81000); обёртка осталась от времён пересчёта. */
    private static long nf(long fabricAmount) {
        return nfcompat.bridge.FluidUnits.toNfLong(fabricAmount);
    }
}
