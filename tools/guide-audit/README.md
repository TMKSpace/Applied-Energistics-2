# Аудит GuideME против ванили 26.3

Инструменты для того, чтобы **не ловить дыры бинарного шима в игре**. GuideME — джар под
NeoForge, где NeoForge патчит ваниль (добавляет методы, расширяет доступ, меняет конструкторы).
Наш шим компенсирует это, но любые две вещи ломаются молча и всплывают крашем в рантайме:

1. **Отсутствующие члены** (`NoSuchMethodError` / `NoClassDefFoundError`).
2. **Недостаточный доступ** (`IllegalAccessError`: GuideME лезет в `private`/package-private
   член ванили, к которому доступ открывал NeoForge).

Оба аудита ниже работают по байткоду, то есть отвечают на вопрос до запуска игры.

## Когда запускать

- после обновления `guideme_version` в `gradle.properties`;
- после обновления MC/лоадера;
- когда в игре снова `NoSuchMethodError`/`IllegalAccessError` из `guideme.**` — чтобы
  посмотреть, не осталось ли рядом ещё десятка таких же.

## 1. Отсутствующие члены

```powershell
$env:PYTHONIOENCODING = "utf-8"
$env:GUIDEME_JAR = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.appliedenergistics\guideme" -Recurse -Filter *.jar | Sort-Object LastWriteTime | Select-Object -Last 1).FullName
$env:VANILLA_JAR = (Get-ChildItem "$env:USERPROFILE\.gradle\caches\fabric-loom\minecraftMaven" -Recurse -Filter "minecraft-merged-deobf-*.jar" | Select-Object -Last 1).FullName
$env:SHIM_DIR = "D:\Code\java\ae2-fabric-probe\build\classes\java\main"

python tools\guide-audit\scan-missing-members.py
```

Печатает, чего GuideME ждёт, а в ванили/шимe нет. `SHIM_DIR` нужен, чтобы ссылки на
`net.neoforged.*` проверялись по **нашему** шиму, а не по отсутствующим классам.

## 2. Аудит доступов (`IllegalAccessError`)

`AwAudit.java` — ASM-сканер: резолвит `Fieldref`/`Methodref` GuideME в ванили с учётом
иерархии и **класса-источника**, и печатает обращения к членам, до которых у нас нет доступа.

Важная деталь, на которой мы уже обжигались: у ссылок на унаследованный член owner в
констант-пуле — это подкласс GuideME, а не ваниль (`getfield textureView` без указания
класса). Наивный фильтр «owner ∈ `net/minecraft`» такие случаи пропускает — именно так
проскочил `PictureInPictureRenderer.textureView`.

```powershell
# 1) скачать ASM (любая свежая 9.x) в отдельный каталог
# 2) скомпилировать и запустить: последние два аргумента — джар GuideME и файл отчёта
javac -cp asm-9.7.1.jar;asm-tree-9.7.1.jar -d out tools\guide-audit\AwAudit.java
java -cp "out;asm-9.7.1.jar;asm-tree-9.7.1.jar;<ваниль>.jar;<gson>;<joml>;<dfu>;<brigadier>" `
     AwAudit <пути к джарникам зависимостей> <GuideME.jar> refs.tsv
```

В отчёте интересны строки `ILLEGAL-private`, `ILLEGAL-package`, `ILLEGAL-protected` и
`CHECK-protected-other` (последние обычно ложные: `super(...)` из наследника).
Каждая строка `ILLEGAL-*` — запись для `ae2.accesswidener`:

```accesswidener
accessible field <класс> <имя> <дескриптор>
accessible method <класс> <имя> <дескриптор>
mutable field ...            # если GuideME ещё и пишет в поле
```

`Overrides.java` — проверка «мёртвых override»: методов GuideME, которые якобы переопределяют
ванильные, но в ванили такого члена нет (значит в NeoForge он был, а у нас метод просто мёртв).

## Что делать с результатом

1. `NoSuchMethodError`/`NoClassDefFoundError` → миксин или стаб в `compat/java`; для статических
   методов чужого класса миксин не годится (Mixin не добавляет non-private static) — тогда
   `@Redirect` в классе GuideME.
2. `IllegalAccessError` → строка в `ae2.accesswidener` (дешевле миксина).
3. После правок — `build.ps1 -Task processResources` (AW едет в `build/resources/main`), затем
   сборка и прогон в игре.

История: этим аудитом закрыты 44 отсутствующих члена (P0) и 27+5+2 нарушений доступа.
