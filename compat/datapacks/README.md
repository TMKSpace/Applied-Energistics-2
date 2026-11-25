# Датапаки совместимости (не часть мода)

Лежат здесь как исходники: это не ресурсы мода, а патчи, которые игрок/админ кладёт
в мир сам. Внутрь `appliedenergistics2-fabric-*.jar` они не попадают.

## ae2-tr-quartz-fix — Tech Reborn + AE2 26.3

**Проблема.** TechReborn 6.2.1 включает свои AE2-рецепты по условию «мод `ae2` загружен»
и ссылается в них на теги `#c:certus_quartz` и `#c:ores/certus_quartz`. В AE2 26.3 таких
тегов нет, а руды сертус-кварца нет вообще (кварц добывают из жеод и кластеров). Из-за
этого реестр рецептов падает и **сервер не запускается**:

```
Errors in element techreborn:grinder/certus_quartz_dust
Missing tag: 'c:certus_quartz' in 'minecraft:item'
Failed to load datapacks, can't proceed with server load
```

На NeoForge с AE2 26.3 то же самое — это баг интеграции в Tech Reborn, не порта.

**Что делает патч.**
- `c:certus_quartz` → `#c:gems/certus_quartz` (кристалл): рецепты помола начинают работать.
- `c:ores/certus_quartz` → пустой тег: рецепты с «рудой» парсятся, но ни с чем не совпадают
  (руды в AE2 нет, и это честно).

**Куда класть.** В `datapacks` **внутри папки мира**, а не в корне сервера:

```
<world>/datapacks/ae2-tr-quartz-fix/   (папка целиком или zip с тем же именем)
```

Проверено на выделенном сервере: без патча — падение реестра, с патчем — старт проходит.
