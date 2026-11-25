/*
 * Часть nfcompat. Автоматическая подписка на шины по аннотациям — то, что на NeoForge
 * делает `AutomaticEventSubscriber` (FancyModLoader, loader/fml/javafmlmod).
 *
 * Правило NeoForge:
 *  - класс помечен `@EventBusSubscriber`; загрузчик находит его через ASM-скан
 *    мод-файлов, грузит класс и подписывает его `static`-методы с `@SubscribeEvent`;
 *  - метод обязан быть static и принимать ровно один параметр-`Event`;
 *  - `value()` (Dist[]) ограничивает физическую сторону; по умолчанию — обе;
 *  - `modid()` уточняет мод для мульти-мод-джарников, иначе берётся `@Mod.value`
 *    того же класса или мод-владелец файла;
 *  - тип шины выбирается по событию: `IModBusEvent` -> мод-шина, остальное ->
 *    `NeoForge.EVENT_BUS`;
 *  - приоритет и receiveCanceled берутся из `@SubscribeEvent`.
 *
 * На Fabric этого нет: аддоны-порты (ae2wtlib, extendedae_plus) помечают классы
 * `@EventBusSubscriber` и ждут, что загрузчик их подпишет. Без этого они не получают
 * ни одного события, даже если мы их постим.
 *
 * Почему ASM, а не рефлексия по всему classpath: `Class.forName` для каждого класса
 * всех модов — это секунды и загрузка чужих статик-инициализаторов. Поэтому сначала
 * дешёвый проход по байткоду (заголовок + аннотации, без кода) и только для найденных
 * классов — `Class.forName`.
 *
 * Класс грузим с `initialize = false` (в отличие от NeoForge): статик-инициализатор
 * чужого мода выполнится при первом вызове его хендлера, а не в момент скана. Так
 * битый/недопорченный аддон не роняет старт игры, а просто пишет ошибку в лог.
 */
package nfcompat.event;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.IModBusEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

import nfcompat.NfCompat;
import nfcompat.scan.ClassFileWalker;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoEventSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger("nfcompat/events");

    private static final Type EVENT_BUS_SUBSCRIBER = Type.getType(EventBusSubscriber.class);
    private static final Type MOD = Type.getType(Mod.class);

    /**
     * Эти "моды" есть всегда и аннотаций NeoForge в себе не держат. Их jar'ы —
     * самые большие на classpath (ваниль), сканировать их бессмысленно.
     */
    private static final Set<String> SKIP_MODS = Set.of("minecraft", "java", "fabricloader");

    /** Больше строк в лог не пишем: иначе один сломанный аддон завалит старт. */
    private static final int MAX_LOGGED_FAILURES = 10;

    private static boolean installed;

    private AutoEventSubscriber() {
    }

    /**
     * Один раз сканирует classpath всех модов и подписывает найденные классы.
     * Идемпотентно: повторный вызов ничего не делает (иначе хендлеры задвоятся —
     * `register(Method)` каждый раз создаёт нового слушателя).
     */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        FabricLoader loader;
        try {
            loader = FabricLoader.getInstance();
            if (loader == null) {
                LOG.debug("[nfcompat] автоподписка @EventBusSubscriber: FabricLoader не инициализирован");
                return;
            }
        } catch (Throwable t) {
            // юнит-тесты шима: лоадера нет, сканировать нечего
            LOG.debug("[nfcompat] автоподписка @EventBusSubscriber: FabricLoader недоступен ({})", describe(t));
            return;
        }

        long start = System.nanoTime();
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seenClasses = new HashSet<>();
        int roots = 0;

        try {
            for (var mod : loader.getAllMods()) {
                String ownerId = mod.getMetadata().getId();
                if (SKIP_MODS.contains(ownerId)) {
                    continue;
                }
                String ownerName = mod.getMetadata().getName();
                String ownerVersion = mod.getMetadata().getVersion().getFriendlyString();

                for (Path root : mod.getRootPaths()) {
                    roots++;
                    try {
                        ClassFileWalker.walk(root, path -> path.endsWith(".class"),
                                in -> collect(in, ownerId, ownerName, ownerVersion, candidates, seenClasses));
                    } catch (IOException | RuntimeException e) {
                        // Вложенный jar (jar-in-jar) через zipfs не открывается как файл —
                        // такие корни просто пропускаем, это не повод падать.
                        LOG.debug("[nfcompat] автоподписка: корень {} мода {} не прочитан: {}",
                                root, ownerId, describe(e));
                    }
                }
            }
        } catch (Throwable t) {
            // Список модов/метаданные — чужая территория: сканирование не должно ронять старт.
            LOG.warn("[nfcompat] автоподписка: classpath не просканирован ({})", describe(t));
        }

        Dist dist = FMLEnvironment.dist;
        int subscribedClasses = 0;
        int subscribedMethods = 0;
        int otherSide = 0;
        List<String> failures = new ArrayList<>();

        for (Candidate c : candidates) {
            if (!onDist(c.dists(), dist)) {
                otherSide++;
                continue;
            }

            Class<?> clazz;
            try {
                clazz = Class.forName(c.className(), false, AutoEventSubscriber.class.getClassLoader());
            } catch (Throwable t) {
                failures.add(c.className() + " (" + describe(t) + ")");
                continue;
            }

            int perClass = 0;
            Method[] methods;
            try {
                methods = clazz.getDeclaredMethods();
            } catch (Throwable t) {
                // например, параметр метода ссылается на класс, которого нет в этой сборке
                failures.add(c.className() + " (методы не читаются: " + describe(t) + ")");
                continue;
            }
            for (Method method : methods) {
                SubscribeEvent subscribe = method.getAnnotation(SubscribeEvent.class);
                if (subscribe == null) {
                    continue;
                }

                if (!Modifier.isStatic(method.getModifiers())) {
                    LOG.warn("[nfcompat] автоподписка: {}#{} не static — пропускаю (NeoForge подписывает только static)",
                            c.className(), method.getName());
                    continue;
                }
                if (method.getParameterCount() != 1
                        || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    LOG.warn("[nfcompat] автоподписка: {}#{} — нужен ровно один параметр-Event, пропускаю",
                            c.className(), method.getName());
                    continue;
                }

                Class<? extends Event> eventType = method.getParameterTypes()[0].asSubclass(Event.class);
                try {
                    // Метод, а не класс: у одного класса хендлеры могут жить на разных
                    // шинах (IModBusEvent/GAME), как и раскладывает NeoForge.
                    busFor(c, eventType).register(method);
                    perClass++;
                } catch (Throwable t) {
                    failures.add(c.className() + "#" + method.getName() + " (" + describe(t) + ")");
                }
            }
            if (perClass > 0) {
                subscribedClasses++;
            }
            subscribedMethods += perClass;
        }

        long millis = (System.nanoTime() - start) / 1_000_000L;
        LOG.info("[nfcompat] автоподписка @EventBusSubscriber: классов {}, методов {} " +
                "(сторона {}, корней {}, кандидатов {}, не на этой стороне {}), {} мс",
                subscribedClasses, subscribedMethods, dist, roots, candidates.size(), otherSide, millis);

        if (!failures.isEmpty()) {
            LOG.warn("[nfcompat] автоподписка: не подписано {} (остальные — в debug)", failures.size());
            for (int i = 0; i < failures.size() && i < MAX_LOGGED_FAILURES; i++) {
                LOG.warn("[nfcompat]   {}", failures.get(i));
            }
            for (int i = MAX_LOGGED_FAILURES; i < failures.size(); i++) {
                LOG.debug("[nfcompat]   {}", failures.get(i));
            }
        }
    }

    /**
     * Читает заголовок class-файла и, если на классе висит `@EventBusSubscriber`,
     * запоминает его значения. Тело и фреймы не разбираются — это и есть дешёвый фильтр.
     */
    private static void collect(InputStream in, String ownerId, String ownerName, String ownerVersion,
            List<Candidate> out, Set<String> seen) {
        try {
            var info = new AnnotatedClass();
            new ClassReader(in).accept(info,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            if (!info.hasEventBusSubscriber || info.className == null || info.className.isEmpty()) {
                return;
            }
            if (!seen.add(info.className)) {
                return;
            }
            out.add(new Candidate(info.className, List.copyOf(info.dists), info.modid, info.bus, info.modValue,
                    ownerId, ownerName, ownerVersion));
        } catch (IOException | RuntimeException e) {
            // один битый class-файл не должен ломать скан всего мода
            LOG.debug("[nfcompat] автоподписка: битый class-файл мода {}: {}", ownerId, describe(e));
        }
    }

    private static boolean onDist(List<String> dists, Dist dist) {
        // Пустой value() — как у NeoForge по умолчанию: и клиент, и выделенный сервер.
        return dists.isEmpty() || dists.contains(dist.name());
    }

    private static String modIdOf(Candidate c) {
        if (!c.modid().isEmpty()) {
            return c.modid();
        }
        if (c.modValue() != null && !c.modValue().isEmpty()) {
            return c.modValue();
        }
        return c.ownerModId();
    }

    private static IEventBus busFor(Candidate c, Class<? extends Event> eventType) {
        // Значение bus=MOD есть только у нашего шима (в NeoForge 26.x такого атрибута нет):
        // если аддон собран против шима и попросил мод-шину — уважаем просьбу.
        boolean modBus = "MOD".equals(c.bus()) || IModBusEvent.class.isAssignableFrom(eventType);
        if (!modBus) {
            return NeoForge.EVENT_BUS;
        }
        return containerFor(c).getEventBus();
    }

    private static net.neoforged.fml.ModContainer containerFor(Candidate c) {
        String modId = modIdOf(c);
        if (NfCompat.MOD_ID.equals(modId)) {
            // Контейнер AE2 уже мог быть построен (и будет построен) точкой входа мода;
            // второй такой же нам не нужен.
            return NfCompat.container();
        }
        var existing = net.neoforged.fml.ModContainer.find(modId);
        // У аддонов своего контейнера нет (их конструктор на Fabric никто не зовёт), но
        // шина у всех наших контейнеров одна — NeoForge.EVENT_BUS, так что подписка
        // мод-шинных событий всё равно доедет.
        return existing.orElseGet(() -> NfCompat.container(modId, c.ownerName(), c.ownerVersion()));
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getName() + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    /** Найденный класс: имя и значения обеих аннотаций. */
    private record Candidate(String className, List<String> dists, String modid, String bus, String modValue,
            String ownerModId, String ownerName, String ownerVersion) {
    }

    /**
     * Посетитель, который вытаскивает из аннотаций ровно то, что нужно: стороны,
     * modid, bus и `@Mod.value`. Enum'ы читаются по имени (`visitEnum`), поэтому
     * сами классы аннотаций аддона грузить не нужно.
     */
    private static final class AnnotatedClass extends ClassVisitor {
        String className;
        boolean hasEventBusSubscriber;
        final Set<String> dists = new LinkedHashSet<>();
        String modid = "";
        String bus;
        String modValue;

        AnnotatedClass() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.className = name.replace('/', '.');
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (EVENT_BUS_SUBSCRIBER.getDescriptor().equals(descriptor)) {
                hasEventBusSubscriber = true;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("modid".equals(name) && value instanceof String s) {
                            modid = s;
                        }
                    }

                    @Override
                    public void visitEnum(String name, String descriptor, String value) {
                        if ("bus".equals(name)) {
                            bus = value;
                        }
                    }

                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!"value".equals(name)) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitEnum(String name, String descriptor, String value) {
                                dists.add(value);
                            }
                        };
                    }
                };
            }
            if (MOD.getDescriptor().equals(descriptor)) {
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("value".equals(name) && value instanceof String s) {
                            modValue = s;
                        }
                    }
                };
            }
            return null;
        }
    }
}
