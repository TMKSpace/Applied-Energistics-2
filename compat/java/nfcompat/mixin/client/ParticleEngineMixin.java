/*
 * Часть nfcompat. Мост `RegisterParticleGroupsEvent`.
 *
 * NeoForge патчит `ParticleEngine` в двух местах:
 *  1) `createParticleGroup` — фабрика группы берётся из карты события, а не из
 *     захардкоженного switch'а (иначе кастомный `ParticleRenderType` AE2 получает
 *     ванильный `QuadParticleGroup` и молния рисуется мусором);
 *  2) порядок отрисовки `RENDER_ORDER` — без него `extract` вообще не перебирает
 *     неизвестный тип, и частицы пропадают.
 *
 * Здесь то же самое: подмена фабрики на HEAD `createParticleGroup` и подстановка
 * расширенного списка вместо ванильного `RENDER_ORDER` в `extract`.
 */
package nfcompat.mixin.client;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.objectweb.asm.Opcodes;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;

import nfcompat.client.ParticleGroups;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Shadow
    @Final
    private static List<ParticleRenderType> RENDER_ORDER;

    @Unique
    private static List<ParticleRenderType> nfcompat$renderOrder;

    @Inject(method = "createParticleGroup", at = @At("HEAD"), cancellable = true)
    private void nfcompat$createParticleGroup(ParticleRenderType type,
            CallbackInfoReturnable<ParticleGroup<?>> cir) {
        var factory = ParticleGroups.factory(type);
        if (factory != null) {
            cir.setReturnValue(factory.apply((ParticleEngine) (Object) this));
        }
    }

    @Redirect(method = "extract", at = @At(value = "FIELD", opcode = Opcodes.GETSTATIC, target = "Lnet/minecraft/client/particle/ParticleEngine;RENDER_ORDER:Ljava/util/List;"))
    private static List<ParticleRenderType> nfcompat$renderOrder() {
        var cached = nfcompat$renderOrder;
        if (cached != null) {
            return cached;
        }

        var extra = ParticleGroups.extraRenderOrder();
        if (extra.isEmpty()) {
            cached = RENDER_ORDER;
        } else {
            var all = new ArrayList<ParticleRenderType>(RENDER_ORDER.size() + extra.size());
            all.addAll(RENDER_ORDER);
            for (var type : extra) {
                if (!all.contains(type)) {
                    all.add(type);
                }
            }
            cached = all;
        }

        nfcompat$renderOrder = cached;
        return cached;
    }
}
