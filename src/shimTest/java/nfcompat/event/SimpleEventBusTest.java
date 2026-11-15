package nfcompat.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SimpleEventBus;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Шина событий шима: тип события вытаскивается из SerializedLambda слушателя,
 * поэтому ломается она молча (слушатель "зарегистрирован", но не вызывается).
 * <p>
 * ВАЖНО про стиль тестов: слушатели-лямбды здесь НЕ захватывают локальные переменные
 * и пишут в статические поля. Это не случайность: шим берёт тип события из ПЕРВОГО
 * параметра impl-метода лямбды, а javac добавляет захваченные переменные (и this)
 * первыми параметрами. Из-за этого любой лямбда-слушатель с захватом сегодня не
 * получает события — см. {@link SimpleEventBusKnownBugsTest}.
 */
class SimpleEventBusTest {

    static final List<String> SEEN = new ArrayList<>();
    static final List<String> SEEN_CHILD = new ArrayList<>();
    static final List<String> ORDER = new ArrayList<>();

    private SimpleEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new SimpleEventBus();
        SEEN.clear();
        SEEN_CHILD.clear();
        ORDER.clear();
    }

    static class BaseEvent extends Event {
    }

    static class ChildEvent extends BaseEvent {
    }

    /** Событие, не связанное с BaseEvent — для проверки изоляции. */
    static class OtherEvent extends Event {
    }

    /** Слушатель-статика: используется как ссылка на метод. */
    static void recordBase(BaseEvent event) {
        SEEN.add("base");
    }

    @Test
    @DisplayName("лямбда без захвата: тип события вытаскивается из SerializedLambda")
    void nonCapturingLambda() {
        bus.addListener((BaseEvent event) -> SEEN.add("base"));

        bus.post(new BaseEvent());

        assertThat(SEEN).containsExactly("base");
    }

    @Test
    @DisplayName("ссылка на статический метод с параметром-событием")
    void staticMethodReference() {
        EventListener<BaseEvent> listener = SimpleEventBusTest::recordBase;
        bus.addListener(listener);

        bus.post(new BaseEvent());

        assertThat(SEEN).containsExactly("base");
    }

    @Test
    @DisplayName("подписчик на базовый класс ловит и наследника")
    void hierarchyDispatch() {
        bus.addListener((BaseEvent event) -> SEEN.add("base"));
        bus.addListener((ChildEvent event) -> SEEN_CHILD.add("child"));

        bus.post(new ChildEvent());

        assertThat(SEEN).containsExactly("base");
        assertThat(SEEN_CHILD).containsExactly("child");
    }

    @Test
    @DisplayName("пост чужого события не трогает подписчиков")
    void noCrossTalk() {
        bus.addListener((BaseEvent event) -> SEEN.add("base"));

        bus.post(new OtherEvent());

        assertThat(SEEN).isEmpty();
    }

    @Test
    @DisplayName("порядок по приоритету: HIGHEST -> HIGH -> NORMAL -> LOW -> LOWEST")
    void priorityOrder() {
        // регистрируем вразнобой, ждём порядок по приоритету
        bus.addListener(EventPriority.LOWEST, (BaseEvent event) -> ORDER.add("lowest"));
        bus.addListener(EventPriority.NORMAL, (BaseEvent event) -> ORDER.add("normal"));
        bus.addListener(EventPriority.HIGHEST, (BaseEvent event) -> ORDER.add("highest"));
        bus.addListener(EventPriority.LOW, (BaseEvent event) -> ORDER.add("low"));
        bus.addListener(EventPriority.HIGH, (BaseEvent event) -> ORDER.add("high"));

        bus.post(new BaseEvent());

        assertThat(ORDER).containsExactly("highest", "high", "normal", "low", "lowest");
    }

    @Test
    @DisplayName("внутри одного приоритета — порядок регистрации")
    void samePriorityKeepsRegistrationOrder() {
        bus.addListener((BaseEvent event) -> ORDER.add("first"));
        bus.addListener((BaseEvent event) -> ORDER.add("second"));
        bus.addListener((BaseEvent event) -> ORDER.add("third"));

        bus.post(new BaseEvent());

        assertThat(ORDER).containsExactly("first", "second", "third");
    }

    @Test
    @DisplayName("подписка объекта по @SubscribeEvent, с иерархией событий")
    void registerObject() {
        var subscriber = new Subscriber();
        bus.register(subscriber);

        bus.post(new BaseEvent());
        bus.post(new ChildEvent());

        assertThat(subscriber.baseCalls).isEqualTo(2);
        assertThat(subscriber.childCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("@SubscribeEvent: приоритет из аннотации соблюдается")
    void registerObjectHonoursPriority() {
        bus.register(new PrioritizedSubscriber());

        bus.post(new BaseEvent());

        assertThat(ORDER).containsExactly("highest", "normal", "lowest");
    }

    static class Subscriber {
        int baseCalls;
        int childCalls;

        @SubscribeEvent
        void onBase(BaseEvent event) {
            baseCalls++;
        }

        @SubscribeEvent
        void onChild(ChildEvent event) {
            childCalls++;
        }
    }

    static class PrioritizedSubscriber {
        @SubscribeEvent(priority = EventPriority.LOWEST)
        void onLowest(BaseEvent event) {
            ORDER.add("lowest");
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        void onHighest(BaseEvent event) {
            ORDER.add("highest");
        }

        @SubscribeEvent
        void onNormal(BaseEvent event) {
            ORDER.add("normal");
        }
    }

    @Test
    @DisplayName("post возвращает тот же объект события")
    void postReturnsSameInstance() {
        var event = new BaseEvent();

        assertThat(bus.post(event)).isSameAs(event);
    }

    @Test
    @DisplayName("пост без слушателей — тихий no-op")
    void postWithoutListeners() {
        var event = new BaseEvent();

        assertThat(bus.post(event)).isSameAs(event);
        assertThat(event.isCanceled()).isFalse();
    }

    @Test
    @DisplayName("отмена — это состояние события, а не обрыв рассылки")
    void cancellationIsState() {
        bus.addListener((BaseEvent event) -> event.setCanceled(true));

        var event = bus.post(new BaseEvent());

        // Слушателей без receiveCanceled шим, как и NeoForge, не фильтрует:
        // решение "идти дальше или нет" — за самим слушателем.
        assertThat(event.isCanceled()).isTrue();
    }

    @Test
    @DisplayName("слушатель, добавленный во время рассылки, не получает текущее событие")
    void listenerAddedDuringDispatchIsNotCalledForSameEvent() {
        bus.addListener((BaseEvent event) -> bus.addListener((BaseEvent late) -> SEEN.add("late")));

        bus.post(new BaseEvent());
        assertThat(SEEN).isEmpty();

        bus.post(new BaseEvent());
        assertThat(SEEN).containsExactly("late");
    }

    @Test
    @DisplayName("анонимный класс (не лямбда) тоже получает события")
    void anonymousClassListenerReceivesEvents() {
        // Тип берётся не из SerializedLambda (у анонимного класса его нет), а из
        // generic-параметра EventListener. Раньше такой слушатель молча терялся.
        var calls = new int[1];
        EventListener<BaseEvent> anonymous = new EventListener<>() {
            @Override
            public void accept(BaseEvent event) {
                calls[0]++;
            }
        };
        bus.addListener(anonymous);

        bus.post(new BaseEvent());

        assertThat(calls[0]).isEqualTo(1);
    }
}
