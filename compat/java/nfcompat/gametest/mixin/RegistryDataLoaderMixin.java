/*
 * Часть nfcompat. Единственная неофорджевская патч-точка, нужная реестровому геймтесту.
 *
 * NeoForge патчит `RegistryDataLoader.load(...)`: после чтения элементов реестров и до
 * их заморозки постит `RegisterGameTestsEvent` (только если
 * `GameTestHooks.isGametestEnabled()`). В ванили 26.3 та же точка — синтетический
 * `lambda$load$2(List<RegistryLoadTask>, Map<ResourceKey, Exception>, Void)`, который
 * замораживает реестры и собирает `RegistryAccess.Frozen`.
 *
 * Почему именно лямбда: список задач (`RegistryLoadTask`) — локальная переменная
 * приватного `load(LoaderFactory, ...)`, а в лямбду он передаётся аргументом, поэтому
 * @Inject на её HEAD даёт доступ к письменным реестрам без capture-костылей.
 * Ровно так же поступает `fabric-gametest-api-v1` (`RegistryDataLoaderMixin`), так что
 * имя синтетика уже проверено живым модом на этой же версии MC.
 *
 * ВАЖНО: имя `lambda$load$2` зависит от порядка лямбд в исходнике MC. При обновлении
 * MC это первое, что надо перепроверить (javap по RegistryDataLoader).
 */
package nfcompat.gametest.mixin;

import java.util.List;
import java.util.Map;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.ResourceKey;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import nfcompat.gametest.GameTestRegistration;

@Mixin(RegistryDataLoader.class)
public class RegistryDataLoaderMixin {

    @Inject(
            method = "lambda$load$2(Ljava/util/List;Ljava/util/Map;Ljava/lang/Void;)Lnet/minecraft/core/RegistryAccess$Frozen;",
            at = @At("HEAD"))
    private static void nfcompat$registerGameTests(List<RegistryLoadTask<?>> loadTasks,
            Map<ResourceKey<?>, Exception> loadingErrors,
            Void ignored,
            CallbackInfoReturnable<RegistryAccess.Frozen> cir) {
        GameTestRegistration.registerIfEnabled(loadTasks);
    }
}
