/*
 * Часть nfcompat. ВРЕМЕННАЯ энерго-секция команды `/nfcompat cap` (см. CapabilityProbeCommand).
 *
 * Показывает оба слоя моста энергии плюс пробные insert/extract по 1000 FE:
 *   1. что видит Fabric: `EnergyStorage.SIDED.find(...)` — класс, amount/capacity,
 *      supportsInsertion/supportsExtraction;
 *   2. что видит шим: `Capabilities.Energy.BLOCK.getCapability(...)` — класс и
 *      getAmountAsLong/getCapacityAsLong;
 *   3. insert(1000) / extract(1000) через каждую сторону (НЕ откатываются).
 *
 * Вызывать только когда `FabricEnergy.isAvailable()`: класс трогает типы Team Reborn
 * и без мода не должен грузиться вообще.
 *
 * Удалить вместе с CapabilityProbeCommand, когда мост проверен в бою.
 */
package nfcompat.debug;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

public final class EnergyProbe {

    private static final Direction SIDE = Direction.UP;
    private static final long PROBE_AMOUNT = 1000;

    private EnergyProbe() {
    }

    public static void probe(CommandSourceStack source, Level level, BlockPos pos,
            @Nullable BlockState state, @Nullable BlockEntity blockEntity) {
        // 1. Fabric-ная сторона
        EnergyStorage storage;
        try {
            storage = EnergyStorage.SIDED.find(level, pos, state, blockEntity, SIDE);
        } catch (RuntimeException e) {
            CapabilityProbeCommand.say(source, ChatFormatting.RED,
                    "EnergyStorage.SIDED.find бросил " + e);
            storage = null;
        }
        if (storage == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "TeamReborn EnergyStorage.SIDED(UP)= null");
        } else {
            CapabilityProbeCommand.say(source, ChatFormatting.AQUA,
                    "TeamReborn EnergyStorage.SIDED(UP)= " + storage.getClass().getSimpleName()
                            + " amount=" + storage.getAmount() + "/" + storage.getCapacity()
                            + " insert=" + storage.supportsInsertion()
                            + " extract=" + storage.supportsExtraction());
        }

        // 2. Сторона шима
        EnergyHandler handler = Capabilities.Energy.BLOCK.getCapability(level, pos, state,
                blockEntity, SIDE);
        if (handler == null) {
            CapabilityProbeCommand.say(source, ChatFormatting.DARK_GRAY,
                    "шим Capabilities.Energy.BLOCK(UP)= null");
        } else {
            CapabilityProbeCommand.say(source, ChatFormatting.AQUA,
                    "шим Capabilities.Energy.BLOCK(UP)= " + handler.getClass().getSimpleName()
                            + " amount=" + handler.getAmountAsLong()
                            + "/" + handler.getCapacityAsLong());
        }

        // 3. Пробные операции. Вставка/извлечение НЕ откатываются — тестировать на мусоре.
        if (storage != null) {
            try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                long inserted = storage.insert(PROBE_AMOUNT, tx);
                tx.commit();
                CapabilityProbeCommand.say(source, ChatFormatting.GREEN,
                        "Fabric insert(" + PROBE_AMOUNT + " FE)= " + inserted + " (НЕ откатывается)");
            } catch (RuntimeException e) {
                CapabilityProbeCommand.say(source, ChatFormatting.RED, "Fabric insert бросил " + e);
            }
            try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
                long extracted = storage.extract(PROBE_AMOUNT, tx);
                tx.commit();
                CapabilityProbeCommand.say(source, ChatFormatting.GREEN,
                        "Fabric extract(" + PROBE_AMOUNT + " FE)= " + extracted + " (НЕ откатывается)");
            } catch (RuntimeException e) {
                CapabilityProbeCommand.say(source, ChatFormatting.RED, "Fabric extract бросил " + e);
            }
        }
        if (handler != null) {
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int inserted = handler.insert((int) PROBE_AMOUNT, tx);
                tx.commit();
                CapabilityProbeCommand.say(source, ChatFormatting.GREEN,
                        "шим insert(" + PROBE_AMOUNT + " FE)= " + inserted + " (НЕ откатывается)");
            } catch (RuntimeException e) {
                CapabilityProbeCommand.say(source, ChatFormatting.RED, "шим insert бросил " + e);
            }
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                int extracted = handler.extract((int) PROBE_AMOUNT, tx);
                tx.commit();
                CapabilityProbeCommand.say(source, ChatFormatting.GREEN,
                        "шим extract(" + PROBE_AMOUNT + " FE)= " + extracted + " (НЕ откатывается)");
            } catch (RuntimeException e) {
                CapabilityProbeCommand.say(source, ChatFormatting.RED, "шим extract бросил " + e);
            }
        }
    }
}
