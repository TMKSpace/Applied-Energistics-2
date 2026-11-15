# -*- coding: utf-8 -*-
"""Батч: unregister в шине, ModList scan data, creative tab, RecipeType, Ingredient, ChunkLoadState, тест-плоты."""
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


def patch(rel, old, new, marker=True, count=1):
    path = resolve(rel)
    if not path:
        print("!! нет файла", rel)
        return False
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено:", rel, "::", old[:60])
        return False
    if marker:
        ls = text.rfind("\n", 0, text.index(old)) + 1
        indent = re.match(r'[ \t]*', text[ls:]).group(0)
        block = "%s//#if FABRIC\n%s%s\n%s//#else\n%s%s\n%s//#endif" % ("", indent, new, indent, indent, old, indent)
    else:
        block = new
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, block, count))
    print("правка:", rel)
    return True


# ---------- 1. ForcedChunkManager (нужен только для javadoc-ссылки) ----------
wh = os.path.join(CJ, "net", "neoforged", "neoforge", "common", "world", "chunk")
os.makedirs(wh, exist_ok=True)
with io.open(os.path.join(wh, "ForcedChunkManager.java"), "w", encoding="utf-8", newline="\n") as fh:
    fh.write('''/*
 * Часть nfcompat. Менеджер принудительно загруженных чанков.
 *
 * В NeoForge он ведёт учёт форс-лоадов по владельцу (мод/блок-сущность) и умеет
 * восстанавливать их после перезагрузки. AE2 ссылается на него только в javadoc
 * (`SpatialAnchorBlockEntity`), а фактическую работу делает через наш `TicketController`,
 * поэтому здесь — только объявление, чтобы javadoc-ссылка разрешалась.
 */
package net.neoforged.neoforge.common.world.chunk;

public final class ForcedChunkManager {
    private ForcedChunkManager() {
    }
}
''')
print("написан ForcedChunkManager")

# ---------- 2. ModList: scan data ----------
MODLIST = os.path.join(CJ, "net", "neoforged", "fml", "ModList.java")
with io.open(MODLIST, encoding="utf-8") as fh:
    ml = fh.read()
if "getAllScanData" not in ml:
    ml = ml.replace("""    public List<ModContainer> getMods() {""",
"""    /**
     * NeoForge собирает при сканировании модов их scan data (аннотации, цели и т.п.),
     * и AE2 этим ищет классы, помеченные её аннотациями (`@TestPlotClass`).
     * На Fabric такого сканирования нет, поэтому список пуст: тест-плоты просто
     * не будут найдены автоматически. Появится, если понадобится — через
     * `ClassGraph`/индекс Fabric, либо явным списком.
     */
    public List<Object> getAllScanData() {
        return List.of();
    }

    public List<ModContainer> getMods() {""", 1)
    with io.open(MODLIST, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(ml)
    print("ModList: getAllScanData добавлен")

# ---------- 3. Шина: unregister ----------
BUS = os.path.join(CJ, "net", "neoforged", "bus", "api", "SimpleEventBus.java")
with io.open(BUS, encoding="utf-8") as fh:
    bus = fh.read()
if "void unregister(" not in bus:
    # запоминаем, какие слушатели добавлены через register(target)
    bus = bus.replace("""    private final Map<Class<?>, List<Entry<?>>> listeners = new ConcurrentHashMap<>();""",
"""    private final Map<Class<?>, List<Entry<?>>> listeners = new ConcurrentHashMap<>();
    /** Слушатели, добавленные через {@link #register(Object)}: нужно для unregister. */
    private final Map<Object, List<Entry<?>>> registered = new ConcurrentHashMap<>();""", 1)
    bus = bus.replace("""    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {""",
"""    /**
     * Отписывает всё, что было подписано через {@link #register(Object)} для этого объекта.
     * В NeoForge `unregister` снимает слушателей, привязанных к экземпляру; нашей
     * регистрации по методу этого достаточно.
     */
    @Override
    public void unregister(Object target) {
        var entries = registered.remove(target);
        if (entries == null) {
            return;
        }
        for (var list : listeners.values()) {
            list.removeAll(entries);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {""", 1)
    with io.open(BUS, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(bus)
    print("SimpleEventBus: unregister добавлен")

IBUS = os.path.join(CJ, "net", "neoforged", "bus", "api", "IEventBus.java")
with io.open(IBUS, encoding="utf-8") as fh:
    ib = fh.read()
if "unregister" not in ib:
    ib = ib.replace("""    <T extends Event> T post(T event);""",
"""    <T extends Event> T post(T event);

    /** Снимает слушателей, подписанных через {@link #register(Object)}. */
    void unregister(Object target);""", 1)
    with io.open(IBUS, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(ib)
    print("IEventBus: unregister добавлен")

# ---------- 4. Маркеры ----------
# CreativeModeTab.builder() без аргументов -> у ванили нужен (Row, int)
patch("appeng/core/FacadeCreativeTab.java",
      "group = CreativeModeTab.builder()",
      "group = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)")
patch("appeng/core/MainCreativeTab.java",
      "var tab = CreativeModeTab.builder()",
      "var tab = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)")

# RecipeType.simple -> register(String)
patch("appeng/recipes/AERecipeTypes.java",
      "RecipeType<T> type = RecipeType.simple(AppEng.makeId(id));",
      "RecipeType<T> type = RecipeType.register(AppEng.makeId(id).toString());")

# Ingredient: isCustom/getValues -> ванильные items()
patch("appeng/recipes/transform/TransformLogic.java",
      "if (!ingredient.isCustom()) {",
      "if (true) { // ванильный Ingredient не бывает \"кастомным\"")
patch("appeng/recipes/transform/TransformLogic.java",
      "holderSets.add(ingredient.getValues());",
      "holderSets.add(net.minecraft.core.HolderSet.direct(ingredient.items().toList()));")
patch("appeng/core/network/serverbound/FillCraftingGridFromRecipePacket.java",
      "if (!ingredient.isCustom()) {",
      "if (true) { // ванильный Ingredient не бывает \"кастомным\"")
patch("appeng/core/network/serverbound/FillCraftingGridFromRecipePacket.java",
      "return ingredient.getValues().stream()",
      "return ingredient.items()")

# ChunkLoadState: Supplier вместо ссылки на конструктор с уровнем
patch("appeng/server/services/ChunkLoadState.java",
      "            ChunkLoadState::new,",
      "            () -> new ChunkLoadState(ServerLifecycleHooks.getCurrentServer().overworld()),")

# Тест-плоты: ItemStackTemplate(Holder,int,Patch) и сравнение с шаблоном
patch("appeng/server/testplots/InscriberTestPlots.java",
      "var namedIngots = new ItemStackTemplate(Items.IRON_INGOT, 2, DataComponentPatch.builder()",
      "var namedIngots = new ItemStackTemplate(Items.IRON_INGOT.builtInRegistryHolder(), 2, DataComponentPatch.builder()")
patch("appeng/server/testplots/InscriberTestPlots.java",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(0), expectedTopSlot),",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(0), expectedTopSlot.create()),")
patch("appeng/server/testplots/InscriberTestPlots.java",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(1), expectedBottomSlot),",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(1), expectedBottomSlot.create()),")
patch("appeng/server/testplots/InscriberTestPlots.java",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(2), expectedMiddleSlot),",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(2), expectedMiddleSlot.create()),")
patch("appeng/server/testplots/InscriberTestPlots.java",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(3), expectedResult),",
      "ItemStack.isSameItemSameComponents(inv.getStackInSlot(3), expectedResult.create()),")

# RecipeOutput.accept с условием
patch("appeng/recipes/mattercannon/MatterCannonAmmo.java",
      "consumer.accept(ResourceKey.create(Registries.RECIPE, id), recipe, null, condition);",
      "consumer.accept(ResourceKey.create(Registries.RECIPE, id), recipe, null);")

# Fluid.getFluidType().getSound(...) — в шиме звуков у жидкости нет
patch("appeng/client/AEClientboundPacketHandler.java",
      "soundEvent = fluid.getFluidType().getSound(fluid, SoundActions.BUCKET_FILL);",
      "soundEvent = null; // nfcompat: звуков жидкостей в шиме нет")
patch("appeng/util/fluid/FluidSoundHelper.java",
      "SoundEvent fillSound = fluid.getFluid().getFluidType().getSound(player, SoundActions.BUCKET_FILL);",
      "SoundEvent fillSound = null; // nfcompat: звуков жидкостей в шиме нет")
patch("appeng/util/fluid/FluidSoundHelper.java",
      "SoundEvent fillSound = fluid.getFluid().getFluidType().getSound(player, SoundActions.BUCKET_EMPTY);",
      "SoundEvent fillSound = null; // nfcompat: звуков жидкостей в шиме нет")
