# -*- coding: utf-8 -*-
"""Продолжение: scan data, P2P-капабилити, рендер-маркеры, мелочь."""
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
        return
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено:", rel, "::", old.strip()[:60])
        return
    if marker:
        indent = re.match(r'[ \t]*', old).group(0)
        block = "//#if FABRIC\n%s\n%s//#else\n%s\n%s//#endif" % (new, indent, old, indent)
    else:
        block = new
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, block, 1))
    print("правка:", rel)


# ---------- 1. Scan data (ModList.getAllScanData) ----------
write("net/neoforged/neoforgespi/language/ModFileScanData.java", '''/*
 * Часть nfcompat. Результат сканирования мода.
 *
 * NeoForge при загрузке сканирует байткод модов и собирает найденные аннотации;
 * AE2 этим ищет классы, помеченные её аннотациями (тест-плоты). Сканирования у нас нет,
 * поэтому список аннотаций пуст — типы нужны, чтобы код AE2 компилировался и корректно
 * обрабатывал пустой результат.
 */
package net.neoforged.neoforgespi.language;

import java.util.Collection;
import java.util.List;

public final class ModFileScanData {
    private final List<AnnotationData> annotations = List.of();

    public Collection<AnnotationData> getAnnotations() {
        return annotations;
    }

    /** Одна найденная аннотация. */
    public static final class AnnotationData {
        private final String memberName;
        private final java.lang.annotation.ElementType targetType;
        private final Type annotationType;

        public AnnotationData(String memberName, java.lang.annotation.ElementType targetType, Type annotationType) {
            this.memberName = memberName;
            this.targetType = targetType;
            this.annotationType = annotationType;
        }

        public String memberName() {
            return memberName;
        }

        public java.lang.annotation.ElementType targetType() {
            return targetType;
        }

        public Type annotationType() {
            return annotationType;
        }
    }

    /** Тип аннотации в том виде, как его видит сканер. */
    public record Type(String getClassName) {
    }
}
''')

MODLIST = os.path.join(CJ, "net", "neoforged", "fml", "ModList.java")
with io.open(MODLIST, encoding="utf-8") as fh:
    ml = fh.read()
ml = ml.replace("    public List<Object> getAllScanData() {",
                "    public List<net.neoforged.neoforgespi.language.ModFileScanData> getAllScanData() {", 1)
with io.open(MODLIST, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(ml)
print("ModList: тип scan data сужен")

# ---------- 2. Хелперы шима ----------
write("nfcompat/util/Lists.java", '''/*
 * Часть nfcompat. Утилиты списков.
 *
 * `NonNullList.copyOf` есть в NeoForge, но не в ванили — а AE2 им пользуется,
 * чтобы получить список без null-элементов.
 */
package nfcompat.util;

import java.util.Collection;

import net.minecraft.core.NonNullList;

public final class Lists {
    private Lists() {
    }

    public static <T> NonNullList<T> nonNullCopy(Collection<T> source) {
        NonNullList<T> list = NonNullList.create();
        list.addAll(source);
        return list;
    }
}
''')

# ---------- 3. Маркеры ----------
patch("appeng/api/features/P2PTunnelAttunement.java",
      "Predicate<ItemStack> test = stack -> stack.getCapability(cap) != null;",
      "Predicate<ItemStack> test = stack -> cap.getCapability(stack, null) != null;")
patch("appeng/api/features/P2PTunnelAttunement.java",
      "Predicate<ItemStack> test = stack -> stack.getCapability(cap, ItemAccess.forStack(stack)) != null;",
      "Predicate<ItemStack> test = stack -> cap.getCapability(stack, ItemAccess.forStack(stack)) != null;")

# TransformLogic: второй экземпляр того же кода
patch("appeng/recipes/transform/TransformLogic.java",
      "if (!ingredient.isCustom()) {",
      "if (true) { // ванильный Ingredient не бывает \"кастомным\"")
patch("appeng/recipes/transform/TransformLogic.java",
      "holderSets.add(ingredient.getValues());",
      "holderSets.add(net.minecraft.core.HolderSet.direct(ingredient.items().toList()));")

# recipeMap в CraftingUnitTransformRecipe (пропущен регуляркой)
patch("appeng/recipes/game/CraftingUnitTransformRecipe.java",
      "for (var holder : level.recipeAccess().recipeMap().byType(AERecipeTypes.CRAFTING_UNIT_TRANSFORM)) {",
      "for (var holder : nfcompat.recipes.RecipeMaps.of(level.recipeAccess()).byType(AERecipeTypes.CRAFTING_UNIT_TRANSFORM)) {")

# Ingredient::isSimple
patch("appeng/recipes/quartzcutting/QuartzCuttingRecipe.java",
      "this.isSimple = ingredients.stream().allMatch(Ingredient::isSimple);",
      "this.isSimple = true; // nfcompat: ванильный Ingredient не различает simple/custom")

# NonNullList.copyOf
patch("appeng/core/network/serverbound/FillCraftingGridFromRecipePacket.java",
      "this.ingredientTemplates = NonNullList.copyOf(ingredientTemplates.stream().map(ItemStack::copy).toList());",
      "this.ingredientTemplates = nfcompat.util.Lists.nonNullCopy(ingredientTemplates.stream().map(ItemStack::copy).toList());")

# TooltipFlag.hasShiftDown -> ванильный флаг advanced
patch("appeng/items/storage/CreativeCellItem.java",
      "if (tooltipFlags.hasShiftDown()) {",
      "if (tooltipFlags.isAdvanced()) {")

# Item.onItemUseFirst — метод NeoForge
patch("appeng/items/materials/UpgradeCardItem.java",
      "return super.onItemUseFirst(stack, context);",
      "return net.minecraft.world.InteractionResult.PASS; // nfcompat: onItemUseFirst — метод NeoForge")

# Block.onCaughtFire — метод NeoForge
patch("appeng/items/tools/powered/EntropyManipulatorItem.java",
      "block.onCaughtFire(level.getBlockState(pos), level, pos, side, p, item);",
      "// nfcompat: Block#onCaughtFire — патч NeoForge, ванильного хука нет")

# CreativeModeTab.Builder.withTabsBefore — метод NeoForge
patch("appeng/core/FacadeCreativeTab.java",
      ".withTabsBefore(AECreativeTabIds.MAIN)",
      "// nfcompat: withTabsBefore — метод NeoForge")

# openMenu с доп. данными
patch("appeng/menu/implementations/MenuTypeBuilder.java",
      "player.openMenu(new AppEngMenuProvider(), buffer -> {",
      "player.openMenu(new AppEngMenuProvider()); if (false) { java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> unused = buffer -> {")

# ambientOcclusion — патч BakedQuad.MaterialInfo
patch("appeng/client/render/cablebus/FacadeBuilder.java",
      "quad.setAmbientOcclusion(originalQuad.materialInfo().ambientOcclusion());",
      "quad.setAmbientOcclusion(false); // nfcompat: AO-флага в ванильном MaterialInfo нет")
patch("appeng/client/render/cablebus/FacadeBuilder.java",
      "return originalPart.ambientOcclusion();",
      "return false; // nfcompat: AO-флага в ванильном BlockStateModelPart нет")

# collectParts с контекстом уровня
patch("appeng/client/render/cablebus/FacadeBuilder.java",
      "model.collectParts(level, pos, blockState, random, parts);",
      "if (model instanceof net.neoforged.neoforge.client.model.DynamicBlockStateModel dynamic) {\n            dynamic.collectParts(level, pos, blockState, random, parts);\n        } else {\n            model.collectParts(random, parts);\n        }")

# registerCapabilityListener
patch("appeng/parts/storagebus/StorageBusPart.java",
      "serverLevel.registerCapabilityListener(targetPos, this.capabilityListener);",
      "nfcompat.capabilities.CapabilityInvalidation.registerListener(serverLevel, targetPos, this.capabilityListener);")

# FluidType у Fluid (NF патч): у нас — статический of(...)
patch("appeng/client/integrations/jei/EntropyManipulatorCategory.java",
      "Either.left(ItemModText.FLOWING_FLUID_NAME.text(fluid.getFluidType().getDescription(fluid))));",
      "Either.left(ItemModText.FLOWING_FLUID_NAME.text(\n                net.neoforged.neoforge.fluids.FluidType.of(fluid).getDescription()));")
