/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/client/model/quad/MutableQuad.java
 *
 * Adaptations for unpatched vanilla MC 26.3 types are marked with "SHIM:" comments below.
 * The storage of normals/colors/ambient-occlusion lives in this class instead of in BakedQuad,
 * because BakedQuad is a vanilla record we cannot patch on Fabric.
 */

package net.neoforged.neoforge.client.model.quad;

import com.mojang.blaze3d.platform.Transparency;
import java.util.Arrays;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.Contract;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A mutable representation of a {@link BakedQuad}.
 *
 * <p>This class can be used for constructing quads from scratch, or for loading and modifying existing quads.
 *
 * @see QuadTransforms
 * @see UVTransform
 */
public class MutableQuad {
    private final Vector3f[] positions = new Vector3f[] {
            new Vector3f(),
            new Vector3f(),
            new Vector3f(),
            new Vector3f()
    };
    private final long[] uvs = new long[4];
    private final int[] normals = new int[4];
    private final int[] colors = new int[4];

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
    private int tintIndex = -1;
    @Nullable
    private Direction shadeOverride = null;
    private int lightEmission;
    private boolean ambientOcclusion;
    /**
     * This is only used to reuse position vectors when possible.
     */
    @Nullable
    private BakedQuad lastSourceQuad;

    public MutableQuad() {
        reset();
    }

    /**
     * {@return the x-component of a vertex's position}
     */
    @Contract(pure = true)
    public float x(int vertexIndex) {
        return positions[vertexIndex].x;
    }

    /**
     * {@return the y-component of a vertex's position}
     */
    @Contract(pure = true)
    public float y(int vertexIndex) {
        return positions[vertexIndex].y;
    }

    /**
     * {@return the z-component of a vertex's position}
     */
    @Contract(pure = true)
    public float z(int vertexIndex) {
        return positions[vertexIndex].z;
    }

    /**
     * {@return a component of a vertex's position}
     */
    @Contract(pure = true)
    public float positionComponent(int vertexIndex, int componentIndex) {
        return positions[vertexIndex].get(componentIndex);
    }

    /**
     * Copies a vertex's position into a new vector and returns it.
     */
    @Contract(pure = true)
    public Vector3f copyPosition(int vertexIndex) {
        return new Vector3f(positions[vertexIndex]);
    }

    /**
     * Copies a vertex's position into the given vector and returns it.
     */
    public Vector3f copyPosition(int vertexIndex, Vector3f dest) {
        var pos = positions[vertexIndex];
        dest.set(pos);
        return dest;
    }

    public MutableQuad setX(int vertexIndex, float x) {
        positions[vertexIndex].x = x;
        return this;
    }

    public MutableQuad setY(int vertexIndex, float y) {
        positions[vertexIndex].y = y;
        return this;
    }

    public MutableQuad setZ(int vertexIndex, float z) {
        positions[vertexIndex].z = z;
        return this;
    }

    public MutableQuad setPositionComponent(int vertexIndex, int componentIndex, float value) {
        positions[vertexIndex].setComponent(componentIndex, value);
        return this;
    }

    public MutableQuad setPosition(int vertexIndex, float x, float y, float z) {
        positions[vertexIndex].set(x, y, z);
        return this;
    }

    public MutableQuad setPosition(int vertexIndex, Vector3fc position) {
        positions[vertexIndex].set(position);
        return this;
    }

    /**
     * Sets the positions of this quad to form a rectangle on the given block side using a coordinate-system matching
     * the default orientation of sprites in Vanilla block-models.
     * <p>All coordinates use a normalized [0,1] range.
     */
    public MutableQuad setCubeFaceFromSpriteCoords(Direction side,
            float left,
            float bottom,
            float right,
            float top,
            float depth) {
        this.direction = side;

        switch (side) {
            case NORTH -> {
                positions[0].set(1 - left, top, depth);
                positions[1].set(1 - left, bottom, depth);
                positions[2].set(1 - right, bottom, depth);
                positions[3].set(1 - right, top, depth);
            }
            case SOUTH -> {
                positions[0].set(left, top, 1 - depth);
                positions[1].set(left, bottom, 1 - depth);
                positions[2].set(right, bottom, 1 - depth);
                positions[3].set(right, top, 1 - depth);
            }
            case EAST -> {
                positions[0].set(1 - depth, top, 1 - left);
                positions[1].set(1 - depth, bottom, 1 - left);
                positions[2].set(1 - depth, bottom, 1 - right);
                positions[3].set(1 - depth, top, 1 - right);
            }
            case WEST -> {
                positions[0].set(depth, top, left);
                positions[1].set(depth, bottom, left);
                positions[2].set(depth, bottom, right);
                positions[3].set(depth, top, right);
            }
            case UP -> {
                positions[0].set(left, 1 - depth, 1 - top);
                positions[1].set(left, 1 - depth, 1 - bottom);
                positions[2].set(right, 1 - depth, 1 - bottom);
                positions[3].set(right, 1 - depth, 1 - top);
            }
            case DOWN -> {
                positions[0].set(left, depth, top);
                positions[1].set(left, depth, bottom);
                positions[2].set(right, depth, bottom);
                positions[3].set(right, depth, top);
            }
        }
        return this;
    }

    /**
     * Same as {@link #setCubeFace(Direction, float, float, float, float, float, float)}, but uses the full cube.
     */
    public MutableQuad setFullCubeFace(Direction side) {
        return setCubeFace(side, 0, 0, 0, 1, 1, 1);
    }

    /**
     * Same as {@link #setCubeFace(Direction, float, float, float, float, float, float)}, but takes the from and to
     * positions from vectors.
     */
    public MutableQuad setCubeFace(Direction side, Vector3fc from, Vector3fc to) {
        return setCubeFace(side, from.x(), from.y(), from.z(), to.x(), to.y(), to.z());
    }

    /**
     * Sets the positions of this quad to the face of a cube as it would be defined in a Vanilla block model.
     * <p>All coordinates use a normalized [0,1] range.
     */
    public MutableQuad setCubeFace(Direction side,
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ) {
        this.direction = side;

        for (int i = 0; i < 4; i++) {
            var vertexInfo = FaceInfo.fromFacing(side).getVertexInfo(i);
            positions[i].set(
                    vertexInfo.xFace().select(fromX, fromY, fromZ, toX, toY, toZ),
                    vertexInfo.yFace().select(fromX, fromY, fromZ, toX, toY, toZ),
                    vertexInfo.zFace().select(fromX, fromY, fromZ, toX, toY, toZ));
        }
        return this;
    }

    /**
     * {@return the horizontal texture coordinate in atlas-space for a vertex}
     */
    @Contract(pure = true)
    public float u(int vertexIndex) {
        return UVPair.unpackU(uvs[vertexIndex]);
    }

    /**
     * {@return the vertical texture coordinate in atlas-space for a vertex}
     */
    @Contract(pure = true)
    public float v(int vertexIndex) {
        return UVPair.unpackV(uvs[vertexIndex]);
    }

    /**
     * {@return the a texture coordinate in atlas-space for a vertex}
     */
    @Contract(pure = true)
    public float uvComponent(int vertexIndex, int componentIndex) {
        return switch (componentIndex) {
            case 0 -> u(vertexIndex);
            case 1 -> v(vertexIndex);
            default -> throw new IllegalArgumentException("Invalid UV index: " + componentIndex);
        };
    }

    /**
     * {@return the texture coordinates in atlas-space for a vertex in packed form}
     */
    @Contract(pure = true)
    public long packedUv(int vertexIndex) {
        return uvs[vertexIndex];
    }

    /**
     * Same as {@link #copyUv(int, Vector2f)}, but constructs a destination vector automatically.
     */
    @Contract(pure = true)
    public Vector2f copyUv(int vertexIndex) {
        return copyUv(vertexIndex, new Vector2f());
    }

    /**
     * Copies the texture coordinates of a vertex into a given vector and returns it.
     */
    public Vector2f copyUv(int vertexIndex, Vector2f dest) {
        var packedUv = uvs[vertexIndex];
        dest.x = UVPair.unpackU(packedUv);
        dest.y = UVPair.unpackV(packedUv);
        return dest;
    }

    /**
     * Sets the texture coordinate of a vertex.
     * <p>Note that this method expects texture coordinates in the coordinate space of the atlas, not the sprite.
     */
    public MutableQuad setUv(int vertexIndex, float u, float v) {
        uvs[vertexIndex] = UVPair.pack(u, v);
        return this;
    }

    /**
     * Sets the texture coordinate of a vertex.
     */
    public MutableQuad setUv(int vertexIndex, Vector2fc uv) {
        return setUv(vertexIndex, uv.x(), uv.y());
    }

    /**
     * Sets a component of the texture coordinate of a vertex.
     */
    public MutableQuad setUvComponent(int vertexIndex, int componentIndex, float value) {
        return switch (componentIndex) {
            case 0 -> setUv(vertexIndex, value, v(vertexIndex));
            case 1 -> setUv(vertexIndex, u(vertexIndex), value);
            default -> throw new IllegalArgumentException("Invalid UV index: " + componentIndex);
        };
    }

    /**
     * Sets the texture coordinate of a vertex from their packed representation.
     */
    public MutableQuad setPackedUv(int vertexIndex, long packedUv) {
        uvs[vertexIndex] = packedUv;
        return this;
    }

    /**
     * Assigns UV coordinates to a vertex of the current quad based on its {@linkplain #sprite() sprite} and the
     * given UV coordinates within that sprite.
     */
    public MutableQuad setUvFromSprite(int vertexIndex, float u, float v) {
        TextureAtlasSprite sprite = requiredSprite();
        return setUv(vertexIndex, sprite.getU(u), sprite.getV(v));
    }

    /**
     * Assigns UV coordinates to a vertex of the current quad based on its {@linkplain #sprite() sprite} and the
     * given UV coordinates within that sprite.
     */
    public MutableQuad setUvFromSprite(int vertexIndex, Vector2fc uv) {
        return setUvFromSprite(vertexIndex, uv.x(), uv.y());
    }

    /**
     * Projects each vertex onto the cube face the quad is sourcing its block lighting from,
     * and derives the vertex UV that way.
     */
    public MutableQuad bakeUvsFromPosition() {
        return bakeUvsFromPosition(UVTransform.IDENTITY);
    }

    /**
     * Same as {@link #bakeUvsFromPosition()}, but applies a transform to the generated UVs before baking.
     */
    public MutableQuad bakeUvsFromPosition(UVTransform transform) {
        switch (direction) {
            case DOWN -> {
                for (int i = 0; i < 4; i++) {
                    uvs[i] = UVPair.pack(positions[i].x, 1 - positions[i].z);
                }
            }
            case UP -> {
                for (int i = 0; i < 4; i++) {
                    uvs[i] = UVPair.pack(positions[i].x, positions[i].z);
                }
            }
            case NORTH -> {
                for (int i = 0; i < 4; i++) {
                    uvs[i] = UVPair.pack(1 - positions[i].x, 1 - positions[i].y);
                }
            }
            case SOUTH -> {
                for (int i = 0; i < 4; i++) {
                    uvs[i] = UVPair.pack(positions[i].x, 1 - positions[i].y);
                }
            }
            case WEST -> {
                for (int i = 0; i < 4; i++) {
                    uvs[i] = UVPair.pack(positions[i].z, 1 - positions[i].y);
                }
            }
            case EAST -> {
                for (int i = 0; i < 4; i++) {
                    uvs[i] = UVPair.pack(1 - positions[i].z, 1 - positions[i].y);
                }
            }
        }

        if (!transform.isIdentity()) {
            for (int i = 0; i < 4; i++) {
                uvs[i] = transform.transformPacked(uvs[i]);
            }
        }

        transformUvsFromSpriteToAtlas();
        return this;
    }

    @Contract(pure = true)
    public int tintIndex() {
        return tintIndex;
    }

    public MutableQuad setTintIndex(int tintIndex) {
        this.tintIndex = tintIndex;
        return this;
    }

    @Contract(pure = true)
    public Direction direction() {
        return direction;
    }

    public MutableQuad setDirection(Direction direction) {
        this.direction = direction;
        return this;
    }

    /**
     * {@return the sprite associated with the quad or null if no sprite has been set yet}
     */
    @Contract(pure = true)
    @Nullable
    public TextureAtlasSprite sprite() {
        return sprite;
    }

    /**
     * Same as {@link #sprite()}, but throws an exception if no sprite is set on the quad yet.
     */
    @Contract(pure = true)
    public TextureAtlasSprite requiredSprite() {
        if (sprite == null) {
            throw new IllegalStateException("A sprite has to be set on this quad before UVs are manipulated");
        }
        return sprite;
    }

    /**
     * {@return the chunk layer associated with the quad or null if no chunk layer has been set yet}
     */
    @Contract(pure = true)
    @Nullable
    public ChunkSectionLayer chunkLayer() {
        return chunkLayer;
    }

    @Contract(pure = true)
    public ChunkSectionLayer requiredChunkLayer() {
        if (chunkLayer == null) {
            throw new IllegalStateException("A ChunkSectionLayer has to be set on this quad before baking");
        }
        return chunkLayer;
    }

    @Contract(pure = true)
    @Nullable
    public RenderType itemRenderType() {
        return itemRenderType;
    }

    @Contract(pure = true)
    public RenderType requiredItemRenderType() {
        if (itemRenderType == null) {
            throw new IllegalStateException("An item RenderType has to be set on this quad before baking");
        }
        return itemRenderType;
    }

    @Contract(pure = true)
    @Nullable
    public RenderType itemGlintRenderType() {
        return itemRenderType;
    }

    @Contract(pure = true)
    public RenderType requiredItemGlintRenderType() {
        if (itemGlintRenderType == null) {
            throw new IllegalStateException("An item RenderType has to be set on this quad before baking");
        }
        return itemGlintRenderType;
    }

    @Contract(pure = true)
    @Nullable
    public RenderType itemGlintSpecialRenderType() {
        return itemGlintSpecialRenderType;
    }

    @Contract(pure = true)
    public RenderType requiredItemGlintSpecialRenderType() {
        if (itemGlintSpecialRenderType == null) {
            throw new IllegalStateException("An item RenderType has to be set on this quad before baking");
        }
        return itemGlintSpecialRenderType;
    }

    /**
     * Changes the material used by this quad and sets the {@link #itemRenderType()} and {@link #chunkLayer()}
     * based on the material.
     */
    public MutableQuad setSprite(Material.Baked material) {
        return setSprite(material, getTransparency(material));
    }

    /**
     * Changes the texture atlas sprite used by this quad.
     */
    public MutableQuad setSprite(Material.Baked material, Transparency transparency) {
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
        return this;
    }

    /**
     * Changes the texture atlas sprite used by this quad.
     */
    public MutableQuad setSprite(TextureAtlasSprite sprite, ChunkSectionLayer chunkLayer, RenderType itemRenderType, RenderType itemGlintRenderType, RenderType itemGlintSpecialRenderType) {
        this.sprite = sprite;
        this.chunkLayer = chunkLayer;
        this.itemRenderType = itemRenderType;
        this.itemGlintRenderType = itemGlintRenderType;
        this.itemGlintSpecialRenderType = itemGlintSpecialRenderType;
        return this;
    }

    /**
     * Changes the sprite and remaps the UV to the new sprites position in the texture atlas.
     */
    public MutableQuad setSpriteAndMoveUv(Material.Baked material) {
        return setSpriteAndMoveUv(material, getTransparency(material));
    }

    /**
     * Changes the sprite and remaps the UV to the new sprites position in the texture atlas.
     */
    public MutableQuad setSpriteAndMoveUv(Material.Baked material, Transparency transparency) {
        transformUvsFromAtlasToSprite();
        setSprite(material, transparency);
        transformUvsFromSpriteToAtlas();
        return this;
    }

    /**
     * Changes the sprite and remaps the UV to the new sprites position in the texture atlas.
     */
    public MutableQuad setSpriteAndMoveUv(TextureAtlasSprite sprite, ChunkSectionLayer chunkLayer, RenderType itemRenderType, RenderType itemGlintRenderType, RenderType itemGlintSpecialRenderType) {
        transformUvsFromAtlasToSprite();
        setSprite(sprite, chunkLayer, itemRenderType, itemGlintRenderType, itemGlintSpecialRenderType);
        transformUvsFromSpriteToAtlas();
        return this;
    }

    @Nullable
    @Contract(pure = true)
    public Direction shadeOverride() {
        return shadeOverride;
    }

    public MutableQuad setShadeOverride(@Nullable Direction shadeOverride) {
        this.shadeOverride = shadeOverride;
        return this;
    }

    @Contract(pure = true)
    public int lightEmission() {
        return lightEmission;
    }

    public MutableQuad setLightEmission(int lightEmission) {
        this.lightEmission = lightEmission;
        return this;
    }

    /**
     * {@return the x-component of a vertex's normal or NaN if the normal is undefined}
     */
    @Contract(pure = true)
    public float normalX(int vertexIndex) {
        return normalComponent(vertexIndex, 0);
    }

    /**
     * {@return the y-component of a vertex's normal or NaN if the normal is undefined}
     */
    @Contract(pure = true)
    public float normalY(int vertexIndex) {
        return normalComponent(vertexIndex, 1);
    }

    /**
     * {@return the z-component of a vertex's normal or NaN if the normal is undefined}
     */
    @Contract(pure = true)
    public float normalZ(int vertexIndex) {
        return normalComponent(vertexIndex, 2);
    }

    /**
     * {@return a component of a vertex's normal or NaN if the normal is undefined}
     */
    @Contract(pure = true)
    public float normalComponent(int vertexIndex, int componentIndex) {
        var packedNormal = normals[vertexIndex];
        if (BakedNormals.isUnspecified(packedNormal)) {
            return Float.NaN;
        } else {
            return BakedNormals.unpackComponent(packedNormal, componentIndex);
        }
    }

    /**
     * {@return a vertex normal in packed form}
     */
    @Contract(pure = true)
    public int packedNormal(int vertexIndex) {
        return normals[vertexIndex];
    }

    /**
     * Same as {@link #copyNormal(int, Vector3f)}, but constructs a new destination vector automatically.
     */
    @Contract(pure = true)
    public Vector3f copyNormal(int vertexIndex) {
        return copyNormal(vertexIndex, new Vector3f());
    }

    /**
     * Copies the normal vector of a vertex into a given vector and returns it.
     */
    public Vector3f copyNormal(int vertexIndex, Vector3f dest) {
        return BakedNormals.unpack(normals[vertexIndex], dest);
    }

    /**
     * Sets the normal vector of a vertex.
     */
    public MutableQuad setNormal(int vertexIndex, float x, float y, float z) {
        normals[vertexIndex] = BakedNormals.pack(x, y, z);
        return this;
    }

    /**
     * Sets the normal vector of a vertex.
     */
    public MutableQuad setNormal(int vertexIndex, Vector3fc normal) {
        normals[vertexIndex] = BakedNormals.pack(normal);
        return this;
    }

    /**
     * Sets a component of a vertex's normal.
     */
    public MutableQuad setNormalComponent(int vertexIndex, int componentIndex, float value) {
        int normal = normals[vertexIndex];
        float x, y, z;
        if (BakedNormals.isUnspecified(normal)) {
            x = 0;
            y = 0;
            z = 0;
        } else {
            x = BakedNormals.unpackX(normal);
            y = BakedNormals.unpackY(normal);
            z = BakedNormals.unpackZ(normal);
        }

        switch (componentIndex) {
            case 0 -> x = value;
            case 1 -> y = value;
            case 2 -> z = value;
            default -> throw new IllegalArgumentException();
        }

        normals[vertexIndex] = BakedNormals.pack(x, y, z);
        return this;
    }

    /**
     * Sets the normal vector of a vertex from its packed representation.
     */
    public MutableQuad setPackedNormal(int vertexIndex, int packedNormal) {
        normals[vertexIndex] = packedNormal;
        return this;
    }

    /**
     * Sets the normal vector of all vertices to the normal vectors encoded in the given baked normals object.
     */
    public MutableQuad setNormal(BakedNormals bakedNormals) {
        normals[0] = bakedNormals.normal(0);
        normals[1] = bakedNormals.normal(1);
        normals[2] = bakedNormals.normal(2);
        normals[3] = bakedNormals.normal(3);
        return this;
    }

    /**
     * Recomputes the quad normal from the vertex positions.
     * <p>Assumes that the quad is planar.
     */
    public MutableQuad recomputeNormals(boolean updateDirection) {
        int normal = BakedNormals.computeQuadNormal(positions[0], positions[1], positions[2], positions[3]);
        Arrays.fill(normals, normal);
        if (updateDirection) {
            float nX = BakedNormals.unpackX(normal);
            float nY = BakedNormals.unpackY(normal);
            float nZ = BakedNormals.unpackZ(normal);
            setDirection(Direction.getApproximateNearest(nX, nY, nZ));
        }
        return this;
    }

    /**
     * {@return the color of a given vertex in ARGB form}
     */
    @Contract(pure = true)
    public int color(int vertexIndex) {
        return colors[vertexIndex];
    }

    /**
     * Sets the color of all vertices to a packed ARGB color.
     */
    public MutableQuad setColor(int packedColor) {
        colors[0] = packedColor;
        colors[1] = packedColor;
        colors[2] = packedColor;
        colors[3] = packedColor;
        return this;
    }

    /**
     * Sets the color of a vertex to a packed ARGB color.
     */
    public MutableQuad setColor(int vertexIndex, int packedColor) {
        colors[vertexIndex] = packedColor;
        return this;
    }

    /**
     * Sets the color of a vertex from integer components (0-255).
     */
    public MutableQuad setColor(int vertexIndex, int r, int g, int b, int a) {
        return setColor(vertexIndex, ARGB.color(a, r, g, b));
    }

    /**
     * Sets the color of all vertices to a packed ARGB color.
     */
    @Contract(pure = true)
    public boolean hasAmbientOcclusion() {
        return ambientOcclusion;
    }

    public MutableQuad setAmbientOcclusion(boolean ambientOcclusion) {
        this.ambientOcclusion = ambientOcclusion;
        return this;
    }

    public MutableQuad setFrom(BakedQuad quad) {
        lastSourceQuad = quad;
        for (int i = 0; i < 4; i++) {
            positions[i].set(quad.position(i));
            uvs[i] = quad.packedUV(i);
            // SHIM: vanilla BakedQuad carries neither normals nor colors.
            normals[i] = 0;
            colors[i] = 0xFFFFFFFF;
        }
        direction = quad.direction();
        BakedQuad.MaterialInfo materialInfo = quad.materialInfo();
        sprite = materialInfo.sprite();
        chunkLayer = materialInfo.layer();
        itemRenderType = materialInfo.itemRenderType();
        itemGlintRenderType = materialInfo.itemGlintRenderType();
        itemGlintSpecialRenderType = materialInfo.itemGlintSpecialRenderType();
        tintIndex = materialInfo.tintIndex();
        shadeOverride = materialInfo.shadeDirectionOverride();
        lightEmission = materialInfo.lightEmission();
        // SHIM: vanilla MaterialInfo has no ambient occlusion flag, NeoForge's default is "true".
        ambientOcclusion = true;
        return this;
    }

    /**
     * Assumes that the UV coordinates are in sprite-space and transforms them to atlas-space.
     */
    private void transformUvsFromSpriteToAtlas() {
        TextureAtlasSprite sprite = requiredSprite();
        for (int i = 0; i < 4; i++) {
            long packedUv = packedUv(i);
            setUv(i, sprite.getU(UVPair.unpackU(packedUv)), sprite.getV(UVPair.unpackV(packedUv)));
        }
    }

    /**
     * Assumes that the UV coordinates are in atlas-space and transforms them to sprite-space.
     */
    private void transformUvsFromAtlasToSprite() {
        TextureAtlasSprite sprite = requiredSprite();
        float uOrigin = sprite.getU0();
        float vOrigin = sprite.getV0();
        float uWidth = sprite.getU1() - uOrigin;
        float vWidth = sprite.getV1() - vOrigin;

        for (int i = 0; i < 4; i++) {
            long packedUv = packedUv(i);
            float u = (UVPair.unpackU(packedUv) - uOrigin) / uWidth;
            float v = (UVPair.unpackV(packedUv) - vOrigin) / vWidth;
            setUv(i, u, v);
        }
    }

    @Contract(pure = true)
    public BakedQuad toBakedQuad() {
        TextureAtlasSprite sprite = requiredSprite();
        ChunkSectionLayer chunkLayer = requiredChunkLayer();
        RenderType itemRenderType = requiredItemRenderType();
        RenderType itemGlintRenderType = requiredItemGlintRenderType();
        RenderType itemGlintSpecialRenderType = requiredItemGlintSpecialRenderType();

        // Try to reuse objects from the last baked quad that we copied from to reduce allocations if
        // the quad was only partially transformed.
        Vector3fc pos0;
        Vector3fc pos1;
        Vector3fc pos2;
        Vector3fc pos3;
        BakedQuad.MaterialInfo materialInfo;
        if (lastSourceQuad != null) {
            pos0 = reuseVector(lastSourceQuad, positions[0]);
            pos1 = reuseVector(lastSourceQuad, positions[1]);
            pos2 = reuseVector(lastSourceQuad, positions[2]);
            pos3 = reuseVector(lastSourceQuad, positions[3]);

            BakedQuad.MaterialInfo srcInfo = lastSourceQuad.materialInfo();
            boolean canReuseMaterialInfo = sprite == srcInfo.sprite() &&
                    chunkLayer == srcInfo.layer() &&
                    itemRenderType == srcInfo.itemRenderType() &&
                    itemGlintRenderType == srcInfo.itemGlintRenderType() &&
                    itemGlintSpecialRenderType == srcInfo.itemGlintSpecialRenderType() &&
                    tintIndex == srcInfo.tintIndex() &&
                    shadeOverride == srcInfo.shadeDirectionOverride() &&
                    lightEmission == srcInfo.lightEmission();
            if (canReuseMaterialInfo) {
                materialInfo = srcInfo;
            } else {
                materialInfo = newMaterialInfo(sprite, chunkLayer, itemRenderType, itemGlintRenderType,
                        itemGlintSpecialRenderType);
            }
        } else {
            pos0 = new Vector3f(positions[0]);
            pos1 = new Vector3f(positions[1]);
            pos2 = new Vector3f(positions[2]);
            pos3 = new Vector3f(positions[3]);
            materialInfo = newMaterialInfo(sprite, chunkLayer, itemRenderType, itemGlintRenderType,
                    itemGlintSpecialRenderType);
        }

        // SHIM: normals, colors and ambient occlusion are lost here; vanilla BakedQuad has no slots for them.
        // NeoForge's BakedQuad record extension cannot be reproduced without patching Minecraft itself.
        return new BakedQuad(
                pos0,
                pos1,
                pos2,
                pos3,
                uvs[0],
                uvs[1],
                uvs[2],
                uvs[3],
                direction,
                materialInfo);
    }

    private BakedQuad.MaterialInfo newMaterialInfo(TextureAtlasSprite sprite,
            ChunkSectionLayer chunkLayer,
            RenderType itemRenderType,
            RenderType itemGlintRenderType,
            RenderType itemGlintSpecialRenderType) {
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

    /**
     * Tries to reuse the position vectors of the last quad we sourced any data from.
     */
    private static Vector3fc reuseVector(BakedQuad quad, Vector3f position) {
        for (int i = 0; i < 4; i++) {
            if (quad.position(i).equals(position)) {
                return quad.position(i);
            }
        }
        return new Vector3f(position);
    }

    /**
     * Applies the given matrix to this quads position and normals (if specified).
     * <p>Note that the {@linkplain #direction()} is not transformed.
     */
    public MutableQuad transform(Matrix4f rotation) {
        Vector3f tmp = null;
        for (int i = 0; i < 4; i++) {
            rotation.transformPosition(positions[i]);
            var normal = normals[i];
            if (!BakedNormals.isUnspecified(normal)) {
                tmp = BakedNormals.unpack(normal, tmp);
                rotation.transformDirection(tmp);
                normals[i] = BakedNormals.pack(tmp);
            }
        }

        return this;
    }

    /**
     * Recalculates the order of vertices to conform to the order expected by the Vanilla AO algorithm.
     *
     * <p>It uses the current {@linkplain #direction() direction} and <strong>only works correctly for axis-aligned quads</strong>.
     *
     * <p>SHIM: {@code FaceBakery#recalculateWinding} does not exist in unpatched MC 26.3 (it is a NeoForge addition),
     * so this is intentionally a no-op. Nothing in AE2 calls it; if that changes, the winding logic has to be
     * reimplemented inside the shim.
     */
    public MutableQuad recalculateWinding() {
        return this;
    }

    /**
     * {@return a copy of this mutable quad}
     */
    public MutableQuad copy() {
        return copyInto(new MutableQuad());
    }

    /**
     * Copies the contents of this mutable quad into the provided mutable quad and returns it.
     */
    public MutableQuad copyInto(MutableQuad dest) {
        for (int i = 0; i < 4; i++) {
            dest.positions[i].set(positions[i]);
        }
        System.arraycopy(uvs, 0, dest.uvs, 0, uvs.length);
        System.arraycopy(normals, 0, dest.normals, 0, normals.length);
        System.arraycopy(colors, 0, dest.colors, 0, colors.length);
        dest.direction = direction;
        dest.sprite = sprite;
        dest.chunkLayer = chunkLayer;
        dest.itemRenderType = itemRenderType;
        dest.itemGlintRenderType = itemGlintRenderType;
        dest.itemGlintSpecialRenderType = itemGlintSpecialRenderType;
        dest.tintIndex = tintIndex;
        dest.shadeOverride = shadeOverride;
        dest.lightEmission = lightEmission;
        dest.ambientOcclusion = ambientOcclusion;
        dest.lastSourceQuad = lastSourceQuad;
        return dest;
    }

    public MutableQuad reset() {
        for (int i = 0; i < 4; i++) {
            positions[i].set(0, 0, 0);
        }
        Arrays.fill(uvs, 0L);
        Arrays.fill(normals, 0);
        Arrays.fill(colors, 0xFFFFFFFF);
        direction = Direction.DOWN;
        sprite = null;
        chunkLayer = null;
        itemRenderType = null;
        itemGlintRenderType = null;
        itemGlintSpecialRenderType = null;
        tintIndex = -1;
        shadeOverride = null;
        lightEmission = 0;
        ambientOcclusion = false;
        lastSourceQuad = null;

        return this;
    }

    private static Transparency getTransparency(Material.Baked material) {
        var spriteTransparency = material.sprite().contents().transparency();
        if (material.forceTranslucent()) {
            return Transparency.TRANSLUCENT;
        }
        return spriteTransparency;
    }
}
