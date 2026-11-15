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

package appeng.init.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.world.NeoForgeEnvironmentAttributes;

import appeng.spatial.SpatialStorageDimensionIds;

public final class InitBiomes {

    private InitBiomes() {
    }

    public static void init(BootstrapContext<Biome> context) {
        var placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        var configuredCarvers = context.lookup(Registries.CARVER);

        var specialEffects = new BiomeSpecialEffects.Builder()
                .waterColor(4159204)
                .build();

        var generationSettings = new BiomeGenerationSettings.Builder(placedFeatures, configuredCarvers)
                .build();

        Biome biome = new Biome.BiomeBuilder()
                .generationSettings(generationSettings)
                .hasPrecipitation(false)
                // Copied from the vanilla void biome
                .temperature(0.5F).downfall(0.5F)
                .specialEffects(specialEffects)
//#if FABRIC
                // nfcompat: neoforge:custom_{weather_effects,clouds,skybox} — атрибуты,
                // которых нет в ванильном реестре environment_attribute, поэтому биом
                // с ними не проходит загрузку реестров. Пространственное хранилище
                // останется без своего неба/облаков, пока эти атрибуты не зарегистрированы.
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .build();
//#else
                .mobSpawnSettings(new MobSpawnSettings.Builder().build())
                .setAttribute(NeoForgeEnvironmentAttributes.CUSTOM_WEATHER_EFFECTS,
                        SpatialStorageDimensionIds.CUSTOM_RENDERER_ID)
                .setAttribute(NeoForgeEnvironmentAttributes.CUSTOM_CLOUDS,
                        SpatialStorageDimensionIds.CUSTOM_RENDERER_ID)
                .setAttribute(NeoForgeEnvironmentAttributes.CUSTOM_SKYBOX,
                        SpatialStorageDimensionIds.CUSTOM_RENDERER_ID)
                .build();
//#endif

        context.register(SpatialStorageDimensionIds.BIOME_KEY, biome);
    }

}
