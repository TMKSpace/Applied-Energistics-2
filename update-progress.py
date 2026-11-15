# -*- coding: utf-8 -*-
"""Обновляем таблицу прогресса в PROGRESS.md."""
import io

PATH = r"D:\Code\java\ae2-fabric-probe\PROGRESS.md"

OLD = """| + **препроцессор + патч-сет маркерами** (`ItemStackTemplate`, `OitPipelineSet`, `EntityType`, `BlockEntityType`, `SavedDataType`) | **378** | 0 | 144 |

**Шим собирается.** Все оставшиеся ошибки — в нетронутых исходниках AE2, кроме тех, что
закрываются помеченными правками (см. `AE2-PATCHES.md`)."""

NEW = """| + **препроцессор + патч-сет маркерами** (`ItemStackTemplate`, `OitPipelineSet`, `EntityType`, `BlockEntityType`, `SavedDataType`) | 378 | 0 | 144 |
| + датаген двумя ветками, квады, тикеты чанков, добор пакетов | **267** | **0** | **119** |

**Шим собирается и проверен тестами.** Все оставшиеся 267 ошибок — в нетронутых исходниках AE2,
кроме мест, закрытых помеченными правками (см. `AE2-PATCHES.md`).

`shimTest` — **85 тестов, 0 упало, 5 пропущено** (пропуски требуют Fabric-рантайма:
`FluidVariant` вне загрузчика не создать). Задача не зависит от `compileJava`, поэтому
гоняется, пока основная сборка красная."""

with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()

if OLD not in text:
    raise SystemExit("не найдено")
text = text.replace(OLD, NEW, 1)

BUG = """
## Найденные тестами баги шима

### `SimpleEventBus.eventTypeOf()` — исправлен

Тип события брался из `SerializedLambda.getImplMethodSignature()`. javac дописывает
захваченные переменные (и `this`) **перед** параметром SAM-метода, поэтому первым
аргументом оказывалось захваченное значение: слушатель регистрировался на
`java.util.List` или на класс владельца и **событий не получал вообще, без ошибок в логе**.
В игре это «часть AE2 молча не реагирует».

Исправление: `getInstantiatedMethodType()` (там всегда `(L<event>;)V`) + запасной путь
для нестандартных компиляторов. Заодно:

- `register(Object)` обходит всю иерархию классов (в NeoForge `@SubscribeEvent` в родителе
  подхватывается), перекрытые методы не дублируются;
- анонимные классы-слушатели больше не теряются: тип берётся из generic-параметра
  `EventListener<T>`.

### Что ещё вскрыл отдельный сорсет

`compileJava` не доходил до трёх файлов шима (падал раньше на AE2), и они не компилировались:

1. `SortedReloadListenerEvent` — литерал `\\n` из автопатча залип в комментарий, и присваивание
   `lastVanilla` оказалось **внутри** комментария;
2. `CreateFluidSourceEvent` — то же с `vanillaResult`;
3. `DeferredRegister` — поле инициализировалось от поля, которое присваивается в конструкторе.

### Известные расхождения (не исправлены, зафиксированы тестами)

- `StorageFluidHandler.isValid()` возвращает `true` для любого непустого ресурса и игнорирует
  индекс (в NeoForge спрашивает слот);
- `insert`/`extract` на отрицательное количество отдают `0`, а не бросают
  `IllegalArgumentException`; на `null`-ресурс — `NPE`;
- для не-slotted `Storage` индекс игнорируется (`view(99)` отдаёт нулевой слот);
- `ICancellableEvent` в шиме не реализует ничего, поэтому `instanceof ICancellableEvent`
  в коде AE2 всегда `false` (отмена живёт флагом в `Event`).

"""

MARK = "## Accesswidener"
if BUG.strip() not in text:
    text = text.replace(MARK, BUG.lstrip("\n") + MARK, 1)

with io.open(PATH, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(text)
print("PROGRESS.md обновлён")
