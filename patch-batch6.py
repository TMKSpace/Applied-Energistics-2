# -*- coding: utf-8 -*-
"""Батч 6: TriState, тик-флаг, KeyMapping, RegisterEvent.register, standalone-модели."""
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


def patch(rel, old, new):
    path = resolve(rel)
    if not path:
        print("!! нет файла", rel)
        return
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    if old not in text:
        print("!! не найдено:", rel, "::", old.strip()[:60])
        return
    indent = re.match(r'[ \t]*', old).group(0)
    block = "//#if FABRIC\n%s\n%s//#else\n%s\n%s//#endif" % (new, indent, old, indent)
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text.replace(old, block, 1))
    print("правка:", rel)


# 1. registerListener в CapabilityInvalidation
CI = os.path.join(CJ, "nfcompat", "capabilities", "CapabilityInvalidation.java")
with io.open(CI, encoding="utf-8") as fh:
    ci = fh.read()
if "registerListener" not in ci:
    ci = ci.replace("""    public static void invalidate(@Nullable Object holder) {
        // no-op: кэша нет
    }""",
"""    public static void invalidate(@Nullable Object holder) {
        // no-op: кэша нет
    }

    /**
     * NeoForge позволяет подписаться на изменение capability у позиции
     * (`ServerLevel#registerCapabilityListener`). Кэша нет — слушать нечего,
     * поэтому вызов принимается и игнорируется.
     */
    public static void registerListener(Object level, Object pos, @Nullable Object listener) {
        // no-op
    }""", 1)
    with io.open(CI, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(ci)
    print("CapabilityInvalidation: registerListener добавлен")

# 2. DynamicBlockStateModel: materialFlags
DBM = os.path.join(CJ, "net", "neoforged", "neoforge", "client", "model", "DynamicBlockStateModel.java")
with io.open(DBM, encoding="utf-8") as fh:
    dbm = fh.read()
if "materialFlags" not in dbm:
    dbm = dbm.replace("""    @Override
    default void collectParts(RandomSource random, List<BlockStateModelPart> parts) {""",
"""    /**
     * NeoForge добавляет контекстный вариант `materialFlags`: модель может ответить,
     * какие материалы ей нужны в этой позиции. Ванильный `BlockStateModel` такого не имеет,
     * поэтому по умолчанию — 0 (без флагов).
     */
    default int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return 0;
    }

    @Override
    default void collectParts(RandomSource random, List<BlockStateModelPart> parts) {""", 1)
    with io.open(DBM, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(dbm)
    print("DynamicBlockStateModel: materialFlags добавлен")

# 3. RegisterEvent.register для датапак-реестров
RE = os.path.join(CJ, "net", "neoforged", "neoforge", "registries", "RegisterEvent.java")
with io.open(RE, encoding="utf-8") as fh:
    re_txt = fh.read()
if "public void register(" not in re_txt:
    re_txt = re_txt.replace("""    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {""",
"""    /**
     * NeoForge'вская форма регистрации элемента датапак-реестра вместе с его кодеком.
     * У нас датапак-реестры не наполняются этим путём, поэтому вызов принимается
     * и ничего не делает — соответствующие записи должны приходить из данных.
     */
    public void register(ResourceKey<? extends Registry<?>> registryKey, net.minecraft.resources.Identifier id,
            java.util.function.Supplier<?> codec) {
        // no-op
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {""", 1)
    with io.open(RE, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(re_txt)
    print("RegisterEvent: register(…, Supplier) добавлен")

# 4. Standalone-модели: ModelManager#getStandaloneModel — патч NeoForge
write("nfcompat/client/StandaloneModels.java", '''/*
 * Часть nfcompat. Доступ к standalone-моделям.
 *
 * NeoForge патчит `ModelManager`, добавляя `getStandaloneModel(StandaloneModelKey)`:
 * моды регистрируют такие модели в `ModelEvent.RegisterStandalone`, и они пекутся
 * вместе с остальными. На Fabric этого механизма нет — возвращаем null, и вызывающие
 * обязаны это переживать (`Objects.requireNonNull` в AE2 упадёт).
 *
 * TODO: реализовать реестр standalone-моделей и запекание в нашем клиентском слое.
 */
package nfcompat.client;

import net.minecraft.client.resources.model.ModelManager;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jetbrains.annotations.Nullable;

public final class StandaloneModels {
    private StandaloneModels() {
    }

    @Nullable
    public static <T> T get(ModelManager manager, StandaloneModelKey<T> key) {
        return null;
    }
}
''')

# 5. Маркеры
patch("appeng/client/render/cablebus/FacadeBuilder.java",
      "return false; // nfcompat: AO-флага в ванильном BlockStateModelPart нет",
      "return net.minecraft.util.TriState.FALSE; // nfcompat: AO-флага в ванильном BlockStateModelPart нет")

patch("appeng/recipes/transform/TransformLogic.java",
      "if (!ingredient.isCustom()) {",
      "if (true) { // ванильный Ingredient не бывает \"кастомным\"")
patch("appeng/recipes/transform/TransformLogic.java",
      "holderSets.add(ingredient.getValues());",
      "holderSets.add(net.minecraft.core.HolderSet.direct(ingredient.items().toList()));")

patch("appeng/client/renderer/entity/TinyTNTPrimedRenderer.java",
      "if (renderState.fuseRemainingInTicks - renderState.partialTick + 1.0F < 10.0F) {",
      "if (renderState.fuseRemainingInTicks + 1.0F < 10.0F) { // nfcompat: partialTick в render state — патч NeoForge")
patch("appeng/client/renderer/entity/TinyTNTPrimedRenderer.java",
      "f2 = 1.0F - (renderState.fuseRemainingInTicks - renderState.partialTick + 1.0F) / 10.0F;",
      "f2 = 1.0F - (renderState.fuseRemainingInTicks + 1.0F) / 10.0F; // nfcompat: partialTick — патч NeoForge")

patch("appeng/client/AppEngClient.java",
'''            "key.ae2.mouse_wheel_item_modifier", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LSHIFT, Hotkeys.CATEGORY);''',
'''            "key.ae2.mouse_wheel_item_modifier", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LSHIFT, Hotkeys.CATEGORY);''')

patch("appeng/client/AppEngClient.java",
'''            "key.ae2.part_placement_opposite", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LCONTROL, Hotkeys.CATEGORY);''',
'''            "key.ae2.part_placement_opposite", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_LCONTROL, Hotkeys.CATEGORY);''')

patch("appeng/client/AppEngClient.java",
      "if (event.getKey() == PART_PLACEMENT_OPPOSITE.getKey().getValue()) {",
      "if (event.getKey() == PART_PLACEMENT_OPPOSITE.getDefaultKey().getValue()) {")

patch("appeng/client/renderer/blockentity/MEChestRenderer.java",
      "var model = modelManager.getStandaloneModel(cellModelKey);",
      "var model = nfcompat.client.StandaloneModels.get(modelManager, cellModelKey);")

patch("appeng/client/renderer/blockentity/CrankRenderer.java",
      "handleModel = new SingleVariant(Objects.requireNonNull(modelManager.getStandaloneModel(HANDLE_MODEL)));",
      "handleModel = new SingleVariant(nfcompat.client.StandaloneModels.get(modelManager, HANDLE_MODEL));")
