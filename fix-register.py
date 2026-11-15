# -*- coding: utf-8 -*-
"""Чиним register(): тело лямбды осталось с лишними/недостающими скобками."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\bus\api\SimpleEventBus.java"

REGISTER = '''    @Override
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
'''

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()

start = text.index("    @Override\n    public void register(Object target) {")
end = text.index("    @SuppressWarnings({ \"unchecked\", \"rawtypes\" })", start)

text = text[:start] + REGISTER + "\n" + text[end:]

with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(text)

# отчёт о балансе скобок вне строк/комментариев (грубо, достаточно для проверки)
depth = 0
for ch in text:
    if ch == '{':
        depth += 1
    elif ch == '}':
        depth -= 1
print("фигурных скобок: баланс", depth)
print("строк в файле:", text.count("\n") + 1)
