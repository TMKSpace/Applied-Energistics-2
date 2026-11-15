# -*- coding: utf-8 -*-
"""Батч: аттачменты, отправка пакетов, getSlotIndex, JEI-типы, мелкие сигнатуры."""
import io
import os
import re

ROOT = r"D:\Code\java\ae2-fabric-probe"
CJ = os.path.join(ROOT, "compat", "java")
SA = os.path.join(ROOT, "src", "main", "java")
SC = os.path.join(ROOT, "src", "client", "java")


def write(rel, text):
    p = os.path.join(CJ, rel.replace("/", os.sep))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with io.open(p, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("написан", rel)


def resolve(rel):
    for base in (SA, SC):
        p = os.path.join(base, rel.replace("/", os.sep))
        if os.path.exists(p):
            return p
    return None


def patch(rel, old, new, marker=True):
    path = resolve(rel)
    if not path:
        print("!! нет файла", rel)
        return False
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено:", rel, "::", old[:50])
        return False
    if marker:
        ls = text.rfind("\n", 0, text.index(old)) + 1
        indent = re.match(r'[ \t]*', text[ls:]).group(0)
        block = "%s//#if FABRIC\n%s%s\n%s//#else\n%s%s\n%s//#endif" % ("", indent, new, indent, indent, old, indent)
    else:
        block = new
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, block, 1))
    print("правка:", rel)
    return True


# ---------------- 1. Аттачменты ----------------
write("nfcompat/attachments/Attachments.java", '''/*
 * Часть nfcompat. Данные, привязанные к сущности.
 *
 * NeoForge патчит `Entity`/`BlockEntity`/`Level` до `IAttachmentHolder`, добавляя
 * `getData(AttachmentType)` / `setData(AttachmentType, value)`. На Fabric такого нет,
 * поэтому значения хранятся здесь: слабая ссылка на носитель -> карта тип->значение.
 *
 * Ограничения, которые надо помнить:
 *  - значения не сериализуются (в NeoForge это делает `AttachmentType#serialize`);
 *  - не синхронизируются с клиентом (`sync(...)`);
 *  - не переживают выгрузку сущности, если она выгружена и создана заново.
 * Для `AEAttachmentTypes.HOLDING_CTRL` (флаг «зажат Ctrl») этого достаточно.
 */
package nfcompat.attachments;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import net.neoforged.neoforge.attachment.AttachmentType;

public final class Attachments {
    private static final Map<Object, Map<AttachmentType<?>, Object>> VALUES = Collections
            .synchronizedMap(new WeakHashMap<>());

    private Attachments() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object holder, Supplier<AttachmentType<T>> type) {
        return get(holder, type.get());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Object holder, AttachmentType<T> type) {
        synchronized (VALUES) {
            var map = VALUES.get(holder);
            if (map != null && map.containsKey(type)) {
                return (T) map.get(type);
            }
        }
        return type.defaultValue();
    }

    public static <T> void set(Object holder, Supplier<AttachmentType<T>> type, T value) {
        set(holder, type.get(), value);
    }

    public static <T> void set(Object holder, AttachmentType<T> type, T value) {
        synchronized (VALUES) {
            VALUES.computeIfAbsent(holder, k -> new java.util.HashMap<>()).put(type, value);
        }
    }
}
''')

patch("appeng/client/AppEngClient.java",
      "var previousIsDown = player.getData(AEAttachmentTypes.HOLDING_CTRL);",
      "var previousIsDown = nfcompat.attachments.Attachments.get(player, AEAttachmentTypes.HOLDING_CTRL);")
patch("appeng/client/AppEngClient.java",
      "player.setData(AEAttachmentTypes.HOLDING_CTRL, isDown);",
      "nfcompat.attachments.Attachments.set(player, AEAttachmentTypes.HOLDING_CTRL, isDown);")
patch("appeng/core/network/serverbound/UpdateHoldingCtrlPacket.java",
      "player.setData(AEAttachmentTypes.HOLDING_CTRL, keyDown);",
      "nfcompat.attachments.Attachments.set(player, AEAttachmentTypes.HOLDING_CTRL, keyDown);")
patch("appeng/parts/PartPlacement.java",
      "side = player.getData(AEAttachmentTypes.HOLDING_CTRL) ? side.getOpposite() : side;",
      "side = nfcompat.attachments.Attachments.get(player, AEAttachmentTypes.HOLDING_CTRL) ? side.getOpposite() : side;")

# ---------------- 2. Отправка пакетов ----------------
write("nfcompat/network/Payloads.java", '''/*
 * Часть nfcompat. Отправка собственных пакетов игроку.
 *
 * NeoForge патчит `ServerGamePacketListenerImpl`, добавляя `send(CustomPacketPayload)`.
 * На Fabric это `ServerPlayNetworking.send(player, payload)` — тот же эффект через
 * реестр payload'ов.
 */
package nfcompat.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class Payloads {
    private Payloads() {
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }
}
''')

patch("appeng/crafting/execution/CraftingCpuLogic.java",
      "connectedPlayer.connection.send(message);",
      "nfcompat.network.Payloads.send(connectedPlayer, message);")
patch("appeng/menu/AEBaseMenu.java",
      "serverPlayer.connection.send(packet);",
      "nfcompat.network.Payloads.send(serverPlayer, packet);")
patch("appeng/core/network/serverbound/RequestClosestMeteoritePacket.java",
      "player.connection.send(new CompassResponsePacket(pos, result));",
      "nfcompat.network.Payloads.send(player, new CompassResponsePacket(pos, result));")

# ---------------- 3. Индекс слота у голого Slot ----------------
write("nfcompat/menu/SlotIndices.java", '''/*
 * Часть nfcompat. Индекс слота.
 *
 * NeoForge патчит `Slot`, добавляя `getSlotIndex()` — номер слота в контейнере.
 * В ванили он лежит в приватном поле `slot`, поэтому читаем его рефлексией
 * (поле ищется один раз). AE2 зовёт это в горячих путях меню, так что кэш обязателен.
 *
 * TODO: заменить на миксин с `@Shadow`, когда появится миксин-полоса; рефлексия
 * в горячем пути — не то, что хочется в финальной сборке.
 */
package nfcompat.menu;

import java.lang.reflect.Field;

import net.minecraft.world.inventory.Slot;

public final class SlotIndices {
    private static Field slotField;

    private SlotIndices() {
    }

    public static int get(Slot slot) {
        try {
            Field f = slotField;
            if (f == null) {
                f = Slot.class.getDeclaredField("slot");
                f.setAccessible(true);
                slotField = f;
            }
            return f.getInt(slot);
        } catch (ReflectiveOperationException e) {
            // Запасной путь: публичный index (номер слота в меню), чтобы не падать.
            return slot.index;
        }
    }
}
''')

for rel, old, new in [
    ("appeng/client/gui/AEBaseScreen.java",
     "configInv.isItemValid(slot.getSlotIndex(), wrappedStack)",
     "configInv.isItemValid(nfcompat.menu.SlotIndices.get(slot), wrappedStack)"),
    ("appeng/client/gui/AEBaseScreen.java",
     "if (s.getSlotIndex() == j && s.container == this.menu.getPlayerInventory()",
     "if (nfcompat.menu.SlotIndices.get(s) == j && s.container == this.menu.getPlayerInventory()"),
    ("appeng/client/gui/AEBaseScreen.java",
     "if (s.getSlotIndex() == j",
     "if (nfcompat.menu.SlotIndices.get(s) == j"),
    ("appeng/client/gui/implementations/InterfaceScreen.java",
     "menu.openSetAmountMenu(configSlot.getSlotIndex());",
     "menu.openSetAmountMenu(nfcompat.menu.SlotIndices.get(configSlot));"),
    ("appeng/menu/implementations/MolecularAssemblerMenu.java",
     "if (!currentItem.isEmpty() && !isValidItemForSlot(slot.getSlotIndex(), currentItem)) {",
     "if (!currentItem.isEmpty() && !isValidItemForSlot(nfcompat.menu.SlotIndices.get(slot), currentItem)) {"),
    ("appeng/items/misc/WrappedGenericStack.java",
     'LOG.error("Removing a broken wrapped generic stack from player {} slot {}", player, slot.getSlotIndex());',
     'LOG.error("Removing a broken wrapped generic stack from player {} slot {}", player, nfcompat.menu.SlotIndices.get(slot));'),
]:
    patch(rel, old, new)

# ---------------- 4. JEI: NeoForgeTypes ----------------
write("mezz/jei/api/neoforge/NeoForgeTypes.java", '''/*
 * Часть nfcompat. NeoForge-специфичные типы ингредиентов JEI.
 *
 * JEI для NeoForge объявляет здесь `FLUID_STACK` (ингредиент = neoforge FluidStack),
 * и AE2 это использует. В fabric-сборке JEI такого класса нет, поэтому объявляем свой:
 * тип ингредиента — обёртка над нашим шим-`FluidStack`.
 *
 * Плата: JEI не знает этот тип изнутри (у него нет ни рендера, ни тултипа по умолчанию),
 * поэтому жидкости в JEI будут видны только после того, как мы зарегистрируем для него
 * рендерер и хелперы. Это ровно то, чем занят `TransformCategory`.
 */
package mezz.jei.api.neoforge;

import mezz.jei.api.ingredients.IIngredientType;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoForgeTypes {
    public static final IIngredientType<FluidStack> FLUID_STACK = () -> FluidStack.class;

    private NeoForgeTypes() {
    }
}
''')

# ---------------- 5. Мелкие сигнатуры ----------------
patch("appeng/api/stacks/AEFluidKey.java",
      "return stack.getFluid().builtInRegistryHolder().is(tag);",
      "return stack.getFluid().builtInRegistryHolder().is((TagKey<Fluid>) tag);")
patch("appeng/client/integrations/jei/EntropyManipulatorCategory.java",
      "Either.left(ItemModText.FLOWING_FLUID_NAME.text(fluid.getFluidType().getDescription())));",
      "Either.left(ItemModText.FLOWING_FLUID_NAME.text(fluid.getFluidType().getDescription(fluid))));")
