# -*- coding: utf-8 -*-
"""Батч 7: BasicUnbakedModel/Resolver, isHovering, KeyMapping.matches, getCloneItemStack, мелочь."""
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


# 1. Хелпер данных render-state
write("nfcompat/client/RenderStateData.java", '''/*
 * Часть nfcompat. Данные, привязанные к render state.
 *
 * NeoForge патчит `LevelRenderState`, добавляя `setRenderData(ContextKey, value)` /
 * `getRenderDataOrDefault(...)`: так моды передают свои данные из фазы извлечения
 * состояния в фазу отрисовки. На Fabric этого нет, поэтому храним значения здесь
 * по слабой ссылке на сам state.
 *
 * Ограничения: данные живут, пока жив объект state; при пересоздании state их надо
 * положить заново (что AE2 и делает — пишет их каждый кадр).
 */
package nfcompat.client;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.util.context.ContextKey;

public final class RenderStateData {
    private static final Map<Object, Map<ContextKey<?>, Object>> VALUES = Collections
            .synchronizedMap(new WeakHashMap<>());

    private RenderStateData() {
    }

    public static <T> void set(Object state, ContextKey<T> key, T value) {
        synchronized (VALUES) {
            VALUES.computeIfAbsent(state, k -> new java.util.HashMap<>()).put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getOrDefault(Object state, ContextKey<T> key, T fallback) {
        synchronized (VALUES) {
            var map = VALUES.get(state);
            if (map != null) {
                var value = map.get(key);
                if (value != null) {
                    return (T) value;
                }
            }
        }
        return fallback;
    }
}
''')

# 2. BasicUnbakedModel: UnbakedModel в ванили не наследует ResolvableModel
patch("appeng/client/render/BasicUnbakedModel.java",
      "public interface BasicUnbakedModel extends UnbakedModel {",
      "public interface BasicUnbakedModel extends UnbakedModel,\n        net.minecraft.client.resources.model.ResolvableModel {")

# 3. KeyMapping.isActiveAndMatches -> matches
patch("appeng/client/gui/AEBaseScreen.java",
      "if (getMinecraft().options.keyPickItem.isActiveAndMatches(pickBlockButton)",
      "if (getMinecraft().options.keyPickItem.matches(pickBlockButton)")

# 4. isHovering: в ванили только 6-аргументная версия
path = resolve("appeng/client/gui/AEBaseScreen.java")
with io.open(path, encoding="utf-8") as fh:
    txt = fh.read()
old = """    @Override
    protected boolean isHovering(Slot slot, double x, double y) {
        if (slot instanceof ResizableSlot resizableSlot) {
            var width = resizableSlot.getWidth();
            var height = resizableSlot.getHeight();
            return this.isHovering(slot.x, slot.y, width, height, x, y);
        }
        return super.isHovering(slot, x, y);
    }"""
new = """//#if FABRIC
    // в ванили `isHovering` есть только в 6-аргументной форме, поэтому это обычный
    // вспомогательный метод, а не переопределение
    private boolean ae2IsHovering(Slot slot, double x, double y) {
        if (slot instanceof ResizableSlot resizableSlot) {
            var width = resizableSlot.getWidth();
            var height = resizableSlot.getHeight();
            return this.isHovering(slot.x, slot.y, width, height, x, y);
        }
        return super.isHovering(slot.x, slot.y, 16, 16, x, y);
    }
//#else
    @Override
    protected boolean isHovering(Slot slot, double x, double y) {
        if (slot instanceof ResizableSlot resizableSlot) {
            var width = resizableSlot.getWidth();
            var height = resizableSlot.getHeight();
            return this.isHovering(slot.x, slot.y, width, height, x, y);
        }
        return super.isHovering(slot, x, y);
    }
//#endif"""
if old in txt:
    with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(txt.replace(old, new, 1))
    print("правка: isHovering")
else:
    print("!! isHovering не найдено")

# 5. getCloneItemStack: ванильная версия без игрока
patch("appeng/api/implementations/blockentities/PatternContainerGroup.java",
"""            var targetItem = level instanceof ServerLevel serverLevel ? targetBlock.getCloneItemStack(level, pos,
                    target.getBlockState(), true, Platform.getFakePlayer(serverLevel, null))
                    : new ItemStack(targetBlock);""",
"""            var targetItem = level instanceof ServerLevel ? targetBlock.getCloneItemStack(level, pos,
                    target.getBlockState(), true)
                    : new ItemStack(targetBlock);""")

# 6. MissingContentItem: у компонента есть copyTag(), но нет contains(String)
patch("appeng/items/misc/MissingContentItem.java",
      'if (itemStackData != null && itemStackData.contains("id")) {',
      'if (itemStackData != null && itemStackData.copyTag().contains("id")) {')
patch("appeng/items/misc/MissingContentItem.java",
      '} else if (genericStackData != null && genericStackData.contains("id")) {',
      '} else if (genericStackData != null && genericStackData.copyTag().contains("id")) {')

# 7. SkyStoneTank: putChild -> child + serialize
patch("appeng/blockentity/storage/SkyStoneTankBlockEntity.java",
      'data.putChild("tank", tank);',
      'tank.serialize(data.child("tank"));')

# 8. AreaOverlay: render data
patch("appeng/client/areaoverlay/AreaOverlayRenderer.java",
      "event.getRenderState().setRenderData(OVERLAY_AREAS, areasInThisLevel);",
      "nfcompat.client.RenderStateData.set(event.getRenderState(), OVERLAY_AREAS, areasInThisLevel);")
patch("appeng/client/areaoverlay/AreaOverlayRenderer.java",
      "var visibleAreas = levelRenderState.getRenderDataOrDefault(OVERLAY_AREAS, List.of());",
      "var visibleAreas = nfcompat.client.RenderStateData.getOrDefault(levelRenderState, OVERLAY_AREAS, List.of());")

# 9. FacadeItemModel: BlockState#isEmpty — патч NeoForge
patch("appeng/client/render/FacadeItemModel.java",
      "if (facadeBlockState.isEmpty()) {",
      "if (facadeBlockState.is(net.minecraft.world.level.block.Blocks.AIR)) {")

# 10. TransformLogic: последняя пара isCustom/getValues (вне маркеров)
TL = resolve("appeng/recipes/transform/TransformLogic.java")
with io.open(TL, encoding="utf-8") as fh:
    lines = fh.read().split("\n")
out, depth, changed = [], 0, 0
for line in lines:
    s = line.strip()
    if s.startswith("//#if"):
        depth += 1
    elif s.startswith("//#endif"):
        depth -= 1
    if depth == 0 and s == "if (!ingredient.isCustom()) {":
        ind = line[:len(line) - len(line.lstrip())]
        out += [ind + "//#if FABRIC", ind + "if (true) { // ванильный Ingredient не \"кастомный\"",
                ind + "//#else", line, ind + "//#endif"]
        changed += 1
        continue
    if depth == 0 and s == "holderSets.add(ingredient.getValues());":
        ind = line[:len(line) - len(line.lstrip())]
        out += [ind + "//#if FABRIC",
                ind + "holderSets.add(net.minecraft.core.HolderSet.direct(ingredient.items().toList()));",
                ind + "//#else", line, ind + "//#endif"]
        changed += 1
        continue
    out.append(line)
if changed:
    with io.open(TL, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(out))
print("TransformLogic: доправлено строк", changed)
