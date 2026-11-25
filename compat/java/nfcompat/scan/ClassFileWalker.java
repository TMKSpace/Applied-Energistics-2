/*
 * Часть nfcompat. Обход корней модов ради байткода.
 *
 * На NeoForge это делает сам загрузчик (scan data): он обходит все mod-файлы и
 * читает аннотации. У нас сканирования нет, поэтому его приходится изображать:
 * корень мода - либо каталог (dev-режим, распакованный мод), либо jar.
 *
 * Раньше такой обход был зашит в `nfcompat.gametest.ModAnnotationScanner` и
 * смотрел только пути с `appeng/`. Теперь их два потребителя (тест-плоты и
 * автоподписка `@EventBusSubscriber`), поэтому сам обход вынесен сюда, а фильтр
 * по имени класса задаёт вызывающий: разбирать каждый class-файл ASM'ом просто
 * так слишком дорого, сначала отсекаем по пути.
 *
 * Вложенные jar'ы (jar-in-jar) не разворачиваются: путь внутри zipfs отдаёт
 * `File` только через `toFile()`, который на zipfs падает. Такой корень просто
 * пропускается с предупреждением у вызывающего.
 */
package nfcompat.scan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ClassFileWalker {

    /** Получает поток с содержимым одного class-файла; поток закрывает обходчик. */
    @FunctionalInterface
    public interface ClassFileVisitor {
        void visit(InputStream in) throws IOException;
    }

    private ClassFileWalker() {
    }

    /**
     * Обходит каталог или jar и отдаёт содержимое записей, прошедших {@code filter}.
     * Для каталога в фильтр приходит полный путь, для jar - имя записи внутри архива.
     * Имя записи всегда с прямыми слэшами.
     */
    public static void walk(Path root, Predicate<String> filter, ClassFileVisitor visitor) throws IOException {
        if (Files.isDirectory(root)) {
            try (var stream = Files.walk(root)) {
                for (Path path : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    if (!filter.test(path.toString())) {
                        continue;
                    }
                    try (InputStream in = Files.newInputStream(path)) {
                        visitor.visit(in);
                    }
                }
            }
        } else if (root.toString().endsWith(".jar") && Files.isRegularFile(root)) {
            try (var zip = new ZipFile(root.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !filter.test(entry.getName())) {
                        continue;
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        visitor.visit(in);
                    }
                }
            }
        }
    }
}
