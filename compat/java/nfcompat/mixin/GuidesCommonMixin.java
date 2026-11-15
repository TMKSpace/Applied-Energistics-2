/*
 * Часть nfcompat. Заглушка открытия гайда GuideME.
 *
 * GuideME на NeoForge — отдельный мод, загрузчик создаёт его сам: регистрирует
 * GuideItem, ArgumentTypeInfo и клиентский прокси. На Fabric его «модовые» классы
 * никто не создаёт, а его статическая инициализация падает — она регистрирует
 * предмет через ArgumentTypeInfos.registerByClass, а это ПАТЧ NeoForge к ванили
 * (Mixin не позволяет добавить туда public static метод).
 *
 * Пока GuideME не поднят, любое открытие гайда (кнопка «?» в интерфейсах AE2)
 * валило клиент. Поэтому здесь просто гасим вызов: интерфейс живёт, гайд не открывается.
 */
package nfcompat.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "guideme.GuidesCommon")
public class GuidesCommonMixin {

    private static boolean nfcompat$warned;

    @Inject(method = "openGuide(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void nfcompat$skipOpenGuide(Player player, Identifier guideId, CallbackInfo ci) {
        warnOnce();
        ci.cancel();
    }

    @Inject(method = "openGuide(Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/resources/Identifier;Lguideme/PageAnchor;)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void nfcompat$skipOpenGuide(Player player, Identifier guideId, Object anchor, CallbackInfo ci) {
        warnOnce();
        ci.cancel();
    }

    private static void warnOnce() {
        if (!nfcompat$warned) {
            nfcompat$warned = true;
            System.out.println("[nfcompat] GuideME не инициализирован на Fabric — открытие гайда пропущено");
        }
    }
}
