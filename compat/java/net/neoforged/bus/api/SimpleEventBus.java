package net.neoforged.bus.api;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Минимальная, но настоящая реализация шины: слушатели группируются по классу события,
 * dispatch идёт по иерархии классов от конкретного к базовому, с учётом приоритета.
 *
 * Тип события вытаскивается из SerializedLambda слушателя. Пока это работает для лямбд
 * и ссылок на методы с одним параметром — ровно то, что использует AE2.
 *
 * Отдельно поддержаны слушатели, зарегистрированные как {@link Consumer} без класса
 * события (так делает GuideME: `bus.addListener(this::onSomething)`). Для них тип
 * неизвестен в рантайме, поэтому слушатель «привязывается» к первому событию, которое
 * он согласился принять: чужой тип лямбда отбрасывает исключением ClassCastException
 * ещё до вызова тела (javac вставляет checkcast в сгенерированный метод), поэтому
 * такая проба безопасна и делается один раз на слушателя.
 */
public final class SimpleEventBus implements IEventBus {

    private record Entry(Consumer<Event> listener, EventPriority priority, boolean receiveCanceled, int order) {
    }

    /** Слушатель без известного типа события. */
    private static final class FuzzyEntry {
        final Consumer<Event> listener;
        final EventPriority priority;
        /** Класс события, который слушатель принял. null — ещё не привязан. */
        volatile Class<?> bound;

        FuzzyEntry(Consumer<Event> listener, EventPriority priority) {
            this.listener = listener;
            this.priority = priority;
        }
    }

    private final Map<Class<?>, List<Entry>> listeners = new ConcurrentHashMap<>();
    private final List<FuzzyEntry> fuzzy = new CopyOnWriteArrayList<>();
    /** Слушатели, добавленные через {@link #register(Object)}: нужно для unregister. */
    private final Map<Object, List<Entry>> registered = new ConcurrentHashMap<>();

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
        addRaw(eventType, priority, true, asConsumer(listener));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(Consumer<T> listener) {
        fuzzy.add(new FuzzyEntry((Consumer<Event>) listener, EventPriority.NORMAL));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(EventPriority priority, Consumer<T> listener) {
        fuzzy.add(new FuzzyEntry((Consumer<Event>) listener, priority));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(Class<T> eventType, Consumer<T> listener) {
        addRaw(eventType, EventPriority.NORMAL, true, (Consumer<Event>) listener);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> listener) {
        addRaw(eventType, priority, true, (Consumer<Event>) listener);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Event> asConsumer(EventListener<?> listener) {
        return (Consumer<Event>) listener;
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
        // Как в NeoForge: повторная регистрация того же объекта/класса/метода игнорируется,
        // иначе хендлеры бы задваивались.
        if (registered.containsKey(target)) {
            return;
        }
        // Как в NeoForge: register() принимает и экземпляр, и Class, и отдельный Method.
        //  - instance: подписываются методы @SubscribeEvent этого объекта (с иерархией классов);
        //  - Class:    только static-методы этого класса (автоподписка @EventBusSubscriber,
        //              AutoEventSubscriber);
        //  - Method:   ровно один static-метод (позволяет раскладывать хендлеры одного
        //              класса по разным шинам, как это делает NeoForge).
        if (target instanceof Method method) {
            var entry = registerMethod(null, method);
            if (entry != null) {
                registered.computeIfAbsent(target, k -> new CopyOnWriteArrayList<>()).add(entry);
            }
            return;
        }
        if (target instanceof Class<?> clazz) {
            var entries = new ArrayList<Entry>();
            for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        continue;
                    }
                    var entry = registerMethod(null, m);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            if (!entries.isEmpty()) {
                registered.put(clazz, entries);
            }
            return;
        }

        // Экземпляр: в NeoForge @SubscribeEvent в родительском классе подхватывается.
        // Перекрытые методы не дублируем.
        var seen = new java.util.HashSet<String>();
        var entries = new ArrayList<Entry>();
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
                if (ann == null || m.getParameterCount() != 1) {
                    continue;
                }
                if (!seen.add(m.getName() + java.util.Arrays.toString(m.getParameterTypes()))) {
                    continue;
                }
                var entry = registerMethod(target, m);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        if (!entries.isEmpty()) {
            registered.put(target, entries);
        }
    }

    /**
     * Подписывает один метод, помеченный {@link SubscribeEvent}. {@code receiver == null} -
     * метод статический. Возвращает null, если метод не годится в слушатели (как и
     * NeoForge, такие методы просто не подписываются).
     */
    private Entry registerMethod(Object receiver, Method m) {
        SubscribeEvent ann = m.getAnnotation(SubscribeEvent.class);
        if (ann == null) {
            return null;
        }
        if (receiver == null && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
            // register(Class/Method) в NeoForge подписывает только static-хендлеры.
            System.err.println("[nfcompat] @SubscribeEvent " + m + " не static — пропускаю");
            return null;
        }
        if (m.getParameterCount() != 1) {
            return null;
        }
        Class<?> param = m.getParameterTypes()[0];
        if (!Event.class.isAssignableFrom(param)) {
            return null;
        }
        try {
            m.setAccessible(true);
        } catch (RuntimeException ignored) {
            // в модульной системе бывает нельзя; попытка вызова ниже всё скажет сама
        }
        return addRaw(param, ann.priority(), ann.receiveCanceled(), event -> {
            try {
                m.invoke(receiver, event);
            } catch (java.lang.reflect.InvocationTargetException e) {
                // не заворачиваем дважды: иначе стек-трейс указывает на шину, а не на код мода
                throw new RuntimeException("Ошибка в слушателе " + m, e.getCause() != null ? e.getCause() : e);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Ошибка вызова слушателя " + m, e);
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Entry addRaw(Class<?> eventType, EventPriority priority, boolean receiveCanceled,
            Consumer<Event> listener) {
        List<Entry> list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        var entry = new Entry(listener, priority, receiveCanceled, list.size());
        list.add(entry);
        list.sort(Comparator.comparingInt((Entry e) -> priorityRank(e.priority())).thenComparingInt(Entry::order));
        return entry;
    }

    /**
     * Отписывает всё, что было подписано через {@link #register(Object)} для этого объекта.
     * В NeoForge `unregister` снимает слушателей, привязанных к экземпляру; нашей
     * регистрации по методу этого достаточно.
     */
    @Override
    public void unregister(Object target) {
        var entries = registered.remove(target);
        if (entries == null) {
            return;
        }
        for (var list : listeners.values()) {
            list.removeAll(entries);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Event> T post(T event) {
        for (Class<?> c = event.getClass(); c != null && Event.class.isAssignableFrom(c); c = c.getSuperclass()) {
            List<Entry> list = listeners.get(c);
            if (list == null) {
                continue;
            }
            for (Entry entry : new ArrayList<>(list)) {
                // Как в NeoForge: слушатель без receiveCanceled отменённое событие не получает.
                // Слушатели, добавленные через addListener(...), сохраняют старое поведение
                // шима (получают всё): они не несут аннотации и флага.
                if (!entry.receiveCanceled() && event.isCanceled()) {
                    continue;
                }
                entry.listener().accept(event);
            }
        }

        dispatchFuzzy(event);
        return event;
    }

    /**
     * Доставка слушателям, зарегистрированным как Consumer. Первое успешное принятие
     * привязывает слушателя к классу события; дальше он вызывается только на нём и его
     * наследниках.
     */
    private void dispatchFuzzy(Event event) {
        for (var entry : fuzzy) {
            var bound = entry.bound;
            if (bound == null) {
                try {
                    entry.listener.accept(event);
                    entry.bound = event.getClass();
                } catch (ClassCastException notForThisEvent) {
                    // чужая лямбда отбросила событие до выполнения тела — идём дальше
                }
            } else if (bound.isInstance(event)) {
                entry.listener.accept(event);
            }
        }
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
