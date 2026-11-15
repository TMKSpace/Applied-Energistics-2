/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.event;

import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.ApiStatus;

/**
 * Fired when a FeatureRenderDispatcher collects all FeatureRenderers for mods to register renderers
 * for custom SubmitNodes.
 * <p>
 * nfcompat: класс нужен для ссылок из сторонних библиотек (GuideME регистрирует свой рендерер
 * сцены). Само событие мы пока не рассылаем — для этого нужен хук в FeatureRenderDispatcher.
 */
public final class RegisterFeatureRenderersEvent extends Event {
    private final FeatureRendererMap featureRenderers;

    @ApiStatus.Internal
    public RegisterFeatureRenderersEvent(FeatureRendererMap featureRenderers) {
        this.featureRenderers = featureRenderers;
    }

    /**
     * Register the provided FeatureRenderer for SubmitNodes referencing the given FeatureRendererType.
     */
    public <S extends SubmitNode> void register(FeatureRendererType<S> type, FeatureRenderer<S> renderer) {
        if (this.featureRenderers.get(type) != null) {
            throw new IllegalStateException("Duplicate renderer registration for " + type);
        }
        this.featureRenderers.put(type, renderer);
    }
}
