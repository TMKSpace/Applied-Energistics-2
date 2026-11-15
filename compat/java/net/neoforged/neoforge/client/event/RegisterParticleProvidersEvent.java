/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Fired for registering particle providers on startup.
 *
 * <p>This event is not cancellable.</p>
 *
 * <p>This event is fired on the mod bus, only on the {@linkplain LogicalSide#CLIENT logical client}.</p>
 *
 * nfcompat: в NeoForge регистрация идёт в патченный ParticleResources (#register) и
 * используется ParticleResources.SpriteParticleRegistration. В ванили такого API нет, поэтому
 * мы копим провайдеры у себя, а применяем их там, где Fabric позволяет их подставить.
 * Сигнатуры совместимы с AE2: {@code event.registerSpriteSet(type, Factory::new)}.
 */
public class RegisterParticleProvidersEvent extends Event implements IModBusEvent {
    private final Map<ParticleType<?>, ParticleProvider<?>> providers = new LinkedHashMap<>();
    private final Map<ParticleType<?>, Function<SpriteSet, ? extends ParticleProvider<?>>> spriteProviders = new LinkedHashMap<>();

    @ApiStatus.Internal
    public RegisterParticleProvidersEvent() {
    }

    /**
     * Registers a {@link ParticleProvider} for a particle type whose provider does not need sprites.
     */
    public <T extends ParticleOptions> void registerSpecial(ParticleType<T> type, ParticleProvider<T> provider) {
        providers.put(type, provider);
    }

    /**
     * Registers a provider factory that receives the {@link SpriteSet} of the particle's json.
     */
    public <T extends ParticleOptions> void registerSpriteSet(ParticleType<T> type,
            Function<SpriteSet, ? extends ParticleProvider<T>> registration) {
        spriteProviders.put(type, registration);
    }

    /** Провайдеры без спрайтов, в порядке регистрации. */
    public Map<ParticleType<?>, ParticleProvider<?>> getSpecialProviders() {
        return providers;
    }

    /** Провайдеры, ждущие {@link SpriteSet}. */
    public Map<ParticleType<?>, Function<SpriteSet, ? extends ParticleProvider<?>>> getSpriteProviders() {
        return spriteProviders;
    }

    @Nullable
    public ParticleProvider<?> getSpecialProvider(ParticleType<?> type) {
        return providers.get(type);
    }
}
