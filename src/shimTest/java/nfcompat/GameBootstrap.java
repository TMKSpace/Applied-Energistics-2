package nfcompat;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Единственная точка бутстрапа MC-регистров для тестов.
 * <p>
 * ПОЧЕМУ ТАК: часть шима (FluidStack/FluidResource) в статике лезет в
 * {@code BuiltInRegistries}, а он в 26.3 требует вызова {@code Bootstrap.bootStrap()}
 * (внутри {@code checkBootstrapCalled}). Без бутстрапа {@code BuiltInRegistries}
 * бросает ExceptionInInitializerError, после чего в этом JVM класс уже навсегда
 * сломан (NoClassDefFoundError), поэтому:
 * <ul>
 * <li>бутстрап делается РОВНО один раз и до первого касания регистров;</li>
 * <li>{@code Bootstrap.bootStrap()} не идемпотентен (второй вызов бросает),
 * поэтому флаг обязателен;</li>
 * <li>тесты, которым нужны только {@code Registries} (например Tags), бутстрап
 * не зовут: {@code ResourceKey}/{@code TagKey} от него не зависят.</li>
 * </ul>
 * Игровой клиент/сервер при этом не поднимается: ни Level, ни окна, ни ассетов.
 */
public final class GameBootstrap {

    private static boolean done;

    private GameBootstrap() {
    }

    public static synchronized void ensure() {
        if (done) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        done = true;
    }
}
