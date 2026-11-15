import io
import os
import re

ROOT = r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge"

def patch(rel, pairs, required=True):
    path = os.path.join(ROOT, rel)
    with io.open(path, encoding="utf-8") as fh:
        text = fh.read()
    orig = text
    for old, new in pairs:
        if old not in text:
            if required:
                print("!! NOT FOUND in %s: %r" % (rel, old[:70]))
            continue
        text = text.replace(old, new)
    if text != orig:
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(text)
        print("patched", rel)

# 1. ChunkEvent: у ChunkAccess нет getLevel()
patch("event/level/ChunkEvent.java", [(
    "super(chunk.getLevel());",
    "super(chunk instanceof net.minecraft.world.level.chunk.LevelChunk levelChunk ? levelChunk.getLevel() : null);"
)])

# 2. LevelEvent.PotentialSpawns: WeightedList.Builder не даёт ни getList, ни remove
patch("event/level/LevelEvent.java", [
    ("private WeightedList.@Nullable Builder<MobSpawnSettings.SpawnerData> list;",
     "private @Nullable List<Weighted<MobSpawnSettings.SpawnerData>> list;"),
    ("""                list = WeightedList.builder();
                list.addAll(view);
                view = list.getList();""",
     """                list = new java.util.ArrayList<>(view);
                view = list;"""),
    ("            list.add(data);",
     "            list.add(data);"),
])

# 3. CreateFluidSourceEvent: canConvertToSource(ServerLevel, BlockPos) — патч NeoForge
patch("event/level/block/CreateFluidSourceEvent.java", [(
    "this.vanillaResult = state.getFluidState().canConvertToSource(level, pos);",
    "// nfcompat: в NeoForge здесь canConvertToSource(ServerLevel, BlockPos) — ванильного аналога нет.\\n"
    "        // Берём ближайшее осмысленное: является ли жидкость источником.\\n"
    "        this.vanillaResult = state.getFluidState().isSource();"
)])

# 4. FinalizeSpawnEvent: Mob#setSpawnCancelled — патч NeoForge, держим свой флаг
patch("event/entity/living/FinalizeSpawnEvent.java", [
    ("this.getEntity().setSpawnCancelled(cancel);", "this.spawnCancelled = cancel;"),
    ("return this.getEntity().isSpawnCancelled();", "return this.spawnCancelled;"),
])

# 5. RegisterKeyMappingsEvent: Identifier#compareNamespaced — патч NeoForge
patch("client/event/RegisterKeyMappingsEvent.java", [(
    "custom.sort((c1, c2) -> c1.id().compareNamespaced(c2.id()));",
    "custom.sort((c1, c2) -> c1.id().toString().compareTo(c2.id().toString()));"
)])

# 6. AddClientReloadListenersEvent: ReloadableResourceManager#getListeners — патч NeoForge
patch("client/event/AddClientReloadListenersEvent.java", [(
    "super(resourceManager.getListeners(), AddClientReloadListenersEvent::lookupName);",
    "super(java.util.List.of(), AddClientReloadListenersEvent::lookupName);"
)])

# 7. ModelEvent.BakingCompleted: standaloneModels() — патч NeoForge
patch("client/event/ModelEvent.java", [(
    """            this.bakingResult = new ModelBakery.BakingResult(
                    bakingResult.missingModels(),
                    Collections.unmodifiableMap(bakingResult.blockStateModels()),
                    Collections.unmodifiableMap(bakingResult.itemStackModels()),
                    Collections.unmodifiableMap(bakingResult.itemProperties()),
                    bakingResult.standaloneModels().unmodifiable());""",
    """            // nfcompat: в NeoForge record расширен полем standaloneModels, в ванили его нет.
            this.bakingResult = bakingResult;"""
)])

# 8. ExtractLevelRenderStateEvent: CustomEnvironmentEffectsRendererManager — только javadoc
patch("client/event/ExtractLevelRenderStateEvent.java", [(
    "import net.neoforged.neoforge.client.CustomEnvironmentEffectsRendererManager;\n", ""
)])

# 9. VanillaContainerWrapper: Container#setItem(int, ItemStack, boolean) и onTransfer — патчи NeoForge
patch("transfer/item/VanillaContainerWrapper.java", [
    ("container.setItem(index, item, true);", "container.setItem(index, item);"),
    ("                container.onTransfer(this.index, inserted, transaction);\n", ""),
    ("                container.onTransfer(this.index, -extracted, transaction);\n", ""),
])

# 10. StackItemAccess: SimpleContainer#setItem с 3 аргументами — патч NeoForge
patch("transfer/access/StackItemAccess.java", [(
    "public void setItem(int slot, ItemStack stack, boolean performSideEffects) {",
    "public void setItem(int slot, ItemStack stack) {"
)])

# 11. PlayerInventoryWrapper: CommonHooks#onPlayerTossEvent / ItemStack#canEquip — патчи NeoForge
patch("transfer/item/PlayerInventoryWrapper.java", [
    ("CommonHooks.onPlayerTossEvent(inventory.player, dropInfo.resource.toStack(dropped), dropInfo.dropAround, dropInfo.prediction);",
     "inventory.player.drop(dropInfo.resource.toStack(dropped), dropInfo.dropAround, dropInfo.prediction);"),
    ("return resource.toStack().canEquip(slot, inventory.player) && super.isValid(resource);",
     "// nfcompat: ItemStack#canEquip(EquipmentSlot, Player) — патч NeoForge. TODO: проверить через Equippable.\n"
     "            return super.isValid(resource);"),
])

# 12. FluidStackTemplate: не хватало импортов DataComponentMap/PatchedDataComponentMap
patch("fluids/FluidStackTemplate.java", [(
    "import net.minecraft.core.component.DataComponentPatch;",
    "import net.minecraft.core.component.DataComponentMap;\n"
    "import net.minecraft.core.component.DataComponentPatch;\n"
    "import net.minecraft.core.component.PatchedDataComponentMap;"
)])

# 13. StacksResourceHandler: импорт удаляемого ResourceHandlerSlot нужен был только для javadoc
patch("transfer/StacksResourceHandler.java", [(
    "import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;\n", ""
)])
