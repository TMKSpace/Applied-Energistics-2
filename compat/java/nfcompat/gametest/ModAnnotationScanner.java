/*
 * Часть nfcompat. Сканирование аннотаций модов — то, что на NeoForge делает
 * `ModList#getAllScanData` (ASM-сканер загрузчика).
 *
 * Зачем: AE2 ищет свои тест-плоты ИМЕННО так — `TestPlots.findAllTestPlotClasses()`
 * перебирает scan data и берёт классы с `@TestPlotClass`. Шим возвращал пустой список,
 * поэтому `TestPlots.getPlots()` был пуст и в реестр `TEST_INSTANCE` не попадало ни
 * одного плота (в прогоне 2026-11-17 в реестре был только ванильный `always_pass`).
 *
 * Сканируем не весь classpath, а только пути, где вообще могут быть аннотации AE2:
 * имена классов, начинающиеся на `appeng/` (аннотации, которые кто-либо из AE2 кода
 * читает через scan data, лежат в `appeng.*`). Minecraft/JEI/Jade при этом даже не
 * читаются, так что стоит это единицы миллисекунд.
 *
 * Кэш статический: `getAllScanData()` зовётся из статической инициализации TestPlots,
 * повторный скан на каждый вызов не нужен.
 */
package nfcompat.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import net.fabricmc.loader.api.FabricLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public final class ModAnnotationScanner {

    /** Аннотации AE2, которые читаются через scan data (сейчас — только тест-плоты). */
    private static final String AE2_PACKAGE_PREFIX = "appeng/";
    private static final String AE2_CLASS_SUFFIX = ".class";

    private static List<ModFileScanData> cache;

    private ModAnnotationScanner() {
    }

    public static synchronized List<ModFileScanData> scanAll() {
        if (cache != null) {
            return cache;
        }

        List<ModFileScanData> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (var mod : FabricLoader.getInstance().getAllMods()) {
            List<ModFileScanData.AnnotationData> annotations = new ArrayList<>();
            for (Path root : mod.getRootPaths()) {
                try {
                    scanRoot(root, annotations, seen);
                } catch (IOException | RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger("nfcompat/gametest")
                            .warn("[gametest] failed to scan {} of mod {}", root, mod.getMetadata().getId(), e);
                }
            }
            result.add(new ModFileScanData(annotations));
        }

        cache = List.copyOf(result);
        return cache;
    }

    private static void scanRoot(Path root, List<ModFileScanData.AnnotationData> out, Set<String> seen)
            throws IOException {
        if (Files.isDirectory(root)) {
            try (var stream = Files.walk(root)) {
                for (Path path : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    if (!isCandidate(path.toString())) {
                        continue;
                    }
                    try (InputStream in = Files.newInputStream(path)) {
                        scanClass(in, out, seen);
                    }
                }
            }
        } else if (root.toString().endsWith(".jar") && Files.isRegularFile(root)) {
            try (var zip = new ZipFile(root.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory() || !isCandidate(entry.getName())) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        scanClass(in, out, seen);
                    }
                }
            }
        }
    }

    private static boolean isCandidate(String path) {
        String normalized = path.replace('\\', '/');
        int index = normalized.indexOf(AE2_PACKAGE_PREFIX);
        return index >= 0 && normalized.endsWith(AE2_CLASS_SUFFIX);
    }

    private static void scanClass(InputStream in, List<ModFileScanData.AnnotationData> out, Set<String> seen)
            throws IOException {
        var reader = new ClassReader(in);
        String className = reader.getClassName().replace('/', '.');
        if (!seen.add(className)) {
            return;
        }

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.startsWith("L" + AE2_PACKAGE_PREFIX)) {
                    out.add(new ModFileScanData.AnnotationData(
                            className,
                            ElementType.TYPE,
                            new ModFileScanData.Type(descriptor.substring(1, descriptor.length() - 1)
                                    .replace('/', '.'))));
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
}
