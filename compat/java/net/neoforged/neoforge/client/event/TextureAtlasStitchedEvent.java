/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.neoforge.client.event;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Fired after a texture atlas is stitched together and all textures therein have been loaded.
 * <p>
 * nfcompat: класс нужен для ссылок из сторонних библиотек (GuideME сбрасывает по нему кэш
 * иконок поиска). Рассылку события можно добавить миксином на TextureAtlas позже.
 */
public class TextureAtlasStitchedEvent extends Event implements IModBusEvent {
    private final TextureAtlas atlas;

    @ApiStatus.Internal
    public TextureAtlasStitchedEvent(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    /**
     * {@return the texture atlas}
     */
    public TextureAtlas getAtlas() {
        return atlas;
    }
}
