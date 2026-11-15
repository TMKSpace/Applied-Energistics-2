package net.neoforged.bus.api;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Минимальная, но настоящая реализация шины: слушатели группируются по классу события,
 * dispatch идёт по иерархии классов от конкретного к базовому, с учётом приоритета.
 *
 * Тип события вытаскивается из SerializedLambda слушателя. Пока это работает для лямбд
 * и ссылок на методы с одним параметром — ровно то, что использует AE2.
 */
public final class SimpleEventBus implements IEventBus {

    private record Entry<T extends Event>(EventListener<T> listener, EventPriority priority, int order) {
    }

    private final Map<Class<?>, List<Entry<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <T extends Event> void addListener(EventListener<T> listener) {
        addListener(EventPriority.NORMAL, listener);
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, EventListener<T> listener) {
        Class<?> eventType = eventTypeOf(listener);
        if (eventType == null) {
            System.err.println("[nfcompat] не удалось определить тип события у слушателя: " + listener.getClass());
            return;
        }
        List<Entry<?>> list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(new Entry<>(listener, priority, list.size()));
        list.sort(Comparator.comparingInt((Entry<?> e) -> priorityRank(e.priority())).thenComparingInt(Entry::order));
    }

    private static int priorityRank(EventPriority p) {
        return switch (p) {
            case HIGHEST -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
            case LOWEST -> 4;
        };
    }

    @Override
    public void register(Object target) {
        // Идём по всей иерархии: в NeoForge @SubscribeEvent в родительском классе
        // подхватывается. Перекрытые методы не дублируем.
        var seen = new java.util.HashSet<String>();
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
                if (ann == null || m.getParameterCount() != 1) {
                    continue;
                }
                if (!seen.add(m.getName() + java.util.Arrays.toString(m.getParameterTypes()))) {
                    continue;
                }
                m.setAccessible(true);
                Class<?> param = m.getParameterTypes()[0];
                if (!Event.class.isAssignableFrom(param)) {
                    continue;
                }
                addRaw(param, ann.priority(), event -> {
                    try {
                        m.invoke(target, event);
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException("Ошибка вызова слушателя " + m, e);
                    }
                });
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void addRaw(Class<?> eventType, EventPriority priority, EventListener listener) {
        List<Entry<?>> list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(new Entry(listener, priority, list.size()));
        list.sort(Comparator.comparingInt((Entry<?> e) -> priorityRank(e.priority())).thenComparingInt(Entry::order));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {
        for (Class<?> c = event.getClass(); c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
            List<Entry<?>> list = listeners.get(c);
            if (list == null) {
                continue;
            }
            for (Entry<?> entry : new ArrayList<>(list)) {
                ((EventListener<Event>) entry.listener()).accept(event);
            }
        }
        return event;
    }

        /**
     * Достаёт тип события у слушателя.
     *
     * Важно: у лямбды нельзя брать impl-сигнатуру. javac дописывает захваченные
     * переменные (и {@code this}) ПЕРЕД параметром SAM-метода, поэтому первым
     * аргументом там оказывается не событие, а захваченное значение: слушатель
     * молча уезжает на чужой класс и событий не получает. Instantiated-тип всегда
     * описывает именно SAM-метод — {@code (L<event>;)V}.
     */
    private static Class<?> eventTypeOf(EventListener<?> listener) {
        Class<?> fromLambda = eventTypeFromLambda(listener);
        if (fromLambda != null) {
            return fromLambda;
        }
        return eventTypeFromListenerInterfaces(listener.getClass());
    }

    private static Class<?> eventTypeFromLambda(EventListener<?> listener) {
        try {
            Method writeReplace = listener.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replaced = writeReplace.invoke(listener);
            if (!(replaced instanceof SerializedLambda lambda)) {
                return null;
            }
            Class<?> type = firstEventParameter(lambda.getInstantiatedMethodType());
            if (type != null) {
                return type;
            }
            // запасной путь на случай нестандартного компилятора
            return firstEventParameter(lambda.getImplMethodSignature());
        } catch (Throwable t) {
            return null;
        }
    }

    /** Разбирает дескриптор метода и берёт первый параметр, если это событие. */
    private static Class<?> firstEventParameter(String signature) {
        if (signature == null) {
            return null;
        }
        int open = signature.indexOf('(');
        int close = signature.indexOf(')', open);
        if (open < 0 || close < 0) {
            return null;
        }
        String args = signature.substring(open + 1, close);
        if (!args.startsWith("L")) {
            return null;
        }
        int semi = args.indexOf(';');
        if (semi < 0) {
            return null;
        }
        try {
            Class<?> cls = Class.forName(args.substring(1, semi).replace('/', '.'));
            return Event.class.isAssignableFrom(cls) ? cls : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Запасной путь для анонимных классов: тип из generic-параметра EventListener. */
    private static Class<?> eventTypeFromListenerInterfaces(Class<?> listenerClass) {
        for (Class<?> c = listenerClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Type itf : c.getGenericInterfaces()) {
                if (!(itf instanceof java.lang.reflect.ParameterizedType parameterized)) {
                    continue;
                }
                if (parameterized.getRawType() != EventListener.class) {
                    continue;
                }
                var arg = parameterized.getActualTypeArguments()[0];
                if (arg instanceof Class<?> cls && Event.class.isAssignableFrom(cls)) {
                    return cls;
                }
            }
        }
        return null;
    }
}
