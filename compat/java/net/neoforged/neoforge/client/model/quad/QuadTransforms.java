/*
 * Vendored from NeoForge (LGPL-2.1-only), adapted for the Fabric shim.
 * Upstream: net/neoforged/neoforge/client/model/quad/QuadTransforms.java
 */

package net.neoforged.neoforge.client.model.quad;

import com.mojang.math.Transformation;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class QuadTransforms {
    /**
     * Reused by {@link #isIdentity(Transformation)}; never mutated.
     */
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();

    private QuadTransforms() {}

    /**
     * Returns a baked quad with the passed transformation applied.
     * <p>
     * Shim note: MC 26.3 {@code Transformation} has no {@code isIdentity()}, so identity is detected by comparing
     * the matrix itself. Vanilla {@code BakedQuad} also carries neither normals nor colors, so only positions are
     * transformed here; the upstream normal transformation is impossible without the NeoForge-patched quad record.
     */
    public static BakedQuad applyTransformation(BakedQuad quad, Transformation transformation) {
        if (isIdentity(transformation)) {
            return quad;
        }
        var matrix = transformation.getMatrix();
        var posTemp = new Vector4f();
        return new BakedQuad(
                transformPosition(matrix, posTemp, quad.position0()),
                transformPosition(matrix, posTemp, quad.position1()),
                transformPosition(matrix, posTemp, quad.position2()),
                transformPosition(matrix, posTemp, quad.position3()),
                quad.packedUV0(),
                quad.packedUV1(),
                quad.packedUV2(),
                quad.packedUV3(),
                // TODO: the direction is currently not being transformed, but probably should be
                quad.direction(),
                quad.materialInfo());
    }

    private static boolean isIdentity(Transformation transformation) {
        return transformation == Transformation.IDENTITY
                || transformation.equals(Transformation.IDENTITY)
                || transformation.getMatrix().equals(IDENTITY_MATRIX);
    }

    private static Vector3fc transformPosition(org.joml.Matrix4fc matrix, Vector4f temp, Vector3fc pos) {
        temp.set(pos.x(), pos.y(), pos.z(), 1);
        // JOML 1.10 has no Matrix4fc#transformPosition(Vector4f), the 4-component variant is Matrix4fc#transform
        matrix.transform(temp);
        temp.div(temp.w);
        return new Vector3f(temp.x(), temp.y(), temp.z());
    }
}
