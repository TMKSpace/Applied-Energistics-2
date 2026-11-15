# -*- coding: utf-8 -*-
"""Фикс критичного бага шины: тип события брался из impl-сигнатуры лямбды."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\bus\api\SimpleEventBus.java"

NEW = '''    /**
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
'''

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()

marker = "    /**"
idx = text.rfind("    private static Class<?> eventTypeOf(")
if idx < 0:
    raise SystemExit("eventTypeOf не найден")
# отступаем до начала javadoc-комментария над методом
start = text.rfind("/**", 0, idx)
if start < 0:
    start = idx

new_text = text[:start] + NEW
with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(new_text)
print("patched:", len(text), "->", len(new_text))
