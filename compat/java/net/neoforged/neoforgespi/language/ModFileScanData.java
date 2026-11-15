/*
 * Часть nfcompat. Результат сканирования мода.
 *
 * NeoForge при загрузке сканирует байткод модов и собирает найденные аннотации;
 * AE2 этим ищет классы, помеченные её аннотациями (тест-плоты). Сканирования у нас нет,
 * поэтому список аннотаций пуст — типы нужны, чтобы код AE2 компилировался и корректно
 * обрабатывал пустой результат.
 */
package net.neoforged.neoforgespi.language;

import java.util.Collection;
import java.util.List;

public final class ModFileScanData {
    private final List<AnnotationData> annotations = List.of();

    public Collection<AnnotationData> getAnnotations() {
        return annotations;
    }

    /** Одна найденная аннотация. */
    public static final class AnnotationData {
        private final String memberName;
        private final java.lang.annotation.ElementType targetType;
        private final Type annotationType;

        public AnnotationData(String memberName, java.lang.annotation.ElementType targetType, Type annotationType) {
            this.memberName = memberName;
            this.targetType = targetType;
            this.annotationType = annotationType;
        }

        public String memberName() {
            return memberName;
        }

        public java.lang.annotation.ElementType targetType() {
            return targetType;
        }

        public Type annotationType() {
            return annotationType;
        }
    }

    /** Тип аннотации в том виде, как его видит сканер. */
    public record Type(String getClassName) {
    }
}
