package appeng.init.worldgen;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TimelineTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.dimension.DimensionType;

import appeng.spatial.SpatialStorageChunkGenerator;
import appeng.spatial.SpatialStorageDimensionIds;

public final class InitDimensionTypes {
    private InitDimensionTypes() {
    }

    public static void init(BootstrapContext<DimensionType> context) {
        DimensionType dimensionType = createSpatialDimensionType(context);

        context.register(SpatialStorageDimensionIds.DIMENSION_TYPE_ID,
                dimensionType);
    }

    @NotNull
    private static DimensionType createSpatialDimensionType(BootstrapContext<DimensionType> context) {
        var timelines = context.lookup(Registries.TIMELINE);
        var blocks = context.lookup(Registries.BLOCK);

        return new DimensionType(
                true, // fixedTime
                false, // hasSkylight
                false, // hasCeiling
                false,
                1.0, // coordinateScale
                SpatialStorageChunkGenerator.MIN_Y, // minY
                SpatialStorageChunkGenerator.HEIGHT, // height
                SpatialStorageChunkGenerator.HEIGHT, // logicalHeight
                blocks.getOrThrow(BlockTags.INFINIBURN_OVERWORLD), // infiniburn
                1.0f, // ambientLight
                new DimensionType.MonsterSettings(ConstantInt.of(0), 0),
                DimensionType.Skybox.OVERWORLD,
                CardinalLighting.Type.DEFAULT,
                EnvironmentAttributeMap.builder()
                        .set(EnvironmentAttributes.BED_RULE, BedRule.DESTROY_ON_USE)
                        .set(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, false)
                        //#if FABRIC
                        // В 26.3 яркость измерения без скайлайта задаётся атрибутом
                        // `minecraft:gameplay/sky_light_level` (так делает ванильный Незер: 4.0).
                        // NeoForge-сборка AE2 этот атрибут не ставит, и spatial-измерение чёрное
                        // «хоть глаз выколи»: света 0 и днём, и ночью. Ставим полную яркость.
                        .set(EnvironmentAttributes.SKY_LIGHT_LEVEL, 15.0f)
                        //#endif
                        // TODO 1.21.11: environmental effects SpatialStorageDimensionIds.SKY_PROPERTIES_ID
                        .build(),
                timelines.getOrThrow(TimelineTags.UNIVERSAL),
                Optional.empty());
    }
}
