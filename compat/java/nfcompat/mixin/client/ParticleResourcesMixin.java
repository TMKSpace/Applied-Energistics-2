/*
 * Часть nfcompat. Хук регистрации провайдеров частиц.
 *
 * NeoForge подменяет `ParticleResources#register` на рассылку
 * `RegisterParticleProvidersEvent`. Нам подменять нечего, поэтому постим событие
 * в конце конструктора (ванильные провайдеры уже на месте) и применяем
 * результат: `ParticleResources` создаётся заново на каждый мир, спрайт-сеты
 * должны быть свежими, поэтому регистрируем через его же методы.
 */
package nfcompat.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.particle.ParticleResources;

@Mixin(ParticleResources.class)
public class ParticleResourcesMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nfcompat$registerParticleProviders(CallbackInfo ci) {
        nfcompat.ClientEventBridge.applyParticleProviders((ParticleResources) (Object) this);
    }
}
