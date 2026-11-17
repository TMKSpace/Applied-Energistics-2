/*
 * Часть nfcompat (GuideME). `SimpleJsonResourceReloadListener.scanDirectory` для Fabric.
 *
 * GuideME зовёт неофорджевскую перегрузку (ResourceManager, FileToIdConverter, DynamicOps,
 * Codec, Map) — в ванили 26.3 такого метода нет вообще, поэтому загрузка гайдов падала
 * `NoSuchMethodError`, обрывала весь перезагруз ресурспаков и уводила клиент в recovery-экран.
 *
 * Реализация — `GuideMEReloadCompat.scanDirectory` (обход json'ов + `Codec#parse` в переданную
 * карту). `require = 0`: если GuideME переедет на другой вызов, миксин промолчит.
 */
package nfcompat.mixin.guideme;

import java.util.Map;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import nfcompat.compat.guideme.GuideMEReloadCompat;

@Mixin(targets = "guideme.internal.GuideReloadListener")
public class GuideMEReloadMixin {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Redirect(method = "loadDataDrivenGuides", require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/SimpleJsonResourceReloadListener;scanDirectory"
                            + "(Lnet/minecraft/server/packs/resources/ResourceManager;"
                            + "Lnet/minecraft/resources/FileToIdConverter;"
                            + "Lcom/mojang/serialization/DynamicOps;"
                            + "Lcom/mojang/serialization/Codec;"
                            + "Ljava/util/Map;)Ljava/util/Map;"))
    private static Map<Identifier, ?> nfcompat$scanDirectory(ResourceManager resourceManager,
            FileToIdConverter lister,
            DynamicOps<JsonElement> ops,
            Codec<?> codec,
            Map<Identifier, ?> into) {
        return GuideMEReloadCompat.scanDirectory(resourceManager, lister, ops, (Codec) codec,
                (Map) into);
    }
}
