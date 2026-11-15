# -*- coding: utf-8 -*-
"""RegisterEvent: сырой каст вместо дженерик-головоломки."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\compat\java\net\neoforged\neoforge\registries\RegisterEvent.java"

OLD = """    @SuppressWarnings("unchecked")
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {
        return (Registry<T>) ((Registry<Registry<?>>) (Registry<?>) BuiltInRegistries.REGISTRY).getValue(key);
    }"""

NEW = """    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> Registry<T> getRegistry(ResourceKey<? extends Registry<T>> key) {
        // BuiltInRegistries.REGISTRY — Registry<? extends Registry<?>>, поэтому без сырого каста
        // не сходится; ключ и значение согласованы на стороне вызывающего.
        return (Registry<T>) ((Registry) BuiltInRegistries.REGISTRY).getValue((ResourceKey) key);
    }"""

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()
if OLD not in text:
    raise SystemExit("не найдено")
with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(text.replace(OLD, NEW, 1))
print("поправлен RegisterEvent")
