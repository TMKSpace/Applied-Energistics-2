/*
 * Часть nfcompat. Автоматическая подписка класса на шину.
 *
 * На NeoForge эту аннотацию читает загрузчик (AutomaticEventSubscriber): находит
 * классы через ASM-скан мод-файлов, грузит их и подписывает static-методы с
 * @SubscribeEvent. На Fabric у нас то же самое делает nfcompat.event.AutoEventSubscriber.
 *
 * Набор атрибутов — как в NeoForge 26.x (`value`, `modid`), плюс `bus` из старых
 * версий: аддоны, собранные против 1.20.x, могли его писать. Для наших классов
 * значение по умолчанию (GAME) означает "как в NeoForge": тип шины выбирается по
 * событию (IModBusEvent -> мод-шина).
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
    /** Стороны, на которых подписывать класс. По умолчанию — обе, как в NeoForge. */
    Dist[] value() default { Dist.CLIENT, Dist.DEDICATED_SERVER };

    String modid() default "";

    Bus bus() default Bus.GAME;

    enum Bus {
        GAME,
        MOD
    }
}
