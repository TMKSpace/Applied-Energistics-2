/*
 * Часть nfcompat. Структурная проверка сигнатур `@WrapOperation`.
 *
 * Зачем: MixinExtras требует, чтобы `Operation` был ПОСЛЕДНИМ параметром обработчика. Если
 * поставить его первым (что естественно для `@Redirect`), ошибка вылезает только в рантайме
 * `InvalidInjectionException` - причём на клиенте, которого наши геймтесты не касаются.
 * Дважды на этом подорвались (визер и MouseHandler), поэтому проверяем на сборке.
 *
 * Тест читает уже скомпилированные классы миксинов (`build/classes/java/main/nfcompat/mixin/**`)
 * через ASM и падает, если у метода с `@WrapOperation` последний параметр - не `Operation`.
 */
package nfcompat.checks;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class MixinSignatureChecks {

    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;";
    private static final Path MIXIN_CLASSES = Path.of("build", "classes", "java", "main", "nfcompat", "mixin");

    @Test
    @DisplayName("Operation в @WrapOperation — последним параметром")
    void operationIsLastParameter() throws Exception {
        assertTrue(Files.isDirectory(MIXIN_CLASSES),
                "Нет каталога скомпилированных миксинов: " + MIXIN_CLASSES.toAbsolutePath());

        List<String> problems = new ArrayList<>();

        try (var stream = Files.walk(MIXIN_CLASSES)) {
            for (Path classFile : (Iterable<Path>) stream.filter(p -> p.toString().endsWith(".class"))::iterator) {
                byte[] bytes = Files.readAllBytes(classFile);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            private boolean wrapOperation;

                            @Override
                            public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc,
                                    boolean visible) {
                                if (WRAP_OPERATION.equals(desc)) {
                                    wrapOperation = true;
                                }
                                return null;
                            }

                            @Override
                            public void visitEnd() {
                                if (!wrapOperation) {
                                    return;
                                }
                                var args = Type.getArgumentTypes(descriptor);
                                var last = args.length == 0 ? null : args[args.length - 1];
                                if (last == null || !OPERATION.equals(last.getDescriptor())) {
                                    problems.add(classFile.getFileName() + "#" + name + " -> "
                                            + (last == null ? "нет параметров" : last.getClassName()));
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }

        assertTrue(problems.isEmpty(),
                "У @WrapOperation `Operation` обязан быть ПОСЛЕДНИМ параметром "
                        + "(иначе InvalidInjectionException в рантайме): " + problems);
    }
}

