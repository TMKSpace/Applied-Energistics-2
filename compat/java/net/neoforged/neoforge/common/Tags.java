/*
 * Часть nfcompat. Convention-теги NeoForge (namespace "c").
 *
 * В NeoForge Tags.java — 154 КБ констант на все случаи жизни. Здесь только те,
 * что реально использует AE2 (33 штуки). Каждая — тот же TagKey с тем же путём,
 * что и в NeoForge, поэтому данные тегов совпадают.
 */
package net.neoforged.neoforge.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;

public final class Tags {
    private static final String NAMESPACE = "c";

    private Tags() {
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    private static TagKey<Biome> biome(String path) {
        return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    public static final class Items {
        private Items() {
        }

        public static final TagKey<Item> BUDDING_BLOCKS = item("budding_blocks");
        public static final TagKey<Item> BUDS = item("buds");
        public static final TagKey<Item> CHESTS = item("chests");
        public static final TagKey<Item> CHESTS_WOODEN = item("chests/wooden");
        public static final TagKey<Item> CLUSTERS = item("clusters");
        public static final TagKey<Item> DUSTS = item("dusts");
        public static final TagKey<Item> DUSTS_GLOWSTONE = item("dusts/glowstone");
        public static final TagKey<Item> DUSTS_REDSTONE = item("dusts/redstone");
        public static final TagKey<Item> ENDER_PEARLS = item("ender_pearls");
        public static final TagKey<Item> GEMS = item("gems");
        public static final TagKey<Item> GEMS_DIAMOND = item("gems/diamond");
        public static final TagKey<Item> GEMS_QUARTZ = item("gems/quartz");
        public static final TagKey<Item> GLASS_BLOCKS = item("glass_blocks");
        public static final TagKey<Item> GLASS_BLOCKS_CHEAP = item("glass_blocks/cheap");
        public static final TagKey<Item> INGOTS_COPPER = item("ingots/copper");
        public static final TagKey<Item> INGOTS_GOLD = item("ingots/gold");
        public static final TagKey<Item> INGOTS_IRON = item("ingots/iron");
        public static final TagKey<Item> NUGGETS_GOLD = item("nuggets/gold");
        public static final TagKey<Item> NUGGETS_IRON = item("nuggets/iron");
        public static final TagKey<Item> RODS_WOODEN = item("rods/wooden");
        public static final TagKey<Item> STONES = item("stones");
        public static final TagKey<Item> STORAGE_BLOCKS = item("storage_blocks");
    }

    public static final class Blocks {
        private Blocks() {
        }

        public static final TagKey<Block> BUDDING_BLOCKS = block("budding_blocks");
        public static final TagKey<Block> BUDS = block("buds");
        public static final TagKey<Block> CHESTS = block("chests");
        public static final TagKey<Block> CHESTS_WOODEN = block("chests/wooden");
        public static final TagKey<Block> CLUSTERS = block("clusters");
        public static final TagKey<Block> GLASS_BLOCKS = block("glass_blocks");
        public static final TagKey<Block> GLASS_BLOCKS_CHEAP = block("glass_blocks/cheap");
        public static final TagKey<Block> RELOCATION_NOT_SUPPORTED = block("relocation_not_supported");
        public static final TagKey<Block> STONES = block("stones");
        public static final TagKey<Block> STORAGE_BLOCKS = block("storage_blocks");
    }

    public static final class Biomes {
        private Biomes() {
        }

        public static final TagKey<Biome> IS_COLD = biome("is_cold");
        public static final TagKey<Biome> IS_OCEAN = biome("is_ocean");
        public static final TagKey<Biome> IS_PLAINS = biome("is_plains");
        public static final TagKey<Biome> IS_SANDY = biome("is_sandy");
        public static final TagKey<Biome> IS_SNOWY = biome("is_snowy");
    }
}
