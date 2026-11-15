/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/client/model/pipeline/QuadBakingVertexConsumer.java
 *
 * SHIM notes, all caused by vanilla MC 26.3 having no NeoForge patches on BakedQuad:
 *
 *  - NeoForge extends BakedQuad with per-vertex normals, per-vertex colors and an
 *    ambient-occlusion flag. Vanilla BakedQuad (a record of 4 positions + 4 packed UVs +
 *    direction + MaterialInfo) has slots for none of them, and vanilla MaterialInfo has
 *    no trailing `ambientOcclusion` component. The setters are kept so that call sites
 *    compile, the values are simply dropped when the quad is baked (see #bakeQuad).
 *  - Vanilla ModelBaker.Interner only interns Vector3fc and MaterialInfo.
 *
 * Everything else (vertex bookkeeping, sprite -> RenderType/ChunkSectionLayer selection,
 * MaterialInfo assembly) matches upstream.
 */

package net.neoforged.neoforge.client.model.pipeline;

import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.model.quad.BakedNormals;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Vertex consumer that outputs {@linkplain BakedQuad baked quads}.
 * <p>
 * This consumer accepts data in {@link com.mojang.blaze3d.vertex.DefaultVertexFormat#BLOCK} and is not picky about
 * ordering or missing elements, but will not automatically populate missing data (color will be black, for example).
 * <p>
 * Built quads must be retrieved after building four vertices
 */
public class QuadBakingVertexConsumer implements VertexConsumer {
    private final Vector3f[] positions = new Vector3f[4];
    private final long[] uvs = new long[4];
    /**
     * SHIM: dropped when baking, vanilla {@link BakedQuad} has no normal slot.
     */
    private final int[] normals = new int[4];
    /**
     * SHIM: dropped when baking, vanilla {@link BakedQuad} has no color slot.
     */
    private final int[] colors = new int[4];
    private int vertexIndex = 0;
    private boolean building = false;

    private int tintIndex = -1;
    private Direction direction = Direction.DOWN;
    @Nullable
    private TextureAtlasSprite sprite;
    @Nullable
    private ChunkSectionLayer chunkLayer;
    @Nullable
    private RenderType itemRenderType;
    @Nullable
    private RenderType itemGlintRenderType;
    @Nullable
    private RenderType itemGlintSpecialRenderType;
    @Nullable
    private Direction shadeOverride;
    private int lightEmission;
    /**
     * SHIM: vanilla {@link BakedQuad.MaterialInfo} has no ambient occlusion flag, so this value is never read.
     */
    private boolean ambientOcclusion;

    public QuadBakingVertexConsumer() {
        clear();
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (building) {
            if (++vertexIndex > 4) {
                throw new IllegalStateException("Expected quad export after fourth vertex");
            }
        }
        building = true;

        positions[vertexIndex].set(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        normals[vertexIndex] = BakedNormals.pack(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int packedColor) {
        colors[vertexIndex] = packedColor;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor(ARGB.color(a, r, g, b));
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        uvs[vertexIndex] = UVPair.pack(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float lineWidth) {
        return this;
    }

    public void setTintIndex(int tintIndex) {
        this.tintIndex = tintIndex;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public void setSprite(Material.Baked material) {
        setSprite(material, material.forceTranslucent() ? Transparency.TRANSLUCENT : material.sprite().transparency());
    }

    public void setSprite(Material.Baked material, Transparency transparency) {
        RenderType itemRenderType;
        RenderType itemGlintRenderType;
        RenderType itemGlintSpecialRenderType;
        if (material.sprite().atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS)) {
            itemRenderType = transparency.hasTranslucent() ? Sheets.translucentBlockItemSheet() : Sheets.cutoutBlockItemSheet();
            itemGlintRenderType = transparency.hasTranslucent() ? Sheets.translucentBlockItemGlintSheet() : Sheets.cutoutBlockItemGlintSheet();
            itemGlintSpecialRenderType = transparency.hasTranslucent() ? Sheets.translucentBlockItemGlintSpecialSheet() : Sheets.cutoutBlockItemGlintSpecialSheet();
        } else {
            itemRenderType = transparency.hasTranslucent() ? Sheets.translucentItemSheet() : Sheets.cutoutItemSheet();
            itemGlintRenderType = transparency.hasTranslucent() ? Sheets.translucentItemGlintSheet() : Sheets.cutoutItemGlintSheet();
            itemGlintSpecialRenderType = transparency.hasTranslucent() ? Sheets.translucentItemGlintSpecialSheet() : Sheets.cutoutItemGlintSpecialSheet();
        }
        setSprite(material.sprite(), ChunkSectionLayer.byTransparency(transparency), itemRenderType, itemGlintRenderType, itemGlintSpecialRenderType);
    }

    public void setSprite(TextureAtlasSprite sprite, ChunkSectionLayer chunkLayer, RenderType itemRenderType, RenderType itemGlintRenderType, RenderType itemGlintSpecialRenderType) {
        this.sprite = sprite;
        this.chunkLayer = chunkLayer;
        this.itemRenderType = itemRenderType;
        this.itemGlintRenderType = itemGlintRenderType;
        this.itemGlintSpecialRenderType = itemGlintSpecialRenderType;
    }

    public void setShadeOverride(@Nullable Direction shadeOverride) {
        this.shadeOverride = shadeOverride;
    }

    public void setLightEmission(int lightEmission) {
        this.lightEmission = lightEmission;
    }

    /**
     * SHIM: accepted for source compatibility, but vanilla {@link BakedQuad.MaterialInfo} has no ambient
     * occlusion flag, so the value is discarded.
     */
    public void setAmbientOcclusion(boolean ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
    }

    public BakedQuad bakeQuad() {
        return bakeQuad(null);
    }

    public BakedQuad bakeQuad(ModelBaker.@Nullable Interner interner) {
        if (!building || ++vertexIndex != 4) {
            throw new IllegalStateException("Not enough vertices available. Vertices in buffer: " + vertexIndex);
        }
        if (sprite == null) {
            throw new IllegalStateException("No sprite set");
        }
        if (chunkLayer == null) {
            throw new IllegalStateException("No ChunkSectionLayer set");
        }
        if (itemRenderType == null) {
            throw new IllegalStateException("No item RenderType set");
        } else if (itemGlintRenderType == null) {
            throw new IllegalStateException("No item glint RenderType set");
        } else if (itemGlintSpecialRenderType == null) {
            throw new IllegalStateException("No item glint special RenderType set");
        }

        BakedQuad.MaterialInfo materialInfo = newMaterialInfo();
        Vector3f position0 = positions[0];
        Vector3f position1 = positions[1];
        Vector3f position2 = positions[2];
        Vector3f position3 = positions[3];
        if (interner != null) {
            materialInfo = interner.materialInfo(materialInfo);
            position0 = new Vector3f(interner.vector(position0));
            position1 = new Vector3f(interner.vector(position1));
            position2 = new Vector3f(interner.vector(position2));
            position3 = new Vector3f(interner.vector(position3));
        }
        // SHIM: the per-vertex normals, colors and the ambient occlusion flag collected above are
        // not representable in vanilla BakedQuad and are dropped here.
        BakedQuad quad = new BakedQuad(
                position0,
                position1,
                position2,
                position3,
                uvs[0],
                uvs[1],
                uvs[2],
                uvs[3],
                direction,
                materialInfo);
        clear();
        return quad;
    }

    private BakedQuad.MaterialInfo newMaterialInfo() {
        // SHIM: vanilla MaterialInfo has no trailing ambientOcclusion flag.
        return new BakedQuad.MaterialInfo(
                sprite,
                chunkLayer,
                itemRenderType,
                itemGlintRenderType,
                itemGlintSpecialRenderType,
                tintIndex,
                shadeOverride,
                lightEmission);
    }

    private void clear() {
        vertexIndex = 0;
        building = false;
        Arrays.setAll(positions, _ -> new Vector3f());
        Arrays.fill(uvs, 0L);
        Arrays.fill(normals, 0);
        Arrays.fill(colors, 0xFFFFFFFF);
        direction = Direction.DOWN;
        sprite = null;
        chunkLayer = null;
        itemRenderType = null;
        itemGlintRenderType = null;
        itemGlintSpecialRenderType = null;
        shadeOverride = null;
        lightEmission = 0;
        ambientOcclusion = true;
    }
}
