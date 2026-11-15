/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/client/model/pipeline/VertexConsumerWrapper.java
 *
 * SHIM: vanilla MC 26.3's VertexConsumer has no `misc(VertexFormatElement, int...)` hook
 * (that is a NeoForge extension of the vertex pipeline), so that single override from
 * upstream is dropped. Everything else delegates 1:1.
 */

package net.neoforged.neoforge.client.model.pipeline;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Wrapper for {@link VertexConsumer} which delegates all operations to its parent.
 * <p>
 * Useful for defining custom pipeline elements that only process certain data.
 */
public abstract class VertexConsumerWrapper implements VertexConsumer {
    protected final VertexConsumer parent;

    public VertexConsumerWrapper(VertexConsumer parent) {
        this.parent = parent;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        parent.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int packedColor) {
        parent.setColor(packedColor);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        parent.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        parent.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        parent.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        parent.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        parent.setUv3(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        parent.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float lineWidth) {
        parent.setLineWidth(lineWidth);
        return this;
    }
}
