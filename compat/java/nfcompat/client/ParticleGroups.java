/*
 * Часть nfcompat. Группы частиц (`RegisterParticleGroupsEvent`).
 *
 * NeoForge патчит `ParticleEngine`: у групп частиц (`ParticleGroup`) есть фабрика на
 * каждый `ParticleRenderType`, и моды могут подсунуть свою (у AE2 такая одна — молния
 * `LightningFXGroup`, ей нужен собственный рендер, ванильный `QuadParticleGroup` её не
 * умеет). Кроме того, тип группы нужно доложить в порядок отрисовки
 * (`ParticleEngine.RENDER_ORDER` — приватный static final список), иначе
 * `ParticleEngine#extract` её просто не перебирает и молнии не рисуются.
 *
 * На Fabric ничего этого нет, поэтому карту фабрик держим здесь, а применяем в
 * `nfcompat.mixin.client.ParticleEngineMixin`.
 */
package nfcompat.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleGroup;
import net.minecraft.client.particle.ParticleRenderType;
import net.neoforged.neoforge.client.event.RegisterParticleGroupsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ParticleGroups {

    private static final Map<ParticleRenderType, Function<ParticleEngine, ParticleGroup<?>>> FACTORIES = new LinkedHashMap<>();

    /** Типы, которые надо доложить в конец ванильного порядка отрисовки. */
    private static final List<ParticleRenderType> EXTRA_RENDER_ORDER = new ArrayList<>();

    private static boolean posted;

    private ParticleGroups() {
    }

    /**
     * Рассылает событие. Идемпотентно: `ParticleEngine` создаётся на каждый мир, а
     * регистрация в AE2 разрешена только один раз (повторная бросит исключение).
     */
    public static void install() {
        if (posted) {
            return;
        }
        posted = true;
        NeoForge.EVENT_BUS.post(new RegisterParticleGroupsEvent(FACTORIES, EXTRA_RENDER_ORDER));
        if (!EXTRA_RENDER_ORDER.isEmpty()) {
            System.out.println("[nfcompat] группы частиц: " + EXTRA_RENDER_ORDER);
        }
    }

    /** {@return фабрика группы для типа, либо null, если тип ванильный} */
    public static Function<ParticleEngine, ParticleGroup<?>> factory(ParticleRenderType type) {
        return FACTORIES.get(type);
    }

    /** {@return дополнительные типы частиц в порядке регистрации} */
    public static List<ParticleRenderType> extraRenderOrder() {
        return Collections.unmodifiableList(EXTRA_RENDER_ORDER);
    }
}
