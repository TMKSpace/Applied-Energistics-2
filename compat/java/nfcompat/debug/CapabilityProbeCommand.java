/*
 * Часть nfcompat. ВРЕМЕННАЯ команда-проба моста capability -> Fabric Transfer API.
 *
 * Зачем: когда Pattern Provider/шина «не видят» сундук, нужно за один прогон понять,
 * на каком слое рвётся — Fabric-ное хранилище, наш шим-capability или адаптер.
 *
 *   /nfcompat cap            — по блоку, на который смотрит игрок (до 6 блоков)
 *   /nfcompat cap <x> <y> <z> — по указанной позиции
 *
 * Печатает класс и размер хранилища на каждом слое и пробует вставку одного камня
 * (вставка НЕ откатывается — тестировать на мусорном сундуке).
 *
 * Удалить вместе с записью в LifecycleBridge, когда мост будет проверен в бою.
 */
package nfcompat.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.neoforge.capabilities.Capabilities;

public final class CapabilityProbeCommand {

    private CapabilityProbeCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nfcompat")
                .then(Commands.literal("cap")
                        .executes(ctx -> probeLookedAt(ctx.getSource()))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> probe(ctx.getSource(),
                                                        new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("parts")
                        .executes(ctx -> listParts(ctx.getSource(), null))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> listParts(ctx.getSource(),
                                                        new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("provider")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> probeProvider(ctx.getSource(),
                                                        new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                                                IntegerArgumentType.getInteger(ctx, "y"),
                                                                IntegerArgumentType.getInteger(ctx, "z"))))))))
                .then(Commands.literal("pp")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .then(Commands.argument("side", StringArgumentType.word())
                                                        .executes(ctx -> probePatternTarget(ctx.getSource(),
                                                                new BlockPos(IntegerArgumentType.getInteger(ctx, "x"),
                                                                        IntegerArgumentType.getInteger(ctx, "y"),
                                                                        IntegerArgumentType.getInteger(ctx, "z")),
                                                                StringArgumentType.getString(ctx, "side")))))))));
    }

    /** Перечислить части на кабель-шинах вокруг центра: где именно pattern provider. */
    private static int listParts(CommandSourceStack source, BlockPos explicitCenter) {
        var level = (ServerLevel) source.getLevel();
        BlockPos center = explicitCenter;
        if (center == null) {
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("[nfcompat] нужен игрок или координаты"));
                return 0;
            }
            center = player.blockPosition();
        }
        int found = 0;

        for (var pos : BlockPos.betweenClosed(center.offset(-8, -4, -8), center.offset(8, 4, 8))) {
            var be = level.getBlockEntity(pos);
            if (!(be instanceof appeng.blockentity.networking.CableBusBlockEntity cableBus)) {
                continue;
            }
            for (var side : appeng.api.parts.IPart.ATTACHMENT_POINTS) {
                var part = cableBus.getPart(side);
                if (part == null) {
                    continue;
                }
                found++;
                var posCopy = pos.immutable();
                var kind = part instanceof appeng.api.networking.crafting.ICraftingProvider
                        ? " [CRAFTING PROVIDER]"
                        : "";
                say(source, ChatFormatting.YELLOW, "часть " + posCopy.toShortString() + " сторона=" + side
                        + " " + part.getClass().getSimpleName() + kind);
            }
        }

        say(source, ChatFormatting.GOLD, "частей найдено: " + found);
        return found;
    }

    /**
     * Что видит сам Pattern Provider: шаблоны, активность узла и цели по сторонам.
     */
    private static int probeProvider(CommandSourceStack source, BlockPos pos) {
        var level = (ServerLevel) source.getLevel();
        var be = level.getBlockEntity(pos);
        say(source, ChatFormatting.GOLD, "блок " + pos.toShortString() + " be="
                + (be == null ? "null" : be.getClass().getSimpleName()));

        if (!(be instanceof appeng.helpers.patternprovider.PatternProviderLogicHost host)) {
            source.sendFailure(Component.literal("[nfcompat] это не pattern provider"));
            return 0;
        }

        var logic = host.getLogic();
        var patterns = logic.getAvailablePatterns();
        say(source, ChatFormatting.AQUA, "шаблонов=" + patterns.size()
                + " grid=" + (logic.getGrid() != null));

        for (var pattern : patterns) {
            say(source, ChatFormatting.AQUA, "  шаблон " + pattern
                    + " external=" + pattern.supportsPushInputsToExternalInventory()
                    + " outputs=" + pattern.getOutputs());
        }

        // Чем именно провайдер будет пушить: первый возможный вход первого шаблона
        appeng.api.stacks.AEKey probeKey = appeng.api.stacks.AEItemKey.of(Items.STONE);
        if (!patterns.isEmpty()) {
            var inputs = patterns.get(0).getInputs();
            if (inputs.length > 0) {
                var possible = inputs[0].getPossibleInputs();
                if (possible.length > 0) {
                    probeKey = possible[0].what();
                }
            }
        }

        for (var side : Direction.values()) {
            var adjPos = pos.relative(side);
            var adjBe = level.getBlockEntity(adjPos);
            var target = appeng.helpers.patternprovider.PatternProviderTarget.get(
                    level, adjPos, adjBe, side.getOpposite(),
                    appeng.api.networking.security.IActionSource.empty());
            if (target == null) {
                continue;
            }
            var simulated = target.insert(probeKey, 1, appeng.api.config.Actionable.SIMULATE);
            say(source, ChatFormatting.GREEN, "сторона " + side + " -> цель есть, insert(SIMULATE "
                    + probeKey.getDisplayName().getString() + ") = " + simulated);
        }

        return 1;
    }

    /**
     * Симуляция PatternProviderTarget.get для позиции/стороны: то, что увидит
     * Pattern Provider, когда попробует пушить в соседний блок.
     */    private static int probePatternTarget(CommandSourceStack source, BlockPos pos, String sideName) {
        Direction side;
        try {
            side = Direction.valueOf(sideName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("[nfcompat] нет такой стороны: " + sideName));
            return 0;
        }

        var level = (ServerLevel) source.getLevel();
        var be = level.getBlockEntity(pos);
        var target = appeng.helpers.patternprovider.PatternProviderTarget.get(
                level, pos, be, side, appeng.api.networking.security.IActionSource.empty());

        say(source, ChatFormatting.GOLD, "PatternProviderTarget " + pos.toShortString() + " side=" + side
                + " -> " + (target == null ? "null" : target.getClass().getSimpleName()));

        if (target != null) {
            var stone = appeng.api.stacks.AEItemKey.of(Items.STONE);
            var inserted = target.insert(stone, 1, appeng.api.config.Actionable.SIMULATE);
            say(source, ChatFormatting.GREEN, "insert(SIMULATE, stone) = " + inserted);
        }
        return 1;
    }

    private static int probeLookedAt(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("[nfcompat] нужен игрок в мире"));
            return 0;
        }
        var hit = player.pick(6.0, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("[nfcompat] не смотрю на блок"));
            return 0;
        }
        return probe(source, ((BlockHitResult) hit).getBlockPos());
    }

    private static int probe(CommandSourceStack source, BlockPos pos) {
        var level = source.getLevel();
        var state = level.getBlockState(pos);
        var be = level.getBlockEntity(pos);

        say(source, ChatFormatting.GOLD, "позиция " + pos.toShortString()
                + " блок=" + state.getBlock().builtInRegistryHolder().key().identifier()
                + " be=" + (be == null ? "null" : be.getClass().getSimpleName()));

        // 1. Что видит сам Fabric
        var itemAstorage = ItemStorage.SIDED.find(level, pos, state, be, Direction.UP);
        var fluidStorage = FluidStorage.SIDED.find(level, pos, state, be, Direction.UP);
        say(source, ChatFormatting.AQUA, "Fabric ItemStorage.SIDED(UP)=" + describe(itemAstorage)
                + "  FluidStorage.SIDED(UP)=" + describe(fluidStorage));

        // 2. Что видит шим
        var shimItems = Capabilities.Item.BLOCK.getCapability(level, pos, state, be, Direction.UP);
        say(source, ChatFormatting.AQUA, "шим Capabilities.Item.BLOCK(UP)="
                + (shimItems == null ? "null" : shimItems.getClass().getSimpleName()
                        + " size=" + shimItems.size()));

        // 3. Пробная вставка через оба пути (камнем)
        var stone = ItemVariant.of(Items.STONE);
        if (itemAstorage != null) {
            try (var tx = Transaction.openOuter()) {
                var inserted = itemAstorage.insert(stone, 1, tx);
                say(source, ChatFormatting.GREEN, "Fabric insert(stone)= " + inserted
                        + " (вставка НЕ откатывается)");
                tx.commit();
            } catch (RuntimeException e) {
                say(source, ChatFormatting.RED, "Fabric insert бросил " + e);
            }
        }
        if (shimItems != null) {
            var resource = net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STONE);
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                var inserted = shimItems.insert(0, resource, 1, tx);
                say(source, ChatFormatting.GREEN, "шим insert(stone) index0 = " + inserted);
                tx.commit();
            } catch (RuntimeException e) {
                say(source, ChatFormatting.RED, "шим insert бросил " + e);
            }
        }

        return 1;
    }

    private static String describe(Object storage) {
        if (storage == null) {
            return "null";
        }
        var name = storage.getClass().getSimpleName();
        if (storage instanceof net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage<?> slotted) {
            var first = slotted.getSlotCount() > 0 ? slotted.getSlot(0) : null;
            var res = first == null ? "-" : String.valueOf(first.getResource()).substring(0,
                    Math.min(40, String.valueOf(first.getResource()).length()));
            return name + " slots=" + slotted.getSlotCount() + " slot0=" + res + "@"
                    + (first == null ? 0 : first.getAmount());
        }
        if (storage instanceof SingleSlotStorage<?> single) {
            return name + " single amount=" + single.getAmount();
        }
        return name;
    }

    private static void say(CommandSourceStack source, ChatFormatting color, String text) {
        source.sendSuccess(() -> Component.literal("[nfcompat] " + text).withStyle(color), false);
        System.out.println("[nfcompat/cap] " + text);
    }
}
