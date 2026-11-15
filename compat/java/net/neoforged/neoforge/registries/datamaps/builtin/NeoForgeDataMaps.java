/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/registries/datamaps/builtin/NeoForgeDataMaps.java
 *
 * SHIM: only the data map types that AE2's datagen references are vendored (raid hero gifts).
 * They are inert descriptors: there is no data map loader on Fabric, so vanilla's built-in
 * `GiveGiftToHero.GIFTS` map is not replaced by the generated JSON either. The constants exist so
 * that `DataMapProvider` subclasses can name the map they feed.
 */

package net.neoforged.neoforge.registries.datamaps.builtin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * Holds the {@link DataMapType data maps} provided by NeoForge.
 */
public final class NeoForgeDataMaps {
    /**
     * The {@linkplain VillagerProfession} data map that replaces the villager-&gt;hero gift table map.
     * <p>
     * The values are objects with 1 field:
     * <ul>
     * <li>{@code loot_table}, the loot table that the villager gives to the player after a raid finishes</li>
     * </ul>
     */
    public static final DataMapType<VillagerProfession, RaidHeroGift> RAID_HERO_GIFTS = DataMapType.builder(
            id("raid_hero_gifts"), Registries.VILLAGER_PROFESSION, RaidHeroGift.CODEC)
            .synced(RaidHeroGift.LOOT_TABLE_CODEC, false).build();

    private NeoForgeDataMaps() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NeoForgeMod.MOD_ID, path);
    }
}
