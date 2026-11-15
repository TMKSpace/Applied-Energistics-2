# План C: compile-time стабы + рантайм-миксины

Цель: убрать из `src/` маркеры того класса, где NeoForge **добавил член в ванильный
класс** (сейчас это ~180 из 216 мест). Тогда при обновлении апстрима в дереве остаются
только правки поведения (двухветочный датаген, атрибуты биома, данные рецептов), а
«недостающие методы» перестают быть нашими проблемами.

## Идея

1. **Стаб-джар (только compile).** На сборке ASM читает деобф-джарники MC и, если в
   классе нет нужного члена, добавляет его (тело — `throw new UnsupportedOperationException`).
   Результат — jar, который кладётся в `compileClasspath` **раньше** MC.
2. **Миксины (runtime).** Те же члены реализуются миксинами по-настоящему.
3. **Список членов — данные**, а не код: `stub-specs.json` (класс → члены).

## Почему это лучше для апгрейдов, а не хуже

- Стабы **генерируются из текущего MC**: если в новой версии член появился сам, ASM видит
  его и ничего не добавляет → стаб «сам снимается». Ничего не надо чинить руками.
- Если MC переименовал класс/метод — падает не сборка AE2, а наш генератор с внятной
  ошибкой («в `Slot` нет `getContainerSlot`, есть `getSlotIndex`») — это сигнал обновить
  спецификацию, а не 40 файлов маркеров.
- Маркер в `src/` при апгрейде либо конфликтует при rebase, либо тихо остаётся на строке,
  которой больше нет смысла. Стаб-спека такой проблемы не имеет.

## Главная техническая сложность: порядок classpath

javac берёт **первый** подходящий класс из classpath, поэтому стаб-джар обязан идти раньше
MC. Loom вставляет MC сам, и порядок им не гарантируется.

Решение — **отдельный сорсет с полностью своим classpath** (по образцу уже работающего
`shimTest`):

```groovy
sourceSets {
    ae2Port {                       // здесь компилируются препроцессированные сурсы AE2
        java.srcDirs = ['build/nfcompatSrc', 'compat/java']
        compileClasspath = files(stubJar) + configurations.ae2PortCompileClasspath
    }
}
```

Тогда порядок задаём мы, а `main` (или задача `assemble`) просто зависит от `ae2Port`.

## Список членов для стабов (первая партия)

| класс | член |
|---|---|
| `Slot` | `getSlotIndex()` |
| `RecipeAccess` | `recipeMap()` |
| `Level` / `BlockGetter` | `getCapability(...)`, `invalidateCapabilities(...)`, `getModelData(BlockPos)` |
| `BlockEntity` | `getModelData()`, `requestModelDataUpdate()`, `onChunkUnloaded()`, `invalidateCapabilities()` |
| `ItemStack` | `getCapability(...)`, `isComponentsPatchEmpty()` |
| `Entity` | `getData/setData(AttachmentType)` |
| `BlockBehaviour` | `getExplosionResistance(...)`, `onCaughtFire(...)` |
| `BakedQuad$MaterialInfo` | `ambientOcclusion()` |
| `BlockStateModelPart` | `ambientOcclusion()` |
| `ServerGamePacketListenerImpl` | `send(CustomPacketPayload)` |
| `KeyMapping` | конструктор с `KeyConflictContext` |
| `CreativeModeTab` | `builder()` без аргументов |
| `Registry` | `addCallback(...)` |
| `MenuProvider` | `shouldTriggerClientSideContainerClosingOnOpen()` |
| `GuiGraphicsExtractor` | `peekScissorStack()`, `submitGuiElementRenderState(...)` |

## Порядок работ

1. Стабилизировать клиент (сейчас падает на тултипе) — иначе нечем проверять.
2. Вынести компиляцию AE2 в отдельный сорсет с управляемым classpath (механика, без стабов).
3. Написать генератор стаб-джара + `stub-specs.json` для 3-4 классов, проверить, что сборка
   зелёная и маркеры для них можно удалить.
4. Расширять список, удаляя маркеры партиями, с прогоном клиента после каждой партии.
5. Миксины для рантайма — по мере надобности (многие заглушки уже есть в шиме).
