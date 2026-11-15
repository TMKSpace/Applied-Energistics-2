/*
 * Часть nfcompat. Автоматическая подписка класса на шину.
 *
 * На Fabric этого механизма нет: подписка идёт через fabric.mod.json и явные вызовы.
 * Заглушка нужна для компиляции.
 */
package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.neoforged.api.distmarker.Dist;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventBusSubscriber {
    String modid() default "";

    Dist[] value() default {};

    Bus bus() default Bus.GAME;

    enum Bus {
        GAME,
        MOD
    }
}
