package nfcompat.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.SimpleEventBus;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Известные баги шимы событий, найденные тестами. Тесты отключены не потому, что
 * они "не важны", а потому что фиксируют поведение, которое надо ПОЧИНИТЬ: пока
 * баг есть, они падают, и это правильно.
 * <p>
 * Причина всех трёх — {@code SimpleEventBus.eventTypeOf()}: он читает первый
 * параметр {@code SerializedLambda.getImplMethodSignature()}. Это неверно, потому
 * что javac дописывает захваченные переменные (и {@code this}) ПЕРЕД параметром
 * SAM-метода. Правильный источник типа — {@code getInstantiatedMethodType()}
 * (это дескриптор SAM с подставленными type-аргументами, он всегда
 * {@code (L<event>;)V}).
 */
class SimpleEventBusKnownBugsTest {

    static class BaseEvent extends Event {
    }

    private SimpleEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new SimpleEventBus();
    }

    @Test
    @DisplayName("лямбда, захватившая локальную переменную, должна получать событие")
    void capturingLambdaReceivesEvent() {
        List<BaseEvent> seen = new ArrayList<>();
        bus.addListener((BaseEvent event) -> seen.add(event));

        var event = new BaseEvent();
        bus.post(event);

        assertThat(seen).containsExactly(event);
    }

    @Test
    @DisplayName("лямбда внутри метода, читающая поле объекта, должна получать событие")
    void thisCapturingLambdaReceivesEvent() {
        var holder = new HoldingSubscriber();
        bus.addListener((BaseEvent event) -> holder.seen.add(event));

        var event = new BaseEvent();
        bus.post(event);

        assertThat(holder.seen).containsExactly(event);
    }

    static class HoldingSubscriber {
        final List<BaseEvent> seen = new ArrayList<>();
    }

    @Test
    @DisplayName("ссылка на метод экземпляра должна получать событие")
    void boundMethodReferenceReceivesEvent() {
        List<BaseEvent> seen = new ArrayList<>();
        EventListener<BaseEvent> listener = seen::add;
        bus.addListener(listener);

        var event = new BaseEvent();
        bus.post(event);

        assertThat(seen).containsExactly(event);
    }

    @Test
    @DisplayName("@SubscribeEvent наследуется от родителя")
    void inheritedSubscribeEventWorks() {
        var subscriber = new ChildSubscriber();
        bus.register(subscriber);

        bus.post(new BaseEvent());

        assertThat(subscriber.calls).isEqualTo(1);
    }

    static class ParentSubscriber {
        int calls;

        @SubscribeEvent
        void onBase(BaseEvent event) {
            calls++;
        }
    }

    static class ChildSubscriber extends ParentSubscriber {
    }
}
