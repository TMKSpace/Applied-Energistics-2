/*
 * Часть nfcompat. Аннотация точки входа мода.
 *
 * На Fabric точки входа берутся из fabric.mod.json, поэтому аннотация нужна для
 * компиляции исходников AE2. Но её значение загрузчик всё-таки читает: если на том же
 * классе висит `@EventBusSubscriber` без `modid`, то мод для подписки берётся отсюда
 * (nfcompat.event.AutoEventSubscriber, как в NeoForge).
 */
package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import net.neoforged.api.distmarker.Dist;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();

    Dist dist() default Dist.CLIENT;
}
