/*
 * Часть nfcompat. В NeoForge FluidType — большой класс (44 КБ): звуки, плотность,
 * вязкость, температуры, взаимодействия. AE2 использует из него одну константу,
 * а скопированный transfer/** — описание и сравнение. Здесь минимум + стабильная
 * идентичность: один FluidType на жидкость, чтобы работали equals/is.
 */
package net.neoforged.neoforge.fluids;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.common.SoundAction;
import org.jetbrains.annotations.Nullable;

public class FluidType {
    /**
     * Объём одного ведра в юнитах шима. Порт целиком живёт в Fabric-шкале droplets,
     * поэтому константа согласована с {@code AEFluidKey.AMOUNT_BUCKET} (81000); иначе
     * FluidUtil/танки и AE-сеть считали бы разные вёдра. NeoForge-ной ветки у шима нет.
     */
    public static final int BUCKET_VOLUME = 81000;

    private static final Map<Fluid, FluidType> BY_FLUID = new ConcurrentHashMap<>();

    private final Fluid fluid;

    protected FluidType(Fluid fluid) {
        this.fluid = fluid;
    }

    private FluidType() {
        this(null);
    }

    public static FluidType of(Fluid fluid) {
        return BY_FLUID.computeIfAbsent(fluid, FluidType::new);
    }

    public Fluid getFluid() {
        return fluid;
    }

    /**
     * Имя жидкости. В NeoForge его даёт FluidType (у воды — «Water»), в ванили такого понятия нет.
     * Раньше мы брали имя ведра ({@code fluid.getBucket()}) и получали «Ведро воды» вместо
     * «Вода»: это лезло в тултипы (например «ПКМ: содержит Ведро воды» дважды) и в JEI.
     * <p>
     * Теперь приоритет — блок жидкости ({@code block.minecraft.water} → «Вода», лава → «Лава»),
     * ведро — только фолбэк для жидкостей без блока/ведра.
     */
    public Component getDescription() {
        if (fluid == null) {
            return Component.empty();
        }

        var state = fluid.defaultFluidState();
        if (!state.isEmpty()) {
            var block = state.createLegacyBlock();
            if (block != null && !block.isAir()) {
                return block.getBlock().getName();
            }
        }

        var bucket = fluid.getBucket();
        if (bucket != null && bucket != net.minecraft.world.item.Items.AIR) {
            return Component.translatable(bucket.getDescriptionId());
        }
        return Component.empty();
    }

    public Component getDescription(FluidStack stack) {
        return getDescription();
    }

    public int getViscosity() {
        return 1000;
    }

    public int getDensity() {
        return 1000;
    }

    public int getTemperature() {
        return 300;
    }

    public boolean isLighterThanAir() {
        return false;
    }

    /**
     * nfcompat: в NeoForge тип жидкости умеет звучать при переливании ведром.
     * У ванильной жидкости своего звука для этого нет — возвращаем null,
     * FluidUtil тогда просто ничего не играет.
     */
    @Nullable
    public SoundEvent getSound(FluidStack stack, SoundAction action) {
        return null;
    }

    /** nfcompat: разрешает ли тип жидкости ставиться в этом месте. По умолчанию — да. */
    public boolean canBePlacedInLevel(Level level, BlockPos pos, FluidStack stack) {
        return true;
    }

    /** nfcompat: должна ли жидкость испариться при попытке поставить её в это место. */
    public boolean isVaporizedOnPlacement(Level level, BlockPos pos, FluidStack stack) {
        return false;
    }

    /** nfcompat: хук на испарение; по умолчанию ничего не делает. */
    public void onVaporize(@Nullable Player player, Level level, BlockPos pos, FluidStack stack) {
    }

    /** nfcompat: каким состоянием жидкости ставить блок. По умолчанию — состоянием по умолчанию. */
    public FluidState getStateForPlacement(Level level, BlockPos pos, FluidStack stack) {
        return stack.getFluid().defaultFluidState();
    }

    /** nfcompat: какой блок соответствует состоянию жидкости. */
    public BlockState getBlockForFluidState(Level level, BlockPos pos, FluidState fluidState) {
        return fluidState.createLegacyBlock();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FluidType other && other.fluid == fluid;
    }

    @Override
    public int hashCode() {
        return fluid == null ? 0 : fluid.hashCode();
    }

    @Override
    public String toString() {
        return "FluidType[" + (fluid == null ? "none" : fluid.builtInRegistryHolder().getRegisteredName()) + "]";
    }
}
