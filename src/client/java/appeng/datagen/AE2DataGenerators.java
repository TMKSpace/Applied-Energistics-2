/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.datagen;

//#if FABRIC
import java.util.List;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.data.DataProvider;

import appeng.core.AppEng;
import appeng.core.definitions.AEDamageTypes;
import appeng.datagen.providers.AE2ParticleDescriptionProvider;
import appeng.datagen.providers.advancements.AdvancementGenerator;
import appeng.datagen.providers.localization.LocalizationProvider;
import appeng.datagen.providers.loot.AE2LootTableProvider;
import appeng.datagen.providers.models.AE2ModelProvider;
import appeng.datagen.providers.models.BlockModelProvider;
import appeng.datagen.providers.models.DecorationModelProvider;
import appeng.datagen.providers.models.ItemModelProvider;
import appeng.datagen.providers.models.PartModelProvider;
import appeng.datagen.providers.recipes.AE2RecipeProvider;
import appeng.datagen.providers.tags.BiomeTagsProvider;
import appeng.datagen.providers.tags.BlockTagsProvider;
import appeng.datagen.providers.tags.DataComponentTypeTagProvider;
import appeng.datagen.providers.tags.FluidTagsProvider;
import appeng.datagen.providers.tags.ItemTagsProvider;
import appeng.datagen.providers.tags.PoiTypeTagsProvider;
import appeng.datagen.providers.tags.VillagerTradeTagsProvider;
import appeng.init.InitVillager;
import appeng.init.worldgen.InitBiomes;
import appeng.init.worldgen.InitDimensionTypes;
import appeng.init.worldgen.InitStructures;

/**
 * Fabric datagen entry point, registered through {@code fabric-data-generation-api-v1}.
 * <p>
 * The NeoForge build drives datagen through {@code GatherDataEvent}/{@code GatherDataRegistryEntriesEvent}. Fabric has
 * its own bootstrap instead: datapack registries are filled via {@link RegistrySetBuilder} and the file providers are
 * added to a pack. Neither the events nor NeoForge's mod-scoped pack generators exist here.
 * <p>
 * The registry bootstraps are the same ones the NeoForge build registers - the only difference is that the Fabric API
 * dispatches them into the reloadable and world registry builders itself.
 */
public final class AE2DataGenerators implements DataGeneratorEntrypoint {

    // Registry entries are gathered before the generator exists, but the advancements already add translations
    private static final LocalizationProvider LOCALIZATION = new LocalizationProvider();

    @Override
    public void buildRegistry(RegistrySetBuilder builder) {
        // Worldgen et al
        builder.add(Registries.DIMENSION_TYPE, InitDimensionTypes::init)
                .add(Registries.STRUCTURE, InitStructures::initDatagenStructures)
                .add(Registries.STRUCTURE_SET, InitStructures::initDatagenStructureSets)
                .add(Registries.BIOME, InitBiomes::init);
    }

    @Override
    public void buildReloadableRegistry(RegistrySetBuilder builder) {
        // The villager bootstraps return the holder they registered, so they are wrapped instead of used as a
        // SingleRegistryBootstrap method reference.
        builder.add(Registries.DAMAGE_TYPE, AEDamageTypes::init)
                .add(Registries.TRADE_SET, context -> InitVillager.bootstrapTradeSets(context))
                .add(Registries.VILLAGER_TRADE, context -> InitVillager.bootstrapTrades(context))
                // Loot, advancements and recipes
                .add(Registries.LOOT_TABLE, AE2LootTableProvider.create())
                .add(Registries.ADVANCEMENT, new AdvancementProvider(List.of(
                        context -> new AdvancementGenerator(context, LOCALIZATION))))
                .add(AE2RecipeProvider.create());
    }

    @Override
    public void onInitializeDataGenerator(FabricDataGenerator generator) {
        var pack = generator.createPack();

        // Tags
        var blockTags = pack.addProvider(
                (output, registries) -> new BlockTagsProvider(output, registries));
        pack.addProvider((output, registries) -> new ItemTagsProvider(output, registries,
                blockTags.contentsGetter()));
        pack.addProvider((output, registries) -> new FluidTagsProvider(output, registries));
        pack.addProvider((output, registries) -> new BiomeTagsProvider(output, registries));
        pack.addProvider((output, registries) -> new PoiTypeTagsProvider(output, registries));
        pack.addProvider((output, registries) -> new VillagerTradeTagsProvider(output, registries));
        pack.addProvider((output, registries) -> new DataComponentTypeTagProvider(output, registries,
                LOCALIZATION));

        // Models
        pack.addProvider(AE2ModelProvider.create(
                AppEng.MOD_ID,
                BlockModelProvider::new,
                DecorationModelProvider::new,
                ItemModelProvider::new,
                PartModelProvider::new));

        // Misc
        DataProvider.Factory<AE2ParticleDescriptionProvider> particles = AE2ParticleDescriptionProvider::new;
        pack.addProvider(particles);

        // Must run last
        DataProvider.Factory<LocalizationProvider> localization = output -> {
            LOCALIZATION.setPackOutput(output);
            return LOCALIZATION;
        };
        pack.addProvider(localization);
    }
}
//#else
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.data.event.GatherDataRegistryEntriesEvent;

import appeng.core.AppEng;
import appeng.core.definitions.AEDamageTypes;
import appeng.datagen.providers.AE2ParticleDescriptionProvider;
import appeng.datagen.providers.advancements.AdvancementGenerator;
import appeng.datagen.providers.datamaps.RaidHeroGiftsProvider;
import appeng.datagen.providers.localization.LocalizationProvider;
import appeng.datagen.providers.loot.AE2LootTableProvider;
import appeng.datagen.providers.models.AE2ModelProvider;
import appeng.datagen.providers.models.BlockModelProvider;
import appeng.datagen.providers.models.DecorationModelProvider;
import appeng.datagen.providers.models.ItemModelProvider;
import appeng.datagen.providers.models.PartModelProvider;
import appeng.datagen.providers.recipes.AE2RecipeProvider;
import appeng.datagen.providers.tags.BiomeTagsProvider;
import appeng.datagen.providers.tags.BlockTagsProvider;
import appeng.datagen.providers.tags.DataComponentTypeTagProvider;
import appeng.datagen.providers.tags.FluidTagsProvider;
import appeng.datagen.providers.tags.ItemTagsProvider;
import appeng.datagen.providers.tags.PoiTypeTagsProvider;
import appeng.datagen.providers.tags.VillagerTradeTagsProvider;
import appeng.init.InitVillager;
import appeng.init.worldgen.InitBiomes;
import appeng.init.worldgen.InitDimensionTypes;
import appeng.init.worldgen.InitStructures;

@EventBusSubscriber(modid = AppEng.MOD_ID)
public class AE2DataGenerators {

    // Registry entries are gathered before the generator exists, but the advancements already add translations
    private static final LocalizationProvider LOCALIZATION = new LocalizationProvider();

    @SubscribeEvent
    public static void onGatherRegistryEntries(GatherDataRegistryEntriesEvent event) {
        // Worldgen et al
        event.add(Registries.DIMENSION_TYPE, InitDimensionTypes::init)
                .add(Registries.STRUCTURE, InitStructures::initDatagenStructures)
                .add(Registries.STRUCTURE_SET, InitStructures::initDatagenStructureSets)
                .add(Registries.BIOME, InitBiomes::init)
                .add(Registries.DAMAGE_TYPE, AEDamageTypes::init)
                .add(Registries.TRADE_SET, InitVillager::bootstrapTradeSets)
                .add(Registries.VILLAGER_TRADE, InitVillager::bootstrapTrades);

        // Loot, advancements and recipes
        event.add(Registries.LOOT_TABLE, AE2LootTableProvider.create())
                .advancement(output -> new AdvancementGenerator(output, LOCALIZATION))
                .add(AE2RecipeProvider.create());
    }

    @SubscribeEvent
    public static void onGatherData(GatherDataEvent.Client event) {
        var generator = event.getGenerator();
        LOCALIZATION.setPackOutput(generator.getPackOutput());

        // Tags
        event.createBlockAndItemTags(BlockTagsProvider::new, ItemTagsProvider::new);
        event.createProvider(FluidTagsProvider::new);
        event.createProvider(BiomeTagsProvider::new);
        event.createProvider(PoiTypeTagsProvider::new);
        event.createProvider(VillagerTradeTagsProvider::new);
        event.createProvider((packOutput, registries) -> new DataComponentTypeTagProvider(packOutput, registries,
                LOCALIZATION));

        // Models
        event.addProvider(AE2ModelProvider.create(
                AppEng.MOD_ID,
                BlockModelProvider::new,
                DecorationModelProvider::new,
                ItemModelProvider::new,
                PartModelProvider::new).create(generator.getPackOutput()));

        // Misc
        event.createProvider(AE2ParticleDescriptionProvider::new);

        // DataMaps
        event.createProvider(RaidHeroGiftsProvider::new);

        // Must run last
        event.addProvider(LOCALIZATION);
    }
}
//#endif
