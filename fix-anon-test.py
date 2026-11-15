# -*- coding: utf-8 -*-
"""Тест анонимного слушателя: поведение изменилось после фикса шины."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\src\shimTest\java\nfcompat\event\SimpleEventBusTest.java"

NEW = '''    @DisplayName("анонимный класс (не лямбда) тоже получает события")
    void anonymousClassListenerReceivesEvents() {
        // Тип берётся не из SerializedLambda (у анонимного класса его нет), а из
        // generic-параметра EventListener. Раньше такой слушатель молча терялся.
        var calls = new int[1];
        EventListener<BaseEvent> anonymous = new EventListener<>() {
            @Override
            public void accept(BaseEvent event) {
                calls[0]++;
            }
        };
        bus.addListener(anonymous);

        bus.post(new BaseEvent());

        assertThat(calls[0]).isEqualTo(1);
    }
}
'''

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()

start = text.index('    @DisplayName("не-лямбда')
new_text = text[:start] + NEW

with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(new_text)
print("patched, длина:", len(new_text))
