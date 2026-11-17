/*
 * Часть nfcompat. NeoForge-патч `RenderPipeline.toBuilder()` для Fabric.
 *
 * В ванили `RenderPipeline` неизменяемый, и Builder'а из него не получить, а GuideME
 * (сборка под NeoForge) использует `toBuilder()` в пяти местах: Blitter,
 * TextureDownloader, InWorldAnnotationRenderer, Styleable, TextStyle. Без метода клиент
 * падает на старте с NoSuchMethodError.
 *
 * Реализация — копирование состояния в новый Builder. Что скопировать нельзя
 * (ShaderDefines: у Builder'а нет API для произвольных defines), то теряется; для
 * пайплайнов GuideME defines пустые, на практике это не влияет.
 *
 * Метод добавлен как обычный instance-метод: Mixin такое разрешает (в отличие от
 * non-private static, который в чужой класс добавить нельзя). @Unique тут не годится:
 * он переименовывает член, а GuideME зовёт `toBuilder()` по имени.
 */
package nfcompat.mixin.guideme;

import java.util.Map;
import java.util.List;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.vertex.VertexFormat;

import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderPipeline.class)
public abstract class RenderPipelineToBuilderMixin {

    @Shadow
    public abstract Identifier getLocation();

    @Shadow
    public abstract Map<ShaderType, Identifier> getShaders();

    @Shadow
    public abstract List<BindGroupLayout> getBindGroupLayouts();

    @Shadow
    public abstract DepthStencilState getDepthStencilState();

    @Shadow
    public abstract PolygonMode getPolygonMode();

    @Shadow
    public abstract boolean isCull();

    @Shadow
    public abstract List<ColorTargetState> getColorTargetStates();

    @Shadow
    public abstract List<VertexFormat> getVertexFormatBindings();

    @Shadow
    public abstract PrimitiveTopology getPrimitiveTopology();

    @Shadow
    public abstract net.minecraft.client.renderer.ShaderDefines getShaderDefines();

    @Shadow
    public abstract int pushConstantSize();

    public RenderPipeline.Builder toBuilder() {
        var builder = RenderPipelineBuilderInvoker.nfcompat$newBuilder();
        builder.withLocation(getLocation());

        // Shader defines: в Builder есть только withShaderDefine(String) для флагов и
        // (String,int/float) для значений. Мы переносим флаги (PER_FACE_LIGHTING, EMISSIVE и
        // т.п.) — без них копия пайплайна выбирает другой вариант шейдера. String-valued
        // defines перенести нечем (нет перегрузки String,String) — их у GUI-пайплайнов нет.
        var defines = getShaderDefines();
        if (defines != null && !defines.isEmpty()) {
            for (var flag : defines.flags()) {
                builder.withShaderDefine(flag);
            }
        }

        var vertexShader = getShaders().get(ShaderType.VERTEX);
        if (vertexShader != null) {
            builder.withVertexShader(vertexShader);
        }
        var fragmentShader = getShaders().get(ShaderType.FRAGMENT);
        if (fragmentShader != null) {
            builder.withFragmentShader(fragmentShader);
        }
        for (var layout : getBindGroupLayouts()) {
            builder.withBindGroupLayout(layout);
        }
        if (getDepthStencilState() != null) {
            builder.withDepthStencilState(getDepthStencilState());
        }
        if (getPolygonMode() != null) {
            builder.withPolygonMode(getPolygonMode());
        }
        builder.withCull(isCull());
        for (var target : getColorTargetStates()) {
            builder.withColorTargetState(target);
        }
        var formats = getVertexFormatBindings();
        for (int i = 0; i < formats.size(); i++) {
            builder.withVertexBinding(i, formats.get(i));
        }
        if (getPrimitiveTopology() != null) {
            builder.withPrimitiveTopology(getPrimitiveTopology());
        }
        builder.withPushConstantSize(pushConstantSize());
        return builder;
    }
}
