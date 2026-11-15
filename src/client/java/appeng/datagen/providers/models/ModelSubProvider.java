package appeng.datagen.providers.models;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.mojang.math.Quadrant;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
//#if FABRIC
//#else
import net.neoforged.neoforge.client.model.generators.blockstate.CustomBlockStateModelBuilder;
import net.neoforged.neoforge.client.model.generators.blockstate.UnbakedMutator;
//#endif

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.IOrientationStrategy;
import appeng.client.model.SpinnableVariant;
//#if FABRIC
//#else
import appeng.client.render.model.DriveModel;
import appeng.client.render.model.SingleSpinnableVariant;
//#endif
import appeng.core.AppEng;
import appeng.core.definitions.BlockDefinition;

public abstract class ModelSubProvider {
    public static final TextureMapping TRANSPARENT_PARTICLE = TextureMapping
            .particle(makeMaterial("block/transparent"));

    public static final ModelTemplate EMPTY_MODEL = new ModelTemplate(Optional.empty(), Optional.empty(),
            TextureSlot.PARTICLE);

    //#if FABRIC
    /**
     * Model used for every block whose real model is a NeoForge-style custom unbaked blockstate model. Vanilla
     * blockstates can only point at a model file, so those blocks get this placeholder cube and are listed in
     * {@link #STUBBED_MODELS} instead.
     */
    public static final Identifier STUB_MODEL = AppEng.makeId("block/custom_model_placeholder");

    /**
     * Human-readable names of all custom blockstate models that could not be expressed in vanilla blockstate json.
     */
    public static final List<String> STUBBED_MODELS = new CopyOnWriteArrayList<>();

    /**
     * Items whose model is a custom unbaked item model, which vanilla item json cannot reference either.
     */
    public static final List<String> STUBBED_ITEM_MODELS = new CopyOnWriteArrayList<>();
    //#endif

    protected final BlockModelGenerators blockModels;
    protected final Consumer<BlockModelDefinitionGenerator> blockStateOutput;
    protected final ItemModelGenerators itemModels;
    protected final BiConsumer<Identifier, ModelInstance> modelOutput;
    protected final PartModelOutput partModels;

    public ModelSubProvider(BlockModelGenerators blockModels, ItemModelGenerators itemModels,
            PartModelOutput partModels) {
        this.blockModels = blockModels;
        this.itemModels = itemModels;
        this.partModels = partModels;
        this.modelOutput = blockModels.modelOutput;
        this.blockStateOutput = blockModels.blockStateOutput;
    }

    protected abstract void register();

    /**
     * Define a block model that is a simple textured cube, and uses the same model for its item. The texture path is
     * derived from the block's id.
     */
    protected void simpleBlockAndItem(BlockDefinition<?> block) {
        blockModels.createTrivialCube(block.block());
        // item falls back automatically to the block model
    }

    protected void simpleBlockAndItem(BlockDefinition<?> block, TexturedModel.Provider model) {
        blockModels.createTrivialBlock(block.block(), model);
        // item falls back automatically to the block model
    }

    /**
     * Define a block model that is a simple textured cube, and uses the same model for its item.
     */
    protected void simpleBlockAndItem(BlockDefinition<?> block, String textureName) {
        blockModels.createTrivialBlock(
                block.block(),
                TexturedModel.CUBE.updateTexture(mapping -> mapping.put(TextureSlot.ALL, makeMaterial(textureName))));
        // item falls back automatically to the block model
    }

    //#if FABRIC
    /**
     * Vanilla blockstates cannot reference AE2's "spinnable_variant" model type. The spin only adds a z-rotation per
     * state, so it is baked into the variant rotations instead: same geometry, no runtime spin state.
     */
    protected MultiVariantGenerator createSpinnableBlock(BlockDefinition<?> block, Identifier model) {
        return MultiVariantGenerator.dispatch(block.block(), plainSpinnableVariant(model))
                .with(createFacingSpinDispatch());
    }
    //#else
    protected MultiVariantGenerator createSpinnableBlock(BlockDefinition<?> block, Identifier model) {
        return MultiVariantGenerator.dispatch(block.block(), plainSpinnableVariant(model))
                .withUnbaked(createFacingSpinDispatch());
    }
    //#endif

    protected static PropertyDispatch<VariantMutator> createFacingDispatch(int baseRotX, int baseRotY) {
        return PropertyDispatch.modify(BlockStateProperties.FACING)
                .select(Direction.DOWN, applyRotation(baseRotX + 90, baseRotY))
                .select(Direction.UP, applyRotation(baseRotX + 270, baseRotY))
                .select(Direction.NORTH, applyRotation(baseRotX, baseRotY))
                .select(Direction.SOUTH, applyRotation(baseRotX, baseRotY + 180))
                .select(Direction.WEST, applyRotation(baseRotX, baseRotY + 270))
                .select(Direction.EAST, applyRotation(baseRotX, baseRotY + 90));
    }

    //#if FABRIC
    /**
     * Vanilla equivalent of NeoForge's unbaked facing+spin dispatch: the spin is folded into the rotation of the
     * plain variant, which is exactly what NeoForge's spinnable variant ends up rendering.
     */
    protected static PropertyDispatch<VariantMutator> createFacingSpinDispatch() {
        return PropertyDispatch.modify(BlockStateProperties.FACING, IOrientationStrategy.SPIN)
                .generate((facing, spin) -> {
                    var orientation = BlockOrientation.get(facing, spin);
                    return applyRotation(orientation.getAngleX(), orientation.getAngleY(), orientation.getAngleZ());
                });
    }
    //#else
    protected static PropertyDispatch<UnbakedMutator> createFacingSpinDispatch() {
        return PropertyDispatch.modifyUnbaked(BlockStateProperties.FACING, IOrientationStrategy.SPIN)
                .generate((facing, spin) -> {
                    var orientation = BlockOrientation.get(facing, spin);
                    return UnbakedMutator.builder()
                            .add(SingleSpinnableVariant.Unbaked.class, unbaked -> new SingleSpinnableVariant.Unbaked(
                                    applyOrientation(unbaked.variant(), orientation)))
                            .add(DriveModel.Unbaked.class, unbaked -> new DriveModel.Unbaked(
                                    applyOrientation(unbaked.variant(), orientation)))
                            .build();
                });
    }
    //#endif

    protected static void withOrientations(MultiPartGenerator multipart, SpinnableVariant baseVariant) {
        withOrientations(multipart, ConditionBuilder::new, baseVariant);
    }

    protected static void withOrientations(MultiPartGenerator multipart,
            Supplier<ConditionBuilder> baseCondition,
            SpinnableVariant baseVariant) {
        var defaultState = multipart.block().defaultBlockState();
        var strategy = IOrientationStrategy.get(defaultState);

        strategy.getAllStates(defaultState).forEach(blockState -> {
            var condition = baseCondition.get();
            for (var property : strategy.getProperties()) {
                addConditionTerm(condition, blockState, property);
            }
            var orientation = BlockOrientation.get(strategy, blockState);
            //#if FABRIC
            // Vanilla has no unbaked blockstate models: keep the orientation, but as plain variant rotations.
            multipart.with(condition, plainSpinnableVariant(baseVariant.modelLocation())
                    .with(applyRotation(orientation.getAngleX(), orientation.getAngleY(), orientation.getAngleZ())));
            //#else
            multipart.with(condition, customBlockStateModel(
                    new SingleSpinnableVariant.Unbaked(applyOrientation(baseVariant, orientation))));
            //#endif
        });
    }

    protected static SpinnableVariant applyOrientation(SpinnableVariant baseVariant, BlockOrientation orientation) {
        return applyOrientation(baseVariant, orientation.getAngleX(), orientation.getAngleY(), orientation.getAngleZ());
    }

    protected static SpinnableVariant applyOrientation(SpinnableVariant baseVariant, int angleX, int angleY,
            int angleZ) {
        return baseVariant
                .withXRot(rotationByAngle(normalizeAngle(angleX)))
                .withYRot(rotationByAngle(normalizeAngle(angleY)))
                .withZRot(rotationByAngle(normalizeAngle(angleZ)));
    }

    protected static Variant applyOrientation(Variant baseVariant, Direction facing) {
        var orientation = BlockOrientation.get(facing);

        return baseVariant
                .withXRot(rotationByAngle(orientation.getAngleX()))
                .withYRot(rotationByAngle(orientation.getAngleY()));
    }

    protected static VariantMutator applyRotation(int angleX, int angleY) {
        angleX = normalizeAngle(angleX);
        angleY = normalizeAngle(angleY);

        VariantMutator mutator = variant -> variant;
        if (angleX != 0) {
            mutator = mutator.then(VariantMutator.X_ROT.withValue(rotationByAngle(angleX)));
        }
        if (angleY != 0) {
            mutator = mutator.then(VariantMutator.Y_ROT.withValue(rotationByAngle(angleY)));
        }
        return mutator;
    }

    //#if FABRIC
    /**
     * Three-angle variant of {@link #applyRotation(int, int)} - the spin of a spinnable block is a z-rotation and is
     * baked into the plain variant on Fabric.
     */
    protected static VariantMutator applyRotation(int angleX, int angleY, int angleZ) {
        var mutator = applyRotation(angleX, angleY);
        var z = normalizeAngle(angleZ);
        if (z != 0) {
            mutator = mutator.then(VariantMutator.Z_ROT.withValue(rotationByAngle(z)));
        }
        return mutator;
    }
    //#endif

    private static int normalizeAngle(int angle) {
        return angle - (angle / 360) * 360;
    }

    private static Quadrant rotationByAngle(int angle) {
        return switch (angle) {
            case 0 -> Quadrant.R0;
            case 90 -> Quadrant.R90;
            case 180 -> Quadrant.R180;
            case 270 -> Quadrant.R270;
            default -> throw new IllegalArgumentException("Invalid angle: " + angle);
        };
    }

    protected final MultiPartGenerator multiPartGenerator(BlockDefinition<?> blockDef) {
        var multipart = MultiPartGenerator.multiPart(blockDef.block());
        blockModels.blockStateOutput.accept(multipart);
        return multipart;
    }

    private static <T extends Comparable<T>> ConditionBuilder addConditionTerm(ConditionBuilder conditionBuilder,
            BlockState blockState,
            Property<T> property) {
        return conditionBuilder.term(property, blockState.getValue(property));
    }

    protected static Material getBlockTexture(BlockDefinition<?> block) {
        return TextureMapping.getBlockTexture(block.block());
    }

    protected static Material getBlockTexture(Block block) {
        return TextureMapping.getBlockTexture(block);
    }

    protected static Material getBlockTexture(Block block, String suffix) {
        return TextureMapping.getBlockTexture(block, suffix);
    }

    protected static Material getBlockTexture(BlockDefinition<?> block, String suffix) {
        return TextureMapping.getBlockTexture(block.block(), suffix);
    }

    //#if FABRIC
    /**
     * Vanilla blockstates can only reference a model by id, so NeoForge's custom unbaked blockstate models cannot be
     * written to disk at all. They are recorded in {@link #STUBBED_MODELS} and replaced by a placeholder cube, so the
     * generated pack stays valid and loadable.
     */
    protected static MultiVariant customBlockStateModel(CustomUnbakedBlockStateModel model) {
        STUBBED_MODELS.add(model.getClass().getName());
        return plainSpinnableVariant(STUB_MODEL);
    }
    //#else
    protected static MultiVariant customBlockStateModel(CustomUnbakedBlockStateModel model) {
        return MultiVariant.of(new CustomBlockStateModelBuilder.Simple(model));
    }
    //#endif

    public static MultiVariant variant(SpinnableVariant variant) {
        //#if FABRIC
        // SpinnableModel is NeoForge's custom unbaked blockstate model; vanilla can only use the plain model file it
        // wraps. Rotations come from the blockstate dispatch instead.
        return plainSpinnableVariant(variant.modelLocation());
        //#else
        return customBlockStateModel(new SingleSpinnableVariant.Unbaked(variant));
        //#endif
    }

    public static MultiVariant variant(Variant variant) {
        return new MultiVariant(WeightedList.of(variant));
    }

    public static MultiVariant plainSpinnableVariant(Identifier model) {
        //#if FABRIC
        return variant(new Variant(model));
        //#else
        return variant(new SpinnableVariant(model));
        //#endif
    }

    public static MultiVariantGenerator createSimpleBlock(BlockDefinition<?> block, MultiVariant variant) {
        return BlockModelGenerators.createSimpleBlock(block.block(), variant);
    }

    protected static Material makeMaterial(String texture) {
        return new Material(AppEng.makeId(texture));
    }
}
