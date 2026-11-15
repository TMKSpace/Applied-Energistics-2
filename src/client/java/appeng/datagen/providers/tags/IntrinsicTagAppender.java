/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2026, TeamAppliedEnergistics, All rights reserved.
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

package appeng.datagen.providers.tags;

import net.minecraft.core.Registry;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
//#if FABRIC
import net.minecraft.tags.TagBuilder;
//#endif
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;

//#if FABRIC
/**
 * Vanilla's {@link TagAppender} only accepts resource keys since 26.2 and has no way to append a raw
 * {@link TagEntry}. This wrapper restores both: it adds the registered-object overload AE2 uses everywhere and
 * keeps the backing {@link TagBuilder} around to append pre-built entries.
 * <p>
 * Fabric note: NeoForge's {@code TagAppender} also gained {@code remove(...)}. Vanilla's {@link TagBuilder} has no
 * removal support at all, so those methods do not exist here - tag entries can only be appended.
 */
public record IntrinsicTagAppender<T>(TagAppender<T> delegate, TagBuilder builder, Registry<T> registry)
        implements TagAppender<T> {
    @SafeVarargs
    public final IntrinsicTagAppender<T> add(T... values) {
        for (var value : values) {
            delegate.add(registry.getResourceKey(value)
                    .orElseThrow(() -> new IllegalArgumentException("Unregistered object: " + value)));
        }
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> add(ResourceKey<T> element) {
        delegate.add(element);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> addOptional(ResourceKey<T> element) {
        delegate.addOptional(element);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> addTag(TagKey<T> tag) {
        delegate.addTag(tag);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> addOptionalTag(TagKey<T> tag) {
        delegate.addOptionalTag(tag);
        return this;
    }

    /**
     * Appends a fully built tag entry (element, optional element, tag or optional tag).
     */
    public IntrinsicTagAppender<T> add(TagEntry entry) {
        builder.add(entry);
        return this;
    }

    /**
     * Replacement for vanilla's {@code TagsProvider#tag(TagKey, boolean)}, which AE2 needs combined with the
     * object-based {@code add} overloads above.
     */
    public IntrinsicTagAppender<T> replace(boolean value) {
        builder.setReplace(value);
        return this;
    }
}
//#else
/**
 * Vanilla's {@link TagAppender} only accepts resource keys since 26.2. This wrapper restores the ability to add
 * registered objects directly by looking up their key in the given registry.
 */
public record IntrinsicTagAppender<T>(TagAppender<T> delegate, Registry<T> registry) implements TagAppender<T> {
    @SafeVarargs
    public final IntrinsicTagAppender<T> add(T... values) {
        for (var value : values) {
            delegate.add(registry.getResourceKey(value)
                    .orElseThrow(() -> new IllegalArgumentException("Unregistered object: " + value)));
        }
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> add(ResourceKey<T> element) {
        delegate.add(element);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> addOptional(ResourceKey<T> element) {
        delegate.addOptional(element);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> addTag(TagKey<T> tag) {
        delegate.addTag(tag);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> addOptionalTag(TagKey<T> tag) {
        delegate.addOptionalTag(tag);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> add(TagEntry entry) {
        delegate.add(entry);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> replace(boolean value) {
        delegate.replace(value);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> remove(ResourceKey<T> element) {
        delegate.remove(element);
        return this;
    }

    @Override
    public IntrinsicTagAppender<T> remove(TagKey<T> tag) {
        delegate.remove(tag);
        return this;
    }
}
//#endif
