/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.event;

import java.util.Map;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.neoforged.neoforge.client.model.standalone.UnbakedStandaloneModel;
import org.jetbrains.annotations.ApiStatus;

/**
 * Houses events related to models.
 */
public abstract class ModelEvent extends Event {
    @ApiStatus.Internal
    protected ModelEvent() {}

    /**
     * Fired when the {@link net.minecraft.client.resources.model.ModelDiscovery} is notified of dependency discovery of its top models.
     * Allows developers to register standalone models to be loaded, along with their dependencies.
     *
     * <p>This event is not {@linkplain ICancellableEvent cancellable}.</p>
     *
     * <p>This event is fired on the mod-specific event bus, only on the {@linkplain LogicalSide#CLIENT logical client}.</p>
     */
    public static class RegisterStandalone extends ModelEvent implements IModBusEvent {
        private final Map<StandaloneModelKey<?>, UnbakedStandaloneModel<?>> modelMap;

        @ApiStatus.Internal
        public RegisterStandalone(Map<StandaloneModelKey<?>, UnbakedStandaloneModel<?>> modelMap) {
            this.modelMap = modelMap;
        }

        /**
         * Registers a {@linkplain UnbakedStandaloneModel model} to be loaded, along with its dependencies.
         */
        public <T> void register(StandaloneModelKey<T> modelKey, UnbakedStandaloneModel<T> baker) {
            modelMap.put(modelKey, baker);
        }
    }
}
