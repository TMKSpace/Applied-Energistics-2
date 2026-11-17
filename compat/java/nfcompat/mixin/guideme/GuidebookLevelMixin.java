/*
 * Часть nfcompat (GuideME). `Entity#onAddedToLevel()` — неофорджевский хук.
 *
 * `GuidebookLevel.addEntity` (сцена гайда) после вставки сущности зовёт
 * `entity.onAddedToLevel()`. NeoForge добавляет этот хук в `Entity`, в ванили его нет вовсе
 * (ни `onAddedToLevel`, ни похожего). Из `compat/java` метод не добавить (это instance-метод
 * чужого класса — можно только миксином), а нам нужно ровно одно: чтобы сцена не падала.
 *
 * Поэтому глотаем вызов в `GuidebookLevel.addEntity`: в сцене гайда сущности декоративные,
 * у AE2 (и в самой GuideME) на этот хук никто не подписан.
 */
package nfcompat.mixin.guideme;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "guideme.scene.level.GuidebookLevel")
public class GuidebookLevelMixin {

    @Redirect(method = "addEntity", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;onAddedToLevel()V"))
    private static void nfcompat$onAddedToLevel(Entity entity) {
        // no-op: см. шапку файла
    }
}
