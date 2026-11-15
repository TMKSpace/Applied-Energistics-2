/*
 * Часть nfcompat. Минимальная замена JSR-305 (com.google.code.findbugs:jsr305),
 * которую NeoForge тащит на classpath, а Fabric - нет.
 * Нужна, потому что нетронутые исходники AE2 местами пишут import javax.annotation.Nullable.
 * Поведение: чистая аннотация, компилятору/рантайму ничего не делает.
 */
package javax.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE })
public @interface Nullable {
}
